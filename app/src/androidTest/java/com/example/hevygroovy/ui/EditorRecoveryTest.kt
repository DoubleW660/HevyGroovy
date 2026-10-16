package com.example.hevygroovy.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.example.hevygroovy.app.*
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.model.*
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class EditorRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogFailureKeepsDraftUntilRetryCommits() {
        val fake = LibraryFake()
        val model = LibraryViewModel(fake.gateway)
        compose.runOnUiThread { model.refresh() }
        compose.setContent { HevyGroovyTheme { ExerciseLibraryScreen(model) } }
        compose.onNodeWithText("New exercise").performClick()
        compose.onNodeWithTag("catalog-exercise-name").performTextReplacement("Draft exercise")
        compose.onNodeWithTag("catalog-exercise-description").performTextReplacement("Keep my description")
        compose.onNodeWithText("Save exercise").performClick()
        compose.onNodeWithText("Save exercise").assertIsNotEnabled()
        compose.runOnIdle { fake.fail() }
        compose.onNodeWithTag("catalog-exercise-name").assertTextContains("Draft exercise")
        compose.onNodeWithTag("catalog-exercise-description").assertTextContains("Keep my description")
        compose.onNodeWithText("Save exercise").assertIsEnabled().performClick()
        compose.runOnIdle { fake.succeed() }
        compose.onNodeWithTag("catalog-exercise-name").assertDoesNotExist()
        assertEquals(2, fake.writes)
    }

    @Test fun templateSaveFailureRetainsDraftAndCommittedRefreshFailureOnlyRetriesRead() {
        val fake = LibraryFake()
        val model = LibraryViewModel(fake.gateway)
        compose.runOnUiThread { model.refresh() }
        compose.setContent { HevyGroovyTheme { TemplateLibraryScreen(model, false) {} } }
        compose.onNodeWithTag("open-template-7").performClick()
        compose.onNodeWithText("Edit routine").performClick()
        compose.onNodeWithTag("template-name").performTextReplacement("Unsaved template")
        compose.onNodeWithText("Save routine").performClick()
        compose.runOnIdle { fake.fail() }
        compose.onNodeWithTag("template-name").assertTextContains("Unsaved template")
        compose.onNodeWithText("Save routine").assertIsEnabled().performClick()
        compose.runOnIdle { fake.fail(CommittedRefreshException(IllegalStateException("Read failed"), 7L)) }
        compose.onNodeWithTag("template-name").assertDoesNotExist()
        compose.onNodeWithText("Edit routine").assertIsNotEnabled()
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.onNodeWithText("Edit routine").assertIsEnabled()
        assertEquals(2, fake.writes)
    }

    @Test fun newViewModelRestoresTemplateAndDraftWhileItsDetailIsReloading() {
        val fake = LibraryFake()
        val handle = SavedStateHandle()
        val current = mutableStateOf(LibraryViewModel(fake.gateway, handle))
        compose.runOnUiThread { current.value.refresh() }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { HevyGroovyTheme { TemplateLibraryScreen(current.value, false) {} } }
        compose.onNodeWithTag("open-template-7").performClick()
        compose.onNodeWithText("Edit routine").performClick()
        compose.onNodeWithTag("template-name").performTextReplacement("Restored draft")
        compose.onNodeWithTag("template-description").performTextReplacement("Unsaved description")
        val restoredGateway = LibraryFake().apply { delayLoads = true }
        compose.runOnIdle {
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            current.value = LibraryViewModel(restoredGateway.gateway, restoredHandle)
            current.value.refresh()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Loading routine…").assertIsDisplayed()
        compose.runOnIdle { restoredGateway.succeed(detail = true) }
        compose.onNodeWithTag("template-name").assertTextContains("Restored draft")
        compose.onNodeWithTag("template-description").assertTextContains("Unsaved description")
        assertEquals(listOf(7L), restoredGateway.loadedIds)
        assertEquals(0, restoredGateway.writes)
    }

    @Test fun workoutAndSetSaveFailuresKeepEnteredValues() {
        val workout = LoggedWorkout().apply { id = 100L; userId = 1L; nameSnapshot = "Session" }
        val row = WorkoutExercise().apply { id = 10L; exerciseNameSnapshot = "Bench" }
        val set = SetEntry().apply { id = 50L; setNumber = 1; reps = 0; weight = 0.0 }
        fun snapshot() = WorkoutGateway.Snapshot(ActiveWorkoutDetail(workout,
            listOf(ActiveWorkoutExercise(row, listOf(ActiveSetEntry(set))))), emptyList())
        lateinit var pending: WorkoutGateway.Callback
        var pendingSet: UpdateSetEntryRequest? = null
        val gateway = Proxy.newProxyInstance(WorkoutGateway::class.java.classLoader, arrayOf(WorkoutGateway::class.java)) { _, method, args ->
            when (method.name) {
                "load" -> (args!![0] as WorkoutGateway.Callback).onSuccess(snapshot())
                "updateWorkout" -> pending = args!!.last() as WorkoutGateway.Callback
                "updateSet" -> {
                    pendingSet = args!![1] as UpdateSetEntryRequest
                    pending = args.last() as WorkoutGateway.Callback
                }
                else -> error("Unexpected ${method.name}")
            }
            null
        } as WorkoutGateway
        val model = WorkoutHomeViewModel(gateway)
        compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
        compose.onNodeWithText("Edit workout details").performClick()
        compose.onNodeWithTag("workout-name").performTextReplacement("My draft session")
        compose.onNodeWithTag("workout-notes").performTextReplacement("Keep these notes")
        compose.onNodeWithText("Save workout").performClick()
        compose.runOnIdle { pending.onFailure(IllegalStateException("Write failed")) }
        compose.onNodeWithTag("workout-name").assertTextContains("My draft session")
        compose.onNodeWithTag("workout-notes").assertTextContains("Keep these notes")
        compose.onNodeWithText("Save workout").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("inline-weight-50").performScrollTo().performTextReplacement("42.5")
        compose.onNodeWithTag("inline-reps-50").performTextReplacement("8")
        compose.onNodeWithTag("inline-complete-50").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(42.5, pendingSet!!.weight!!, 0.0)
            assertEquals(8, pendingSet!!.reps!!.toInt())
            assertTrue(pendingSet!!.completed!!)
        }
        compose.runOnIdle { pending.onFailure(IllegalStateException("Write failed")) }
        compose.onNodeWithTag("inline-weight-50").assertTextContains("42.5")
        compose.onNodeWithTag("inline-reps-50").assertTextContains("8")
        compose.onNodeWithTag("inline-complete-50").performScrollTo().assertIsOff().assertIsEnabled().performClick()
        compose.runOnIdle {
            set.weight = pendingSet!!.weight!!
            set.reps = pendingSet!!.reps!!
            set.unit = pendingSet!!.unit
            set.setType = pendingSet!!.setType
            set.isCompleted = pendingSet!!.completed!!
            pending.onSuccess(snapshot())
        }
        compose.onNodeWithTag("inline-weight-50").assertTextContains("42.5")
        compose.onNodeWithTag("inline-reps-50").assertTextContains("8")
        compose.onNodeWithTag("inline-complete-50").assertIsOn()
    }

    private class LibraryFake {
        var delayLoads = false
        var writes = 0
        val loadedIds = mutableListOf<Long?>()
        private lateinit var pending: LibraryGateway.LibraryCallback
        val gateway = Proxy.newProxyInstance(LibraryGateway::class.java.classLoader, arrayOf(LibraryGateway::class.java)) { _, method, args ->
            when (method.name) {
                "loadLibrary", "loadTemplate" -> {
                    loadedIds += args!![0] as Long?
                    pending = args.last() as LibraryGateway.LibraryCallback
                    if (!delayLoads) succeed(detail = args[0] != null)
                }
                "createCatalogExercise", "updateTemplate" -> {
                    writes++
                    pending = args!!.last() as LibraryGateway.LibraryCallback
                }
                else -> error("Unexpected ${method.name}")
            }
            null
        } as LibraryGateway

        fun fail(error: Exception = IllegalStateException("Write failed")) = pending.onFailure(error)
        fun succeed(detail: Boolean = false) {
            val card = TemplateCard(TemplateSummaryModel().apply { templateWorkoutId = 7L; title = "Upper" })
            val selected = if (detail) TemplateEditorDetail(TemplateDetailModel().apply { templateWorkoutId = 7L; title = "Upper" }) else null
            pending.onSuccess(LibraryGateway.LibrarySnapshot(emptyList(), listOf(card), selected))
        }
    }
}
