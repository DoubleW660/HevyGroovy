package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LibraryGateway
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.dto.CreateExerciseRequest
import com.example.hevygroovy.dto.CreateTemplateRequest
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExerciseNavigationFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun exercisePageReturnsToTheActiveWorkoutWithItsUnsavedDraftAndAlsoOpensFromLibraryAndHistory() {
        val database = "exercise-navigation-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = SQLiteAppStorage(context, database).use { storage ->
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Navigation bench"; description = "Set up the bench.\n\nPress and return."
                primaryMuscleGroup = MuscleGroup.CHEST
            }).exerciseId
            val previous = storage.workoutSessions.startEmptyWorkout(user).id
            storage.workoutSessions.updateWorkoutDetails(user, previous, "Previous upper", "")
            val previousRow = storage.workoutSessions.addExerciseToWorkout(user, previous, exercise).id
            val previousSet = storage.setEntries.addSet(user, previousRow).id
            storage.setEntries.updateSet(user, previousSet, UpdateSetEntryRequest().apply {
                weight = 100.0; reps = 5; completed = true
            })
            storage.workoutSessions.finishWorkout(user, previous)
            val active = storage.workoutSessions.startEmptyWorkout(user).id
            val row = storage.workoutSessions.addExerciseToWorkout(user, active, exercise).id
            val set = storage.setEntries.addSet(user, row).id
            val template = storage.templates.createTemplate(user, CreateTemplateRequest().apply { title = "Navigation routine" }).templateWorkoutId
            longArrayOf(exercise, row, set, previous, previousRow, template)
        }
        try {
            AppContainer(context, database).use { container ->
                val libraryLoads = AtomicInteger()
                val libraryGateway = object : LibraryGateway by container.library {
                    override fun loadLibrary(templateId: Long?, callback: LibraryGateway.LibraryCallback) {
                        libraryLoads.incrementAndGet()
                        container.library.loadLibrary(templateId, callback)
                    }
                }
                lateinit var workouts: WorkoutHomeViewModel
                lateinit var library: LibraryViewModel
                lateinit var history: HistoryViewModel
                lateinit var profile: ProfileViewModel
                lateinit var records: ExerciseRecordsViewModel
                instrumentation.runOnMainSync {
                    workouts = WorkoutHomeViewModel(container.workouts)
                    library = LibraryViewModel(libraryGateway)
                    history = HistoryViewModel(container.history)
                    profile = ProfileViewModel(container.profile)
                    records = ExerciseRecordsViewModel(container.records)
                }
                compose.setContent { HevyGroovyTheme { WorkoutApp(workouts, library, history, profile, records) } }
                compose.waitUntil(10_000) { workouts.state.loaded && !workouts.state.loading }
                compose.runOnIdle {
                    assertEquals(0, libraryLoads.get())
                    assertFalse(library.state.loaded)
                }
                compose.onNodeWithTag("resume-workout").performClick()
                compose.onNodeWithTag("inline-weight-${fixture[2]}").performScrollTo().performTextReplacement("invalid")
                compose.onNodeWithTag("open-active-exercise-${fixture[1]}").performScrollTo().performClick()
                compose.waitUntil(10_000) { records.state.loaded && !records.state.loading }
                compose.onNodeWithTag("exercise-detail").assertIsDisplayed()
                compose.onNodeWithTag("exercise-tab-overview").assertIsSelected()
                compose.onNodeWithTag("exercise-tab-history").performClick()
                compose.onNodeWithTag("exercise-history-${fixture[3]}").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("exercise-tab-info").performClick()
                compose.onNodeWithText("Press and return.").performScrollTo().assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals("Exercise details must use the records gateway without loading the library", 0, libraryLoads.get())
                    assertFalse(library.state.loaded)
                }
                compose.onNodeWithTag("exercise-detail-back").performClick()
                compose.onNodeWithTag("inline-weight-${fixture[2]}").performScrollTo().assertTextContains("invalid")
                compose.onNodeWithTag("minimize-workout").performClick()
                compose.onNodeWithTag("exercises-tab").performClick()
                compose.waitUntil(10_000) { library.state.loaded && !library.state.loading }
                val loadedLibraryCalls = libraryLoads.get()
                assertTrue("Opening the Exercises tab must still load its library", loadedLibraryCalls > 0)
                compose.onNodeWithTag("open-library-exercise-${fixture[0]}").performScrollTo().performClick()
                compose.waitUntil(10_000) { records.state.loaded && !records.state.loading }
                compose.onNodeWithTag("exercise-detail").assertIsDisplayed()
                compose.onNodeWithTag("exercise-tab-overview").assertIsSelected()
                compose.runOnIdle { assertEquals("Opening a catalog exercise must reuse the loaded library", loadedLibraryCalls, libraryLoads.get()) }
                compose.onNodeWithTag("exercise-detail-back").performClick()
                compose.onNodeWithTag("guide-exercise-${fixture[0]}").performScrollTo().performClick()
                compose.waitUntil(10_000) { records.state.loaded && !records.state.loading }
                compose.onNodeWithTag("exercise-tab-info").assertIsSelected()
                compose.runOnIdle { assertEquals("Opening the exercise guide must not reload the library", loadedLibraryCalls, libraryLoads.get()) }
                // The global History tab leaves the exercise page and returns to the main app.
                compose.onNodeWithTag("history-tab").performClick()
                compose.onNodeWithTag("exercise-detail").assertDoesNotExist()
                compose.waitUntil(10_000) { history.state.loaded && !history.state.loading }
                compose.onNodeWithTag("history-card-${fixture[3]}").performScrollTo().performClick()
                compose.waitUntil(10_000) { history.state.detail != null && !history.state.detailLoading }
                compose.onNodeWithTag("history-detail").performScrollToNode(hasTestTag("open-history-exercise-${fixture[4]}"))
                compose.onNodeWithTag("open-history-exercise-${fixture[4]}").performClick()
                compose.waitUntil(10_000) { records.state.loaded && !records.state.loading }
                compose.onNodeWithTag("exercise-detail").assertIsDisplayed()
                compose.runOnIdle { assertEquals("Opening an exercise from history must not reload the library", loadedLibraryCalls, libraryLoads.get()) }
                compose.onNodeWithTag("exercise-detail-back").performClick()
                compose.onNodeWithTag("history-detail").assertIsDisplayed()
                compose.runOnIdle { assertEquals(fixture[3], history.state.detail!!.workoutId) }
                // Both reselecting a tab and leaving/returning open its main page.
                compose.onNodeWithTag("history-tab").performClick()
                compose.onNodeWithTag("history-detail").assertDoesNotExist()
                compose.runOnIdle { assertNull(history.state.selectedWorkoutId) }
                compose.onNodeWithTag("exercises-tab").performClick()
                compose.waitUntil(10_000) { !library.state.loading }
                compose.onNodeWithTag("exercise-search").performTextReplacement("no matching name")
                compose.onNodeWithText("No matching exercises.").assertExists()
                compose.onNodeWithTag("exercises-tab").performClick()
                compose.onNodeWithTag("exercise-search").assertTextContains("")
                compose.onNodeWithTag("open-library-exercise-${fixture[0]}").assertExists()
                compose.onNodeWithTag("templates-tab").performClick()
                compose.waitUntil(10_000) { !library.state.loading }
                compose.onNodeWithTag("open-template-${fixture[5]}").performScrollTo().performClick()
                compose.waitUntil(10_000) { library.state.selectedTemplate != null && !library.state.loading }
                compose.onNodeWithTag("templates-tab").performClick()
                compose.runOnIdle { assertNull(library.state.selectedTemplateId) }
                compose.onNodeWithTag("template-list-ready").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("history-tab").performClick()
                compose.waitUntil(10_000) { !history.state.loading }
                compose.onNodeWithTag("history-detail").assertDoesNotExist()
            }
        } finally { context.deleteDatabase(database) }
    }
}
