package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkoutHomeScreen(viewModel: WorkoutHomeViewModel) {
    val state = viewModel.state
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("HevyGroovy", style = MaterialTheme.typography.headlineLarge)
                Text("Your workouts", style = MaterialTheme.typography.titleMedium)
            }
            if (state.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            state.error?.let { message ->
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Retry") }
                        }
                    }
                }
            }
            if (state.loaded) {
                val active = state.activeWorkout
                item {
                    if (active == null) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Ready for your next session?", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = viewModel::startWorkout, enabled = !state.loading && state.error == null) {
                                    Text("Start empty workout")
                                }
                            }
                        }
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Workout in progress", style = MaterialTheme.typography.labelLarge)
                                Text(active.nameSnapshot ?: "Workout", style = MaterialTheme.typography.titleLarge)
                                Text("Started ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(active.startedAtEpochMillis))}")
                                active.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                if (active.exercises.isEmpty()) Text("No exercises added yet.")
                                active.exercises.forEach { exercise ->
                                    Text(exercise.exerciseNameSnapshot ?: "Exercise")
                                    Text("${exercise.sets.count { it.isCompleted }} of ${exercise.sets.size} sets completed")
                                }
                                TextButton(onClick = { confirmDiscard = true }, enabled = !state.loading) {
                                    Text("Discard workout", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recent workouts", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh") }
                    }
                }
                if (state.history.isEmpty()) {
                    item { Text("Your finished workouts will appear here.") }
                }
                items(state.history, key = { it.workoutId }) { workout ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(workout.nameSnapshot ?: "Workout", style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateInstance().format(Date(workout.startedAtEpochMillis)))
                            Text("${workout.totalExercises} exercises · ${workout.totalSets} completed sets")
                        }
                    }
                }
            }
        }
    }
    if (confirmDiscard && state.activeWorkout != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("This removes this workout and all its sets. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discardWorkout() }, enabled = !state.loading) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep workout") } },
        )
    }
}
