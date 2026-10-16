package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title") {
                Text("HevyGroovy", style = MaterialTheme.typography.headlineLarge)
                Text("Your workouts", style = MaterialTheme.typography.titleMedium)
            }
            if (state.loading) {
                item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            state.error?.let { message ->
                item(key = "error") {
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
                item(key = "active-workout") {
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
                        ActiveWorkoutHeader(viewModel, active, onDiscard = { confirmDiscard = true })
                    }
                }
                if (active != null) {
                    itemsIndexed(active.exercises, key = { _, exercise -> "exercise-${exercise.workoutExerciseId}" }) { index, exercise ->
                        WorkoutExerciseCard(viewModel, exercise, index, active.exercises.size)
                    }
                }
                item(key = "recent-heading") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recent workouts", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh") }
                    }
                }
                if (state.history.isEmpty()) {
                    item(key = "empty-history") { Text("Your finished workouts will appear here.") }
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
