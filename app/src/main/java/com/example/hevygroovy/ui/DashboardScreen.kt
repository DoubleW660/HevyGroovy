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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DashboardScreen(model: WorkoutHomeViewModel, onWorkout: () -> Unit,
    onResume: () -> Unit, onHistory: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val state = model.state
    LazyColumn(Modifier.fillMaxSize().testTag(if (state.loaded && !state.busy) "workouts-ready" else "home-loading"),
        contentPadding = PaddingValues(GroovyLayout.ScreenPadding), verticalArrangement = Arrangement.spacedBy(GroovyLayout.SectionGap)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HEVY GROOVY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("Your training", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                }
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center) { NavigationGlyph(NavigationIcon.Workout) }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item {
            ErrorCard(message, onRetry = model::refresh, retryEnabled = !state.loading)
        } }
        item {
            PrimaryActionCard(
                title = if (state.activeWorkout == null) "A little stronger, every session." else "Your session is waiting.",
                description = if (state.activeWorkout == null) "Pick a routine or start fresh. Your next rep starts here."
                    else state.activeWorkout.nameSnapshot ?: "Workout in progress",
                actionLabel = if (state.activeWorkout == null) "Start a workout" else "Resume workout",
                onClick = if (state.activeWorkout == null) onWorkout else onResume,
                enabled = state.loaded && !state.busy)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight)) { Text("View all") }
            }
        }
        if (state.loaded && state.history.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your story starts with one workout.", style = MaterialTheme.typography.titleMedium)
                    Text("Finish your first session and your training log will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.history.take(3), key = { it.workoutId }) { workout ->
            Card(onClick = { onOpenWorkout(workout.workoutId) }, modifier = Modifier.fillMaxWidth(),
                colors = groovyCardColors()) {
                Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(workout.startedAtEpochMillis)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(workout.nameSnapshot ?: "Workout", style = MaterialTheme.typography.titleLarge)
                    Text("${workout.totalExercises} exercises  ·  ${workout.totalSets} completed sets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun WorkoutStartPanel(model: WorkoutHomeViewModel, onStart: () -> Unit, onResume: () -> Unit) {
    val state = model.state
    PrimaryActionCard(
        title = if (state.activeWorkout == null) "Quick start" else "Workout in progress",
        description = if (state.activeWorkout == null) "Build your session as you go." else state.activeWorkout.nameSnapshot ?: "Your session",
        actionLabel = if (state.activeWorkout == null) "Start empty workout" else "Resume workout",
        onClick = if (state.activeWorkout == null) onStart else onResume,
        enabled = state.loaded && !state.busy, highlighted = true)
}
