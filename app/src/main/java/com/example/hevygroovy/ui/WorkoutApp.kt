package com.example.hevygroovy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class AppTab(val title: String, val icon: NavigationIcon, val tag: String) {
    Home("Home", NavigationIcon.Home, "home-tab"),
    Exercises("Exercises", NavigationIcon.Exercises, "exercises-tab"),
    Workout("Workout", NavigationIcon.Workout, "templates-tab"),
    History("History", NavigationIcon.History, "history-tab"),
    Profile("Profile", NavigationIcon.Profile, "profile-tab"),
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun WorkoutApp(workouts: WorkoutHomeViewModel, library: LibraryViewModel, history: HistoryViewModel, profile: ProfileViewModel) {
    ReportOperationDrawn(workouts.performanceTiming)
    ReportOperationDrawn(library.performanceTiming)
    var tab by rememberSaveable(key = "main-destination-v2") { mutableStateOf(AppTab.Home) }
    var sessionOpen by rememberSaveable(key = "session-editor-open") { mutableStateOf(false) }
    var previousWorkoutId by remember { mutableStateOf(workouts.state.activeWorkout?.workoutId) }
    val savedScreens = rememberSaveableStateHolder()
    val active = workouts.state.activeWorkout
    val showSession = sessionOpen && (active != null || workouts.state.loading)

    fun selectTab(destination: AppTab) {
        if (tab == destination) return
        when (destination) {
            AppTab.Home -> workouts.refresh()
            AppTab.Workout, AppTab.Exercises -> library.refresh()
            AppTab.History -> history.refresh()
            AppTab.Profile -> profile.refresh()
        }
        tab = destination
    }
    fun openHistory(id: Long) {
        history.refresh()
        history.openWorkout(id)
        tab = AppTab.History
    }
    fun showHistoryList() {
        history.closeDetail()
        selectTab(AppTab.History)
    }
    LaunchedEffect(Unit) {
        when (tab) {
            AppTab.Home -> Unit // The workout ViewModel loads once on creation.
            AppTab.Workout, AppTab.Exercises -> library.refresh()
            AppTab.History -> history.refresh()
            AppTab.Profile -> profile.refresh()
        }
    }
    LaunchedEffect(active?.workoutId, workouts.state.savedChanges) {
        val completedId = previousWorkoutId
        previousWorkoutId = active?.workoutId
        if (completedId != null && active == null) {
            sessionOpen = false
            if (workouts.state.history.any { it.workoutId == completedId }) openHistory(completedId)
        }
    }
    BackHandler(enabled = showSession || tab != AppTab.Home) {
        when {
            showSession -> sessionOpen = false
            tab == AppTab.History && history.state.selectedWorkoutId != null -> history.closeDetail()
            tab == AppTab.Workout && library.state.selectedTemplateId != null -> library.showTemplateList()
            else -> selectTab(AppTab.Home)
        }
    }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag(if (workouts.performanceTiming.idle && library.performanceTiming.idle) "benchmark-idle" else "benchmark-busy"),
        bottomBar = {
            if (!showSession) Column {
                if (active != null) Surface(
                    onClick = { sessionOpen = true },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().testTag("resume-workout"),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        NavigationGlyph(NavigationIcon.Workout)
                        Column(Modifier.weight(1f)) {
                            Text(active.nameSnapshot ?: "Workout in progress", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Workout in progress", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Resume", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
                    AppTab.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = tab == destination,
                            onClick = { selectTab(destination) },
                            icon = { NavigationGlyph(destination.icon) },
                            label = {
                                Text(destination.title, maxLines = 2, textAlign = TextAlign.Center,
                                    fontSize = 11.sp, letterSpacing = 0.sp,
                                    style = MaterialTheme.typography.labelMedium)
                            },
                            modifier = Modifier.testTag(destination.tag),
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (!showSession && tab == AppTab.Workout) workouts.state.error?.let { message ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message)
                        TextButton(onClick = workouts::refresh, enabled = !workouts.state.loading) { Text("Retry") }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                val route = if (showSession) "session-${active?.workoutId ?: "starting"}" else tab.name
                savedScreens.SaveableStateProvider(route) {
                    if (showSession) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { sessionOpen = false }, modifier = Modifier.testTag("minimize-workout")) { Text("⌄  Minimize") }
                                Spacer(Modifier.weight(1f))
                                Text("ACTIVE SESSION", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                            }
                            Box(Modifier.weight(1f)) { WorkoutHomeScreen(workouts, showHistory = false) }
                        }
                    } else when (tab) {
                        AppTab.Home -> DashboardScreen(workouts,
                            onWorkout = { selectTab(AppTab.Workout) }, onResume = { sessionOpen = true },
                            onHistory = ::showHistoryList, onOpenWorkout = ::openHistory)
                        AppTab.Exercises -> ExerciseLibraryScreen(library)
                        AppTab.Workout -> TemplateLibraryScreen(library,
                            workoutBlocked = active != null || workouts.state.busy,
                            onStart = { id -> workouts.startFromTemplate(id); sessionOpen = true },
                            header = {
                                WorkoutStartPanel(workouts,
                                    onStart = { workouts.startWorkout(); sessionOpen = true },
                                    onResume = { sessionOpen = true })
                            })
                        AppTab.History -> HistoryScreen(history) { selectTab(AppTab.Workout) }
                        AppTab.Profile -> ProfileScreen(profile,
                            onHistory = ::showHistoryList, onExercises = { selectTab(AppTab.Exercises) })
                    }
                }
            }
        }
    }
}
