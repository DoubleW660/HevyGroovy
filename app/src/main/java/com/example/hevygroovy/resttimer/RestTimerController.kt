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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.hevygroovy.MainActivity
import com.example.hevygroovy.R
import com.example.hevygroovy.app.WorkoutRestTimer
import kotlinx.coroutines.CoroutineScope
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

/** One application-owned timer. It is created explicitly by the activity or its alarm receiver. */
class RestTimerController private constructor(context: Context) : WorkoutRestTimer {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = mutableState.asStateFlow()
    private var ticker: Job? = null
    private var completionDismissal: Job? = null

    init {
        createChannel()
        restore()
    }

    @Synchronized
    override fun start(workoutId: Long, exerciseRowId: Long, setId: Long, exerciseName: String,
        seconds: Int) {
        cancelCurrent()
        notifications.cancel(NOTIFICATION_ID)
        if (seconds <= 0) return
        val now = SystemClock.elapsedRealtime()
        val timer = RestTimerState(UUID.randomUUID().toString(), workoutId, exerciseRowId,
            setId, exerciseName, now + seconds.toLong() * 1_000L, seconds)
        mutableState.value = timer
        persist(timer)
        schedule(timer)
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
        persist(updated)
        if (deadline <= now) expire(updated.token) else schedule(updated)
    }

    @Synchronized
    fun skip() {
        cancelCurrent()
        notifications.cancel(NOTIFICATION_ID)
    }

    @Synchronized
    override fun cancelForWorkout(workoutId: Long) {
        if (mutableState.value?.workoutId == workoutId) skip()
        else if (notificationWorkoutId() == workoutId) notifications.cancel(NOTIFICATION_ID)
    }

    @Synchronized
    override fun reconcileWorkout(activeWorkoutId: Long?) {
        if (mutableState.value?.workoutId?.let { it != activeWorkoutId } == true) skip()
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
        else schedule(current)
    }

    @Synchronized
    internal fun onAlarm(token: String) {
        val current = mutableState.value ?: return
        if (current.token != token || current.isComplete) return
        // An adjustment can race with an already queued alarm. A callback may never finish a
        // replacement timer or one whose deadline was extended after the alarm was dispatched.
        if (current.deadlineElapsedMillis > SystemClock.elapsedRealtime()) schedule(current)
        else expire(token)
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
        // Claim completion durably before notifying. The UI ticker and OS receiver share this
        // operation, so only one can alert, including when the process is restored by the alarm.
        cancelCurrent()
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
        alert(current)
    }

    private fun cancelCurrent() {
        ticker?.cancel()
        ticker = null
        completionDismissal?.cancel()
        completionDismissal = null
        mutableState.value?.let { cancelAlarm(it.token) }
        mutableState.value = null
        preferences.edit().clear().commit()
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

    private fun persist(timer: RestTimerState) {
        val elapsed = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        preferences.edit().putString("token", timer.token)
            .putLong("workout", timer.workoutId).putLong("exercise", timer.exerciseRowId)
            .putLong("set", timer.setId).putString("name", timer.exerciseName)
            .putLong("deadline_elapsed", timer.deadlineElapsedMillis)
            .putLong("deadline_wall", wall + timer.deadlineElapsedMillis - elapsed)
            .putLong("boot_epoch", wall - elapsed).putInt("boot_count", bootCount()).commit()
    }

    private fun restore() {
        val token = preferences.getString("token", null) ?: return
        val now = SystemClock.elapsedRealtime()
        val deadline = RestTimerTiming.restoredDeadline(
            preferences.getLong("deadline_elapsed", -1L),
            preferences.getLong("deadline_wall", -1L), preferences.getInt("boot_count", -1),
            preferences.getLong("boot_epoch", Long.MIN_VALUE), bootCount(), now,
            System.currentTimeMillis())
        // An old force-stopped rest should not chime when the user opens the app much later.
        if (deadline == null || now - deadline > MAX_ALERT_LATENESS_MILLIS) {
            cancelAlarm(token)
            preferences.edit().clear().commit()
            return
        }
        val restored = RestTimerState(token, preferences.getLong("workout", 0L),
            preferences.getLong("exercise", 0L), preferences.getLong("set", 0L),
            preferences.getString("name", "Exercise").orEmpty(), deadline,
            RestTimerTiming.remainingSeconds(deadline, now))
        mutableState.value = restored
        if (deadline > now) schedule(restored)
        startTicker()
    }

    private fun bootCount(): Int = try {
        Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: SecurityException) { -1 }

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
        private const val MAX_ALERT_LATENESS_MILLIS = 10 * 60 * 1_000L
        @Volatile private var instance: RestTimerController? = null

        @JvmStatic
        fun get(context: Context): RestTimerController = instance ?: synchronized(this) {
            instance ?: RestTimerController(context).also { instance = it }
        }
    }
}
