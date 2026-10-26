package com.example.hevygroovy.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.hevygroovy.resttimer.RestTimerController
import com.example.hevygroovy.resttimer.RestTimerState
import java.util.Locale

@Composable
internal fun RestTimerControls(controller: RestTimerController, modifier: Modifier = Modifier) {
    val timer by controller.state.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember(context) { context.getSharedPreferences("rest-alert-permissions", Context.MODE_PRIVATE) }
    val alarms = remember(context) { context.getSystemService(AlarmManager::class.java) }
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var exactAlarmsAllowed by remember { mutableStateOf(alarms.canScheduleExactAlarms()) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactAlarmsAllowed = alarms.canScheduleExactAlarms()
                controller.rescheduleAlarm()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(timer?.token, timer?.isComplete) {
        if (timer?.isComplete == false && !preferences.getBoolean("notifications-requested", false)) {
            preferences.edit().putBoolean("notifications-requested", true).apply()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val current = timer ?: return
    RestTimerBar(current, onAdjust = controller::adjustSeconds, onSkip = controller::skip,
        modifier = modifier,
        permissionAction = when {
            !notificationsEnabled -> ({
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
            !exactAlarmsAllowed -> ({
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            })
            else -> null
        },
        permissionLabel = if (!notificationsEnabled) "Enable rest notifications" else "Allow timer alerts with screen off")
}

@Composable
internal fun RestTimerBar(timer: RestTimerState, onAdjust: (Int) -> Unit, onSkip: () -> Unit,
    modifier: Modifier = Modifier, permissionAction: (() -> Unit)? = null, permissionLabel: String = "") {
    Surface(modifier.fillMaxWidth().testTag("rest-timer"),
        color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = GroovyLayout.ContentMaxWidth).fillMaxWidth()
                .padding(horizontal = GroovyLayout.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Rest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(timer.exerciseName, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (timer.isComplete) {
                        Text("Get It!", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("rest-timer-complete"))
                    } else {
                        val seconds = timer.remainingSeconds.coerceAtLeast(0)
                        Text(String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60),
                            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("rest-timer-remaining"))
                    }
                }
                if (!timer.isComplete) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAdjust(-15) },
                        modifier = Modifier.weight(1f).heightIn(min = GroovyLayout.ButtonHeight).testTag("rest-minus-15")) { Text("−15s") }
                    OutlinedButton(onClick = { onAdjust(15) },
                        modifier = Modifier.weight(1f).heightIn(min = GroovyLayout.ButtonHeight).testTag("rest-plus-15")) { Text("+15s") }
                    TextButton(onClick = onSkip,
                        modifier = Modifier.weight(1f).heightIn(min = GroovyLayout.ButtonHeight).testTag("rest-skip")) { Text("Skip") }
                }
                if (!timer.isComplete && permissionAction != null) TextButton(onClick = permissionAction,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight).testTag("rest-alert-permission")) {
                    Text(permissionLabel, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
