package com.example.hevygroovy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hevygroovy.dto.PersonalRecord
import com.example.hevygroovy.resttimer.RestTimerController
import kotlinx.coroutines.flow.first

private enum class AppTab(val title: String, val icon: NavigationIcon, val tag: String) {
    Home("Home", NavigationIcon.Home, "home-tab"),
    Exercises("Exercises", NavigationIcon.Exercises, "exercises-tab"),
    Workout("Workout", NavigationIcon.Workout, "templates-tab"),
    History("History", NavigationIcon.History, "history-tab"),
    Profile("Profile", NavigationIcon.Profile, "profile-tab"),
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun WorkoutApp(workouts: WorkoutHomeViewModel, library: LibraryViewModel, history: HistoryViewModel,
    profile: ProfileViewModel, records: ExerciseRecordsViewModel? = null,
    restTimer: RestTimerController? = null, openWorkoutId: Long? = null,
    onWorkoutOpened: () -> Unit = {}, imports: HistoryImportViewModel? = null) {
    ReportOperationDrawn(workouts.performanceTiming)
    ReportOperationDrawn(library.performanceTiming)
    var tab by rememberSaveable(key = "main-destination-v2") { mutableStateOf(AppTab.Home) }
    var tabVisit by rememberSaveable { mutableLongStateOf(0L) }
    var sessionOpen by rememberSaveable(key = "session-editor-open") { mutableStateOf(false) }
    var exerciseName by rememberSaveable { mutableStateOf("Exercise") }
    var exerciseInitialTab by rememberSaveable { mutableStateOf(0) }
    var previousWorkoutId by remember { mutableStateOf(workouts.state.activeWorkout?.workoutId) }
    val savedScreens = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val compactNavigation = screenWidth < 360 || (screenWidth < 480 && LocalDensity.current.fontScale > 1.2f)
    val active = workouts.state.activeWorkout
    val exerciseId = records?.state?.selectedExerciseId
    val showSession = sessionOpen && (active != null || workouts.state.loading)
    LaunchedEffect(openWorkoutId, active?.workoutId, workouts.state.loaded) {
        if (openWorkoutId != null && workouts.state.loaded) {
            if (openWorkoutId == active?.workoutId) {
                records?.close()
                sessionOpen = true
            }
            onWorkoutOpened()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    var recordDetails by remember { mutableStateOf<List<PersonalRecord>>(emptyList()) }
    LaunchedEffect(exerciseId, records?.state?.loaded, records?.state?.loading, records?.state?.error) {
        if (exerciseId != null && !records.state.loaded &&
            !records.state.loading && records.state.error == null) records.refresh(exerciseId)
    }
    LaunchedEffect(workouts.state.recordNoticeVersion) {
        val notices = workouts.state.recordNotice
        if (notices.isNotEmpty()) {
            // Consume before showing: rotation must not announce the same achievement again.
            workouts.dismissRecordNotice()
            val names = notices.map { it.exerciseName }.distinct().joinToString(", ")
            val message = if (notices.size == 1) "New record · $names" else "${notices.size} new records · $names"
            if (snackbar.showSnackbar(message, actionLabel = "View", withDismissAction = true,
                    duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                recordDetails = notices
            }
        }
    }

    fun selectTab(destination: AppTab) {
        focus.clearFocus()
        keyboard?.hide()
        savedScreens.removeState("${tab.name}-$tabVisit")
        tabVisit++
        if (records?.state?.selectedExerciseId != null) records.close()
        sessionOpen = false
        history.resetNavigation()
        library.showTemplateList()
        when (destination) {
            AppTab.Home -> workouts.refresh()
            AppTab.Workout, AppTab.Exercises -> library.refresh()
            AppTab.History -> history.refresh()
            AppTab.Profile -> profile.refresh()
        }
        tab = destination
    }
    fun openExercise(id: Long, name: String, initialTab: Int = 0) {
        if (records == null || id <= 0) return
        exerciseName = name
        exerciseInitialTab = initialTab
        // A fresh visit honors its shortcut; rotation still restores the current page.
        savedScreens.removeState("exercise-$id-$initialTab")
        records.refresh(id)
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
        profile.refresh()
        when (tab) {
            AppTab.Home -> Unit // The workout ViewModel loads once on creation.
            AppTab.Workout, AppTab.Exercises -> library.refresh()
            AppTab.History -> history.refresh()
            AppTab.Profile -> profile.refresh()
        }
    }
    LaunchedEffect(history.state.deletedWorkouts, library.state.exerciseChanges) {
        if (history.state.deletedWorkouts > 0 || library.state.exerciseChanges > 0) {
            records?.invalidate()
            snapshotFlow { workouts.state.loading }.first { !it }
            workouts.refresh()
            if (library.state.exerciseChanges > 0 && history.state.loaded) {
                snapshotFlow { history.state.loading }.first { !it }
                history.refresh()
            }
        }
    }
    LaunchedEffect(imports?.state?.changes) {
        if ((imports?.state?.changes ?: 0) > 0) {
            records?.invalidate()
            snapshotFlow { workouts.state.loading }.first { !it }; workouts.refresh()
            snapshotFlow { library.state.loading }.first { !it }; library.refresh()
            snapshotFlow { history.state.loading || history.state.deleting }.first { !it }; history.refresh()
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
    BackHandler(enabled = exerciseId != null || showSession || tab != AppTab.Home) {
        when {
            exerciseId != null -> records?.close()
            showSession -> sessionOpen = false
            tab == AppTab.History && history.state.selectedWorkoutId != null -> history.closeDetail()
            tab == AppTab.Workout && library.state.selectedTemplateId != null -> library.showTemplateList()
            else -> selectTab(AppTab.Home)
        }
    }
    CompositionLocalProvider(LocalWeightUnit provides profile.state.unit) {
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag(if (workouts.performanceTiming.idle && library.performanceTiming.idle) "benchmark-idle" else "benchmark-busy"),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.testTag("new-record-snackbar")) },
        bottomBar = {
            Column(Modifier.imePadding()) {
            if (restTimer != null) RestTimerControls(restTimer,
                modifier = if (showSession) Modifier.navigationBarsPadding() else Modifier)
            if (!showSession) Column {
                if (active != null) Surface(
                    onClick = { records?.close(); sessionOpen = true },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().testTag("resume-workout"),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(Modifier.widthIn(max = GroovyLayout.ContentMaxWidth).fillMaxWidth()
                        .padding(horizontal = GroovyLayout.ScreenPadding, vertical = 12.dp),
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
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NavigationBar(modifier = Modifier.widthIn(max = GroovyLayout.ContentMaxWidth).fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
                    AppTab.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = tab == destination,
                            onClick = { selectTab(destination) },
                            icon = { NavigationGlyph(destination.icon) },
                            label = {
                                val label = if (compactNavigation) when (destination) {
                                    AppTab.Exercises -> "Library"
                                    AppTab.Workout -> "Train"
                                    else -> destination.title
                                } else destination.title
                                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    fontSize = 11.sp, letterSpacing = 0.sp,
                                    style = MaterialTheme.typography.labelMedium)
                            },
                            modifier = Modifier.testTag(destination.tag).semantics { contentDescription = destination.title },
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
                }
            }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = GroovyLayout.ContentMaxWidth).fillMaxSize()) {
            if (!showSession && tab == AppTab.Workout) workouts.state.error?.let { message ->
                ErrorCard(message, onRetry = workouts::refresh, retryEnabled = !workouts.state.loading,
                    modifier = Modifier.padding(horizontal = GroovyLayout.ScreenPadding, vertical = 8.dp))
            }
            Box(Modifier.weight(1f)) {
                val route = when {
                    exerciseId != null -> "exercise-$exerciseId-$exerciseInitialTab"
                    showSession -> "session-${active?.workoutId ?: "starting"}"
                    else -> "${tab.name}-$tabVisit"
                }
                key(route) { savedScreens.SaveableStateProvider(route) {
                    if (exerciseId != null) {
                        ExerciseDetailScreen(records, exerciseName, onBack = records::close,
                            initialTab = exerciseInitialTab, library = library)
                    } else if (showSession) {
                        WorkoutHomeScreen(workouts, showHistory = false, onMinimize = { sessionOpen = false },
                            onOpenExercise = { id, name -> openExercise(id, name) })
                    } else when (tab) {
                        AppTab.Home -> DashboardScreen(workouts,
                            onWorkout = { selectTab(AppTab.Workout) }, onResume = { sessionOpen = true },
                            onHistory = ::showHistoryList, onOpenWorkout = ::openHistory)
                        AppTab.Exercises -> ExerciseLibraryScreen(library, records,
                            onOpenExercise = { id, name, initialTab -> openExercise(id, name, initialTab) })
                        AppTab.Workout -> TemplateLibraryScreen(library,
                            workoutBlocked = active != null || workouts.state.busy,
                            onOpenExercise = { id, name -> openExercise(id, name) },
                            onStart = { id -> workouts.startFromTemplate(id); sessionOpen = true },
                            header = {
                                WorkoutStartPanel(workouts,
                                    onStart = { workouts.startWorkout(); sessionOpen = true },
                                    onResume = { sessionOpen = true })
                            })
                        AppTab.History -> HistoryScreen(history,
                            onOpenExercise = { id, name -> openExercise(id, name) }) { selectTab(AppTab.Workout) }
                        AppTab.Profile -> ProfileScreen(profile,
                            onHistory = ::showHistoryList, onExercises = { selectTab(AppTab.Exercises) }, imports = imports)
                    }
                } }
            }
        }
        }
    }
    if (recordDetails.isNotEmpty()) {
        AlertDialog(onDismissRequest = { recordDetails = emptyList() },
            modifier = Modifier.testTag("record-notice-dialog"), title = { Text("New personal records") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    PersonalRecordSection(recordDetails, provisional = workouts.state.activeWorkout != null)
                }
            },
            confirmButton = {
                TextButton(onClick = { recordDetails = emptyList() }, modifier = Modifier.testTag("record-notice-close")) { Text("Close") }
            })
    }
    }
}
