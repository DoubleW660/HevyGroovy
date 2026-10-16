package com.example.hevygroovy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

@Composable
fun WorkoutApp(workouts: WorkoutHomeViewModel, library: LibraryViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab) {
        if (tab == 0) workouts.refresh() else library.refresh()
    }
    BackHandler(enabled = tab != 0) {
        if (tab == 2 && library.state.selectedTemplate != null) library.showTemplateList() else tab = 0
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> WorkoutHomeScreen(workouts)
                1 -> ExerciseLibraryScreen(library)
                2 -> TemplateLibraryScreen(library, workouts.state.activeWorkout != null || workouts.state.loading) { id ->
                    workouts.startFromTemplate(id)
                    tab = 0
                }
            }
        }
        TabRow(selectedTabIndex = tab, modifier = Modifier.navigationBarsPadding()) {
            listOf("Workouts", "Exercises", "Templates").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
    }
}
