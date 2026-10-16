package com.example.hevygroovy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DashboardScreen(model: WorkoutHomeViewModel, onWorkout: () -> Unit,
    onResume: () -> Unit, onHistory: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val state = model.state
    LazyColumn(Modifier.fillMaxSize().testTag(if (state.loaded && !state.busy) "workouts-ready" else "home-loading"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HEVY GROOVY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("Your training", style = MaterialTheme.typography.headlineLarge)
                }
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center) { NavigationGlyph(NavigationIcon.Workout) }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(message)
                    TextButton(onClick = model::refresh, enabled = !state.loading) { Text("Retry") }
                }
            }
        } }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (state.activeWorkout == null) "A little stronger,\nevery session." else "Your session is waiting.",
                        style = MaterialTheme.typography.headlineMedium)
                    Text(if (state.activeWorkout == null) "Pick a routine or start fresh. Your next rep starts here."
                        else state.activeWorkout.nameSnapshot ?: "Workout in progress",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = if (state.activeWorkout == null) onWorkout else onResume,
                        enabled = state.loaded && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (state.activeWorkout == null) "Start a workout" else "Resume workout")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onHistory) { Text("View all") }
            }
        }
        if (state.loaded && state.history.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your story starts with one workout.", style = MaterialTheme.typography.titleMedium)
                    Text("Finish your first session and your training log will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.history.take(3), key = { it.workoutId }) { workout ->
            Card(onClick = { onOpenWorkout(workout.workoutId) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(workout.startedAtEpochMillis)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(workout.nameSnapshot ?: "Workout", style = MaterialTheme.typography.titleLarge)
                    Text("${workout.totalExercises} exercises  ·  ${workout.totalSets} completed sets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Built for your next rep.", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
internal fun WorkoutStartPanel(model: WorkoutHomeViewModel, onStart: () -> Unit, onResume: () -> Unit) {
    val state = model.state
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (state.activeWorkout == null) "Quick start" else "Workout in progress", style = MaterialTheme.typography.titleLarge)
            Text(if (state.activeWorkout == null) "Build your session as you go." else state.activeWorkout.nameSnapshot ?: "Your session",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = if (state.activeWorkout == null) onStart else onResume,
                enabled = state.loaded && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text(if (state.activeWorkout == null) "Start empty workout" else "Resume workout")
            }
        }
    }
}
