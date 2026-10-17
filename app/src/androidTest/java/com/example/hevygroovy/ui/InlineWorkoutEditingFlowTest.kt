package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveSetEntry
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.dto.ActiveWorkoutExercise
import com.example.hevygroovy.dto.AddTemplateExerciseRequest
import com.example.hevygroovy.dto.CreateExerciseRequest
import com.example.hevygroovy.dto.CreateTemplateRequest
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.entity.LoggedWorkout
import com.example.hevygroovy.entity.SetEntry
import com.example.hevygroovy.entity.WorkoutExercise
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.lang.reflect.Proxy

/** Inline set controls exercise the real worker and an isolated database, without personal data. */
class InlineWorkoutEditingFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun inlineDraftSurvivesRestorationAndCompletionSavesWeightAndRepsTogether() {
        val database = "inline-set-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = seedWorkout(database, user)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                val restoration = StateRestorationTester(compose)
                restoration.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
                awaitLoaded(model)
                compose.onNodeWithTag("inline-weight-${fixture.setId}")
                    .performScrollTo().performTextReplacement("92.5")
                compose.onNodeWithTag("inline-reps-${fixture.setId}")
                    .performTextReplacement("10")
                restoration.emulateSavedInstanceStateRestore()
                compose.onNodeWithTag("inline-weight-${fixture.setId}").assertTextContains("92.5")
                compose.onNodeWithTag("inline-reps-${fixture.setId}").assertTextContains("10")
                compose.onNodeWithTag("inline-complete-${fixture.setId}").performScrollTo().performClick()
                awaitLoaded(model)
                compose.runOnIdle {
                    val set = model.state.activeWorkout!!.exercises.single().sets.single()
                    assertEquals(92.5, set.weight, 0.0)
                    assertEquals(10, set.reps)
                    assertEquals(WeightUnit.LB, set.unit)
                    assertTrue(set.isCompleted)
                }
                compose.onNodeWithTag("inline-complete-${fixture.setId}").assertIsOn()
            }
            SQLiteAppStorage(context, database).use { reopened ->
                val set = reopened.workoutSessions.getActiveWorkout(user).get()
                    .exercises.single().sets.single()
                assertEquals(92.5, set.weight, 0.0)
                assertEquals(10, set.reps)
                assertTrue(set.isCompleted)
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun inlineWarmupAndDropSelectionPersistsAndRespectsRecordEligibility() {
        val database = "inline-type-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = seedWorkout(database, user)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
                awaitLoaded(model)
                compose.onNodeWithTag("inline-type-${fixture.setId}").performScrollTo().performClick()
                compose.onNodeWithText("Warm-up", useUnmergedTree = true).performClick()
                awaitLoaded(model)
                compose.runOnIdle {
                    assertEquals(SetType.WARMUP, model.state.activeWorkout!!.exercises.single().sets.single().setType)
                }
                compose.onNodeWithTag("inline-complete-${fixture.setId}").performScrollTo().performClick()
                awaitLoaded(model)
                compose.runOnIdle {
                    assertTrue(model.state.activeWorkout!!.exercises.single().sets.single().isCompleted)
                    assertTrue(model.state.activeRecords.isEmpty())
                }
                compose.onNodeWithTag("inline-type-${fixture.setId}").performScrollTo().performClick()
                compose.onNodeWithText("Drop", useUnmergedTree = true).performClick()
                awaitLoaded(model)
                compose.runOnIdle {
                    val set = model.state.activeWorkout!!.exercises.single().sets.single()
                    assertEquals(SetType.DROP, set.setType)
                    assertTrue(set.isCompleted)
                    assertFalse(model.state.activeRecords.isEmpty())
                }
            }
            SQLiteAppStorage(context, database).use { reopened ->
                assertEquals(SetType.DROP, reopened.workoutSessions.getActiveWorkout(user).get()
                    .exercises.single().sets.single().setType)
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun previousColumnUsesLastPerformedExerciseAcrossDifferentRoutinesAndKeepsOriginalUnit() {
        val database = "inline-previous-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = seedWorkout(database, user, previousInAnotherRoutine = true)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
                awaitLoaded(model)
                compose.onNode(hasText("60 kg × 8") and
                    hasAnyAncestor(hasTestTag("previous-set-${fixture.setId}")))
                    .performScrollTo().assertIsDisplayed()
                compose.runOnIdle {
                    val exercise = model.state.activeWorkout!!.exercises.single()
                    assertEquals(fixture.previousWorkoutId, exercise.previousWorkoutId)
                    assertEquals(60.0, exercise.previousSets.single().weight, 0.0)
                    assertEquals(WeightUnit.KG, exercise.previousSets.single().unit)
                    // Current input remains separate from the previous performance reference.
                    assertEquals(WeightUnit.LB, exercise.sets.single().unit)
                    assertEquals(80.0, exercise.sets.single().weight, 0.0)
                }
                compose.onNodeWithTag("inline-weight-${fixture.setId}").assertTextContains("80")
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun emptyWorkoutCanBeCancelledOnlyAfterConfirmationAndStaysGoneAfterReopen() {
        val database = "inline-cancel-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val workoutId = SQLiteAppStorage(context, database).use {
            it.workoutSessions.startEmptyWorkout(user).id
        }
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
                awaitLoaded(model)
                compose.onNodeWithTag("cancel-workout").performScrollTo().performClick()
                compose.onNodeWithText("Discard workout?").assertIsDisplayed()
                compose.onNodeWithText("Keep workout").performClick()
                compose.runOnIdle { assertEquals(workoutId, model.state.activeWorkout!!.workoutId) }
                compose.onNodeWithTag("cancel-workout").performScrollTo().performClick()
                compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
                awaitLoaded(model)
                compose.runOnIdle {
                    assertNull(model.state.activeWorkout)
                    assertTrue(model.state.history.isEmpty())
                }
                compose.onNodeWithText("Start empty workout").assertIsDisplayed()
            }
            SQLiteAppStorage(context, database).use { reopened ->
                assertFalse(reopened.workoutSessions.getActiveWorkout(user).isPresent)
                assertTrue(reopened.history.getWorkoutHistory(user).isEmpty())
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun typingOriginalWeightDuringPendingSaveSurvivesItsResponseAndCanBeSavedAgain() {
        val workout = LoggedWorkout().apply { id = 100L; userId = 1L; nameSnapshot = "Draft recovery" }
        val exercise = WorkoutExercise().apply {
            id = 10L; loggedWorkoutId = workout.id; exerciseId = 1L; exerciseNameSnapshot = "Pending bench"
        }
        val saved = SetEntry().apply {
            id = 50L; workoutExerciseId = exercise.id; setNumber = 1
            weight = 100.0; reps = 5; unit = WeightUnit.LB; setType = SetType.NORMAL
        }
        fun snapshot() = WorkoutGateway.Snapshot(ActiveWorkoutDetail(workout,
            listOf(ActiveWorkoutExercise(exercise, listOf(ActiveSetEntry(saved))))), emptyList())
        lateinit var pending: WorkoutGateway.Callback
        val writes = mutableListOf<UpdateSetEntryRequest>()
        val gateway = Proxy.newProxyInstance(WorkoutGateway::class.java.classLoader,
            arrayOf(WorkoutGateway::class.java)) { _, method, arguments ->
            when (method.name) {
                "load" -> (arguments!![0] as WorkoutGateway.Callback).onSuccess(snapshot())
                "updateSet" -> {
                    writes += arguments!![1] as UpdateSetEntryRequest
                    pending = arguments.last() as WorkoutGateway.Callback
                }
                else -> error("Unexpected ${method.name}")
            }
            null
        } as WorkoutGateway
        lateinit var model: WorkoutHomeViewModel
        instrumentation.runOnMainSync { model = WorkoutHomeViewModel(gateway) }
        compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
        compose.onNodeWithTag("inline-weight-50").performScrollTo().performTextReplacement("110")
        compose.onNodeWithTag("inline-weight-50").performImeAction()
        compose.runOnIdle {
            assertEquals(1, writes.size)
            assertEquals(110.0, writes.single().weight!!, 0.0)
            assertTrue(model.state.loading)
        }

        // Inputs remain editable while the worker is saving. Returning to the old value is an edit.
        compose.onNodeWithTag("inline-weight-50").performTextReplacement("100")
        compose.runOnIdle {
            saved.weight = 110.0
            pending.onSuccess(snapshot())
        }
        compose.onNodeWithTag("inline-weight-50").assertTextContains("100")
        compose.runOnIdle { assertEquals(1, writes.size) }
        compose.onNodeWithTag("inline-weight-50").performImeAction()
        compose.runOnIdle {
            assertEquals(2, writes.size)
            assertEquals(100.0, writes.last().weight!!, 0.0)
            saved.weight = 100.0
            pending.onSuccess(snapshot())
        }
        compose.onNodeWithTag("inline-weight-50").assertTextContains("100")
        compose.runOnIdle {
            assertFalse(model.state.loading)
            assertNull(model.state.error)
            assertEquals(100.0, model.state.activeWorkout!!.exercises.single().sets.single().weight, 0.0)
        }
    }

    @Test
    fun holdingAndDraggingExercisePersistsNewOrder() {
        val database = "inline-reorder-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        seedWorkout(database, user)
        val rows = addSecondExercise(database, user)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model, showHistory = false) } }
                awaitLoaded(model)
                // Keep the touch assertion independent of keyboard focus, blur saves and IME resizing.
                Espresso.closeSoftKeyboard()
                compose.onNodeWithTag("workout-list").performScrollToIndex(2)
                compose.waitForIdle()
                val list = compose.onNodeWithTag("workout-list")
                val header = compose.onNodeWithTag("reorder-exercise-${rows[0]}").assertIsDisplayed()
                val original = header.fetchSemanticsNode().boundsInRoot
                val listBounds = list.fetchSemanticsNode().boundsInRoot
                val grab = Offset(original.width * 0.3f, original.height * 0.25f)
                val start = original.topLeft + grab - listBounds.topLeft
                compose.mainClock.autoAdvance = false
                try {
                    var holdMillis = 0L
                    list.performTouchInput { holdMillis = viewConfiguration.longPressTimeoutMillis; down(start) }
                    // Holding must preserve the exact grab point and the list geometry.
                    compose.mainClock.advanceTimeBy(holdMillis + 100)
                    val preview = compose.onNodeWithTag("exercise-drag-preview").fetchSemanticsNode().boundsInRoot
                    assertEquals(original.left, preview.left, 2f)
                    assertEquals(original.top, preview.top, 2f)
                    assertEquals(original.top, header.fetchSemanticsNode().boundsInRoot.top, 2f)
                    val end = Offset(start.x + 7f, listBounds.height - 12f)
                    list.performTouchInput { moveTo(end, 300) }
                    compose.mainClock.advanceTimeBy(1400)
                    val moving = compose.onNodeWithTag("exercise-drag-preview").fetchSemanticsNode().boundsInRoot
                    assertEquals(listBounds.left + end.x - grab.x, moving.left, 2f)
                    assertEquals(listBounds.top + end.y - grab.y, moving.top, 2f)
                    compose.onAllNodes(hasTestTag("reorder-drop-" + rows.last())).assertCountEquals(1)
                    list.performTouchInput { up() }
                } finally {
                    compose.mainClock.autoAdvance = true
                }
                compose.waitUntil(10_000) {
                    !model.state.loading && model.state.activeWorkout?.exercises
                        ?.map { it.workoutExerciseId } == rows.reversed()
                }
                compose.runOnIdle {
                    assertNull(model.state.error)
                    assertEquals(rows.reversed(), model.state.activeWorkout!!.exercises.map { it.workoutExerciseId })
                }
            }
            SQLiteAppStorage(context, database).use { reopened ->
                val exercises = reopened.workoutSessions.getActiveWorkout(user).get().exercises
                assertEquals(rows.reversed(), exercises.map { it.workoutExerciseId })
                assertEquals(listOf(0, 1), exercises.map { it.orderIndex })
                assertEquals(80.0, exercises.flatMap { it.sets }.single().weight, 0.0)
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun accessibleReorderPreservesFocusedInlineDraftUntilItIsSaved() {
        val database = "inline-reorder-draft-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = seedWorkout(database, user)
        val rows = addSecondExercise(database, user)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model, showHistory = false) } }
                awaitLoaded(model)
                compose.onNodeWithTag("inline-weight-${fixture.setId}")
                    .performScrollTo().performTextReplacement("92.5")
                Espresso.closeSoftKeyboard()
                val moveDown = compose.onNodeWithTag("reorder-exercise-${rows[0]}")
                    .fetchSemanticsNode().config[SemanticsActions.CustomActions]
                    .single { it.label == "Move down" }
                compose.runOnUiThread { assertTrue(moveDown.action()) }
                compose.waitUntil(10_000) {
                    !model.state.loading && model.state.activeWorkout?.exercises
                        ?.map { it.workoutExerciseId } == rows.reversed()
                }
                compose.onNodeWithTag("workout-list").performScrollToNode(
                    hasTestTag("inline-weight-${fixture.setId}"))
                compose.onNodeWithTag("inline-weight-${fixture.setId}")
                    .assertTextContains("92.5").performClick().performImeAction()
                compose.waitUntil(10_000) {
                    !model.state.loading && model.state.activeWorkout?.exercises
                        ?.flatMap { it.sets }?.single()?.weight == 92.5
                }
                compose.runOnIdle { assertNull(model.state.error) }
            }
            SQLiteAppStorage(context, database).use { reopened ->
                val exercises = reopened.workoutSessions.getActiveWorkout(user).get().exercises
                assertEquals(rows.reversed(), exercises.map { it.workoutExerciseId })
                assertEquals(92.5, exercises.flatMap { it.sets }.single().weight, 0.0)
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun invalidOrFailedCompletedSetDraftBlocksFinishingUntilTheEditIsSaved() {
        val workout = LoggedWorkout().apply { id = 100L; userId = 1L; nameSnapshot = "Finish guard" }
        val exercise = WorkoutExercise().apply {
            id = 10L; loggedWorkoutId = workout.id; exerciseId = 1L; exerciseNameSnapshot = "Finish bench"
        }
        val saved = SetEntry().apply {
            id = 50L; workoutExerciseId = exercise.id; setNumber = 1
            weight = 100.0; reps = 5; unit = WeightUnit.LB; setType = SetType.NORMAL; isCompleted = true
        }
        fun snapshot() = WorkoutGateway.Snapshot(ActiveWorkoutDetail(workout,
            listOf(ActiveWorkoutExercise(exercise, listOf(ActiveSetEntry(saved))))), emptyList())
        lateinit var pending: WorkoutGateway.Callback
        val writes = mutableListOf<UpdateSetEntryRequest>()
        var finishes = 0
        val gateway = Proxy.newProxyInstance(WorkoutGateway::class.java.classLoader,
            arrayOf(WorkoutGateway::class.java)) { _, method, arguments ->
            when (method.name) {
                "load" -> (arguments!![0] as WorkoutGateway.Callback).onSuccess(snapshot())
                "updateSet" -> {
                    writes += arguments!![1] as UpdateSetEntryRequest
                    pending = arguments.last() as WorkoutGateway.Callback
                }
                "finish" -> {
                    finishes++
                    (arguments!!.last() as WorkoutGateway.Callback)
                        .onSuccess(WorkoutGateway.Snapshot(null, emptyList()))
                }
                else -> error("Unexpected ${method.name}")
            }
            null
        } as WorkoutGateway
        lateinit var model: WorkoutHomeViewModel
        instrumentation.runOnMainSync { model = WorkoutHomeViewModel(gateway) }
        compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
        compose.onNodeWithText("Finish workout").assertIsEnabled()
        compose.onNodeWithTag("inline-weight-50").performScrollTo().performTextReplacement("-1")
        compose.onNodeWithTag("workout-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, writes.size); assertEquals(0, finishes) }

        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("inline-weight-50"))
        compose.onNodeWithTag("inline-weight-50").performTextReplacement("120")
        compose.onNodeWithTag("workout-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("inline-weight-50"))
        compose.onNodeWithTag("inline-weight-50").performImeAction()
        compose.runOnIdle {
            assertEquals(1, writes.size)
            assertTrue(model.state.loading)
            pending.onFailure(IllegalStateException("Write failed"))
        }
        compose.onNodeWithTag("inline-weight-50").assertTextContains("120")
        compose.onNodeWithTag("workout-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, writes.size); assertEquals(0, finishes) }

        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("inline-weight-50"))
        compose.onNodeWithTag("inline-weight-50").performClick().performImeAction()
        compose.runOnIdle {
            assertEquals(2, writes.size)
            assertEquals(120.0, writes.last().weight!!, 0.0)
        }
        // Acknowledging the saved edit must clear the guard even while the row is offscreen.
        compose.onNodeWithTag("workout-list").performScrollToNode(hasText("Finish workout"))
        compose.runOnIdle {
            saved.weight = 120.0
            pending.onSuccess(snapshot())
        }
        compose.onNodeWithText("Finish workout").assertIsEnabled().performClick()
        compose.onNodeWithText("Finish", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(1, finishes)
            assertNull(model.state.activeWorkout)
        }
    }

    private fun model(container: AppContainer): WorkoutHomeViewModel {
        lateinit var model: WorkoutHomeViewModel
        instrumentation.runOnMainSync { model = WorkoutHomeViewModel(container.workouts) }
        return model
    }

    private fun awaitLoaded(model: WorkoutHomeViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }

    private data class Fixture(val setId: Long, val previousWorkoutId: Long?)

    private fun addSecondExercise(database: String, user: Long): List<Long> {
        SQLiteAppStorage(context, database).use { storage ->
            val active = storage.workoutSessions.getActiveWorkout(user).get()
            val first = active.exercises.single().workoutExerciseId
            val squat = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Inline squat"; primaryMuscleGroup = MuscleGroup.QUADRICEPS
            }).exerciseId
            val second = storage.workoutSessions.addExerciseToWorkout(user, active.workoutId, squat).id
            return listOf(first, second)
        }
    }

    private fun seedWorkout(database: String, user: Long, previousInAnotherRoutine: Boolean = false): Fixture {
        SQLiteAppStorage(context, database).use { storage ->
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Inline bench press"
                primaryMuscleGroup = MuscleGroup.CHEST
            }).exerciseId
            var previousWorkoutId: Long? = null
            if (previousInAnotherRoutine) {
                val previousRoutine = routine(storage, user, exercise, "Upper A")
                previousWorkoutId = storage.workoutSessions.startWorkoutFromTemplate(user, previousRoutine).id
                val row = storage.workoutSessions.getActiveWorkout(user).get().exercises.single().workoutExerciseId
                val set = storage.setEntries.addSet(user, row).id
                storage.setEntries.updateSet(user, set, UpdateSetEntryRequest().apply {
                    weight = 60.0; reps = 8; unit = WeightUnit.KG; completed = true
                })
                storage.workoutSessions.finishWorkout(user, previousWorkoutId)
            }
            val currentRoutine = routine(storage, user, exercise, "Upper B")
            storage.workoutSessions.startWorkoutFromTemplate(user, currentRoutine)
            val row = storage.workoutSessions.getActiveWorkout(user).get().exercises.single().workoutExerciseId
            val set = storage.setEntries.addSet(user, row).id
            storage.setEntries.updateSet(user, set, UpdateSetEntryRequest().apply {
                weight = 80.0; reps = 8; unit = WeightUnit.LB; completed = false
            })
            return Fixture(set, previousWorkoutId)
        }
    }

    private fun routine(storage: SQLiteAppStorage, user: Long, exercise: Long, name: String): Long {
        val id = storage.templates.createTemplate(user, CreateTemplateRequest().apply { title = name }).templateWorkoutId
        storage.templates.addExerciseToTemplate(user, AddTemplateExerciseRequest().apply {
            templateId = id; exerciseId = exercise
        })
        return id
    }
}
