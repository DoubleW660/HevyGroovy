package com.example.hevygroovy.resttimer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.hevygroovy.MainActivity
import com.example.hevygroovy.R
import com.example.hevygroovy.app.WorkoutRestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** One application-owned timer. It is created explicitly by the activity or its alarm receiver. */
class RestTimerController internal constructor(context: Context,
    private val storage: RestTimerStorage = PreferencesRestTimerStorage(context),
    private val storageWorker: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "rest-timer-storage").apply { isDaemon = true }
    },
    private val completionAlert: ((RestTimerState) -> Unit)? = null,
) : WorkoutRestTimer {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = mutableState.asStateFlow()
    private var ticker: Job? = null
    private var completionDismissal: Job? = null
    // A durable clear can outlive the brief overlay. Workout changes must still cancel its alert.
    private var pendingCompletion: RestTimerState? = null
    private val restored = CompletableDeferred<Unit>()
    private var revision = 0L
    private var reconcileBeforeRestore = false
    private var activeWorkoutBeforeRestore: Long? = null
    private val cancelledBeforeRestore = mutableSetOf<Long>()
    // Accessed only by storageWorker. Alarm changes follow the same order as durable saves.
    private var persistedAlarmToken: String? = null

    init {
        createChannel()
        restore()
    }

    @Synchronized
    override fun start(workoutId: Long, exerciseRowId: Long, setId: Long, exerciseName: String,
        seconds: Int) {
        stopCurrent()
        notifications.cancel(NOTIFICATION_ID)
        revision++
        if (seconds <= 0) {
            enqueueSave(null)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val timer = RestTimerState(UUID.randomUUID().toString(), workoutId, exerciseRowId,
            setId, exerciseName, now + seconds.toLong() * 1_000L, seconds)
        mutableState.value = timer
        enqueueSave(timer)
        startTicker()
    }

    @Synchronized
    fun adjustSeconds(delta: Int) {
        val current = mutableState.value ?: return
        if (current.isComplete) return
        val now = SystemClock.elapsedRealtime()
        val deadline = RestTimerTiming.adjustedDeadline(current.deadlineElapsedMillis, now, delta)
        val updated = current.copy(deadlineElapsedMillis = deadline,
            remainingSeconds = RestTimerTiming.remainingSeconds(deadline, now))
        mutableState.value = updated
        if (deadline <= now) expire(updated.token)
        else {
            revision++
            enqueueSave(updated)
        }
    }

    @Synchronized
    fun skip() {
        stopCurrent()
        revision++
        enqueueSave(null)
        notifications.cancel(NOTIFICATION_ID)
    }

    @Synchronized
    override fun cancelForWorkout(workoutId: Long) {
        if (!restored.isCompleted) cancelledBeforeRestore += workoutId
        if (mutableState.value?.workoutId == workoutId ||
            pendingCompletion?.workoutId == workoutId) skip()
        else if (notificationWorkoutId() == workoutId) notifications.cancel(NOTIFICATION_ID)
    }

    @Synchronized
    override fun reconcileWorkout(activeWorkoutId: Long?) {
        if (!restored.isCompleted) {
            reconcileBeforeRestore = true
            activeWorkoutBeforeRestore = activeWorkoutId
        }
        if (mutableState.value?.workoutId?.let { it != activeWorkoutId } == true ||
            pendingCompletion?.workoutId?.let { it != activeWorkoutId } == true) skip()
        if (notificationWorkoutId()?.let { it != activeWorkoutId } == true) {
            notifications.cancel(NOTIFICATION_ID)
        }
    }

    /** Recheck special alarm access after the activity returns from system settings. */
    @Synchronized
    fun rescheduleAlarm() {
        val current = mutableState.value ?: return
        if (current.isComplete) return
        if (current.deadlineElapsedMillis <= SystemClock.elapsedRealtime()) expire(current.token)
        else storageWorker.execute { schedule(current) }
    }

    internal fun onAlarm(token: String, onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                // The receiver can create this controller in a fresh process. Wait for its disk
                // read, and retain goAsync until a durable completion is finished.
                restored.await()
                synchronized(this@RestTimerController) {
                    val current = mutableState.value
                    if (current?.token == token && !current.isComplete) {
                        // A dispatched alarm cannot finish a replacement or extended rest.
                        if (current.deadlineElapsedMillis > SystemClock.elapsedRealtime()) {
                            storageWorker.execute { schedule(current) }
                        } else expire(token)
                    }
                }
            } finally {
                afterPendingWork(onFinished)
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                synchronized(this@RestTimerController) {
                    val current = mutableState.value ?: return@launch
                    if (current.isComplete) return@launch
                    val now = SystemClock.elapsedRealtime()
                    if (current.deadlineElapsedMillis <= now) {
                        expire(current.token)
                        return@launch
                    }
                    mutableState.value = current.copy(remainingSeconds =
                        RestTimerTiming.remainingSeconds(current.deadlineElapsedMillis, now))
                }
                delay(200L)
            }
        }
    }

    private fun expire(token: String) {
        val current = mutableState.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (current.token != token || current.isComplete || current.deadlineElapsedMillis > now) return
        stopCurrent()
        val completionRevision = ++revision
        pendingCompletion = current
        val displayMillis = RestTimerTiming.completionDisplayMillis(current.deadlineElapsedMillis, now)
        if (displayMillis > 0L) {
            mutableState.value = current.copy(remainingSeconds = 0, isComplete = true)
            completionDismissal = scope.launch {
                delay(displayMillis)
                synchronized(this@RestTimerController) {
                    // A completed set can start another timer during the brief message.
                    if (mutableState.value?.let { it.token == token && it.isComplete } == true) {
                        mutableState.value = null
                        completionDismissal = null
                    }
                }
            }
        }
        // The ticker and alarm share one claim. Do not alert until the clear reaches disk, and
        // suppress a queued alert if the user has since skipped or started another rest.
        enqueueSave(null) { committed ->
            synchronized(this@RestTimerController) {
                if (committed && revision == completionRevision) {
                    if (completionAlert == null) alert(current) else completionAlert.invoke(current)
                }
                if (pendingCompletion?.token == current.token) pendingCompletion = null
            }
        }
    }

    private fun stopCurrent() {
        ticker?.cancel()
        ticker = null
        completionDismissal?.cancel()
        completionDismissal = null
        mutableState.value = null
        pendingCompletion = null
    }

    private fun enqueueSave(timer: RestTimerState?, afterWrite: ((Boolean) -> Unit)? = null) {
        storageWorker.execute {
            val previousToken = persistedAlarmToken
            val committed = try {
                (if (timer == null) storage.clear() else storage.save(timer)).also {
                    if (!it) Log.e("RestTimer", "Could not persist the rest timer")
                }
            } catch (failure: RuntimeException) {
                Log.e("RestTimer", "Could not persist the rest timer", failure)
                false
            }
            if (committed) {
                if (previousToken != null && previousToken != timer?.token) cancelAlarm(previousToken)
                persistedAlarmToken = timer?.token
                if (timer != null) schedule(timer)
            }
            if (afterWrite != null) scope.launch { afterWrite(committed) }
        }
    }

    /** A receiver remains alive until all commands already submitted have reached disk. */
    internal fun afterPendingWork(onFinished: () -> Unit) {
        storageWorker.execute { scope.launch { onFinished() } }
    }

    private fun schedule(timer: RestTimerState) {
        val intent = alarmIntent(timer.token)
        alarms.cancel(intent)
        // Exact access is optional. The active app still expires at its monotonic deadline;
        // Android may defer the backup alarm without access or while the device is in Doze.
        try {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    timer.deadlineElapsedMillis, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    timer.deadlineElapsedMillis, intent)
            }
        } catch (_: SecurityException) {
            // Access can change between checking and scheduling.
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                timer.deadlineElapsedMillis, intent)
        }
    }

    private fun alarmIntent(token: String): PendingIntent = PendingIntent.getBroadcast(app,
        ALARM_REQUEST_CODE, Intent(app, RestTimerReceiver::class.java).apply {
            action = ACTION_EXPIRED
            data = Uri.parse("hevygroovy://rest-timer/$token")
            putExtra(EXTRA_TOKEN, token)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelAlarm(token: String) {
        val intent = alarmIntent(token)
        alarms.cancel(intent)
        intent.cancel()
    }

    private fun restore() {
        storageWorker.execute {
            val saved = try {
                storage.read()
            } catch (failure: RuntimeException) {
                Log.e("RestTimer", "Could not restore the rest timer", failure)
                StoredRestTimer(null, null)
            }
            persistedAlarmToken = saved.token
            scope.launch {
                synchronized(this@RestTimerController) {
                    try {
                        // A user action during the disk read takes precedence over the saved timer.
                        if (revision == 0L) {
                            val timer = saved.timer?.takeUnless {
                                it.workoutId in cancelledBeforeRestore ||
                                    (reconcileBeforeRestore &&
                                        it.workoutId != activeWorkoutBeforeRestore)
                            }
                            if (timer == null) {
                                if (saved.token != null) enqueueSave(null)
                            } else {
                                mutableState.value = timer
                                if (timer.deadlineElapsedMillis > SystemClock.elapsedRealtime()) {
                                    storageWorker.execute { schedule(timer) }
                                }
                                startTicker()
                            }
                        }
                    } finally {
                        cancelledBeforeRestore.clear()
                        restored.complete(Unit)
                    }
                }
            }
        }
    }

    private fun createChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Rest complete",
            NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when your exercise rest timer finishes"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            })
    }

    private fun alert(timer: RestTimerState) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            playBriefTone()
            return
        }
        val launch = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORKOUT_ID, timer.workoutId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rest_timer)
            .setContentTitle("Rest complete")
            .setContentText("${timer.exerciseName} · time for your next set")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addExtras(Bundle().apply { putLong(EXTRA_WORKOUT_ID, timer.workoutId) })
            .setContentIntent(launch).setAutoCancel(true).build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            playBriefTone()
        }
    }

    private fun notificationWorkoutId(): Long? = notifications.activeNotifications
        .firstOrNull { it.id == NOTIFICATION_ID }?.notification?.extras
        ?.takeIf { it.containsKey(EXTRA_WORKOUT_ID) }?.getLong(EXTRA_WORKOUT_ID)

    private fun playBriefTone() {
        val audio = app.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0 ||
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500L)
        } catch (_: RuntimeException) {
            // Audio hardware can be unavailable; timer completion remains recorded.
        }
    }

    companion object {
        const val EXTRA_WORKOUT_ID = "rest_timer_workout_id"
        internal const val ACTION_EXPIRED = "com.example.hevygroovy.REST_TIMER_EXPIRED"
        internal const val EXTRA_TOKEN = "rest_timer_token"
        private const val CHANNEL_ID = "rest_complete"
        private const val NOTIFICATION_ID = 4_015
        private const val ALARM_REQUEST_CODE = 15
        @Volatile private var instance: RestTimerController? = null

        @JvmStatic
        fun get(context: Context): RestTimerController = instance ?: synchronized(this) {
            instance ?: RestTimerController(context).also { instance = it }
        }
    }
}
