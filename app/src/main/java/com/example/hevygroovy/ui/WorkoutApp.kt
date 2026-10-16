package com.example.hevygroovy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun WorkoutApp(workouts: WorkoutHomeViewModel, library: LibraryViewModel) {
    ReportOperationDrawn(workouts.performanceTiming)
    ReportOperationDrawn(library.performanceTiming)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The workout ViewModel starts its own initial load. Only restored library tabs
    // need an initial request here; subsequent navigation dispatches before changing tabs.
    LaunchedEffect(Unit) {
        if (tab != 0) library.refresh()
    }
    fun selectTab(index: Int) {
        if (tab == index) return
        if (index == 0) workouts.refresh() else library.refresh()
        tab = index
    }
    BackHandler(enabled = tab != 0) {
        if (tab == 2 && library.state.selectedTemplateId != null) library.showTemplateList() else selectTab(0)
    }
    Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }
        .testTag(if (workouts.performanceTiming.idle && library.performanceTiming.idle) "benchmark-idle" else "benchmark-busy")) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> WorkoutHomeScreen(workouts)
                1 -> ExerciseLibraryScreen(library)
                2 -> TemplateLibraryScreen(library, workouts.state.activeWorkout != null || workouts.state.busy) { id ->
                    workouts.startFromTemplate(id)
                    tab = 0
                }
            }
        }
        TabRow(selectedTabIndex = tab, modifier = Modifier.navigationBarsPadding()) {
            listOf("Workouts", "Exercises", "Templates").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { selectTab(index) }, text = { Text(title) },
                    modifier = Modifier.testTag(listOf("home-tab", "exercises-tab", "templates-tab")[index]))
            }
        }
    }
}
