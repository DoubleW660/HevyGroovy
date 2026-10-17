package com.example.hevygroovy.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.persistence.WorkoutDatabase
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class RoutinePageFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val user get() = LocalUserIdentity(context).userId

    @Test fun sortingAndRoutineOnlyGraphShowFinishedHistoryWithSelectableMetricsAndProfileUnits() {
        val database = "routine-page-" + UUID.randomUUID() + ".db"
        val fixture = seed(database)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                var unit by mutableStateOf(WeightUnit.LB)
                compose.setContent { HevyGroovyTheme { CompositionLocalProvider(LocalWeightUnit provides unit) {
                    TemplateLibraryScreen(model, false, onStart = {})
                } } }
                await(model)
                compose.onNodeWithText("Sort: Oldest completed first").assertIsDisplayed()
                compose.onNodeWithTag("routine-progress").assertDoesNotExist()
                compose.onNodeWithTag("library-list").performScrollToIndex(2)
                val never = compose.onNodeWithTag("template-card-" + fixture.never).fetchSemanticsNode().boundsInRoot.top
                val older = compose.onNodeWithTag("template-card-" + fixture.older).fetchSemanticsNode().boundsInRoot.top
                val latest = compose.onNodeWithTag("template-card-" + fixture.routine).fetchSemanticsNode().boundsInRoot.top
                assertTrue(never < older && older < latest)
                compose.onNodeWithTag("template-sort").performScrollTo().performClick()
                compose.onNodeWithTag("template-sort-NewestCompleted").performClick()
                compose.onNodeWithText("Sort: Newest completed first").assertIsDisplayed()
                compose.onNodeWithTag("open-template-" + fixture.routine).performScrollTo().performClick()
                await(model)
                compose.runOnIdle { assertEquals(2, model.state.templateHistory.size) }
                compose.onNodeWithTag("routine-chart-value").assertTextEquals("4,760 lb")
                compose.onNodeWithTag("routine-metric-reps").performClick().assertIsSelected()
                compose.onNodeWithTag("routine-chart-value").assertTextEquals("17 reps")
                compose.onNodeWithTag("routine-metric-duration").performClick().assertIsSelected()
                compose.onNodeWithTag("routine-chart-value").assertTextEquals("60 min")
                compose.onNodeWithTag("routine-chart-previous").performScrollTo().performClick()
                compose.onNodeWithTag("routine-chart-value").performScrollTo().assertTextEquals("1 min")
                compose.onNodeWithTag("routine-metric-volume").performClick()
                compose.onNodeWithTag("routine-chart-value").assertTextEquals("1,400 lb")
                compose.runOnIdle { unit = WeightUnit.KG }
                compose.onNodeWithTag("routine-chart-value").assertTextEquals("635 kg")
                compose.onNodeWithTag("routine-chart-next").performScrollTo().performClick()
                compose.onNodeWithTag("routine-chart-value").performScrollTo().assertTextEquals("2,159.1 kg")
                compose.onNodeWithTag("template-list-back").performScrollTo().performClick()
                compose.onNodeWithTag("open-template-" + fixture.never).performScrollTo().performClick()
                await(model)
                compose.onNodeWithText("Finish this routine to see your progress here.").assertIsDisplayed()
                compose.onNodeWithTag("routine-progress-chart").assertDoesNotExist()
            }
        } finally { context.deleteDatabase(database) }
    }

    @Test fun templateDragStaysAtTheGrabPointSupportsCancelAndPersistsOnlyOnDrop() {
        val database = "template-drag-" + UUID.randomUUID() + ".db"
        val fixture = seed(database)
        try {
            AppContainer(context, database).use { container ->
                val model = model(container)
                compose.setContent { HevyGroovyTheme { TemplateLibraryScreen(model, false, onStart = {}) } }
                await(model)
                compose.runOnIdle { model.openTemplate(fixture.routine) }
                await(model)
                Espresso.closeSoftKeyboard()
                val list = compose.onNodeWithTag("library-list")
                list.performScrollToIndex(3)
                val first = compose.onNodeWithTag("reorder-template-exercise-" + fixture.rows.first())
                val original = first.fetchSemanticsNode().boundsInRoot
                val listBounds = list.fetchSemanticsNode().boundsInRoot
                val grab = Offset(original.width * 0.65f, original.height * 0.3f)
                val start = original.topLeft + grab - listBounds.topLeft
                compose.mainClock.autoAdvance = false
                try {
                    var hold = 0L
                    list.performTouchInput { hold = viewConfiguration.longPressTimeoutMillis; down(start) }
                    compose.mainClock.advanceTimeBy(hold + 100)
                    val preview = compose.onNodeWithTag("exercise-drag-preview").fetchSemanticsNode().boundsInRoot
                    assertEquals(original.left, preview.left, 2f)
                    assertEquals(original.top, preview.top, 2f)
                    assertEquals(original.top, first.fetchSemanticsNode().boundsInRoot.top, 2f)
                    list.performTouchInput { moveTo(start + Offset(9f, 30f), 150) }
                    compose.mainClock.advanceTimeBy(100)
                    val moving = compose.onNodeWithTag("exercise-drag-preview").fetchSemanticsNode().boundsInRoot
                    assertEquals(original.left + 9f, moving.left, 2f)
                    assertEquals(original.top + 30f, moving.top, 2f)
                    list.performTouchInput { cancel() }
                    compose.mainClock.advanceTimeBy(100)
                    compose.onNodeWithTag("exercise-drag-preview").assertDoesNotExist()
                    compose.runOnIdle { assertEquals(0L, model.state.savedChanges) }

                    val second = compose.onNodeWithTag("template-exercise-" + fixture.rows.last()).fetchSemanticsNode().boundsInRoot
                    val end = Offset(start.x, (second.center.y - listBounds.top + 30f).coerceAtMost(listBounds.height - 10f))
                    list.performTouchInput { down(start) }
                    compose.mainClock.advanceTimeBy(hold + 100)
                    list.performTouchInput { moveTo(end, 300) }
                    compose.mainClock.advanceTimeBy(300)
                    compose.onNodeWithTag("reorder-drop-" + fixture.rows.last()).assertExists()
                    list.performTouchInput { up() }
                } finally { compose.mainClock.autoAdvance = true }
                compose.waitUntil(10_000) {
                    !model.state.loading && model.state.selectedTemplate?.exercises?.map { it.id } == fixture.rows.reversed()
                }
                compose.runOnIdle { assertEquals(1L, model.state.savedChanges); assertNull(model.state.error) }
            }
            SQLiteAppStorage(context, database).use { reopened ->
                assertEquals(fixture.rows.reversed(), reopened.templates.getTemplateDetail(user, fixture.routine).exercises.map { it.templateExerciseId })
            }
        } finally { context.deleteDatabase(database) }
    }

    private fun model(container: AppContainer): LibraryViewModel {
        lateinit var model: LibraryViewModel
        instrumentation.runOnMainSync { model = LibraryViewModel(container.library); model.refresh() }
        return model
    }
    private fun await(model: LibraryViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }
    private data class Fixture(val routine: Long, val older: Long, val never: Long, val rows: List<Long>)
    private fun seed(database: String): Fixture {
        SQLiteAppStorage(context, database).use { storage ->
            fun template(title: String) = storage.templates.createTemplate(user, CreateTemplateRequest().apply { this.title = title }).templateWorkoutId
            val routine = template("Latest")
            val older = template("Older")
            val never = template("Never")
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Single leg press"; primaryMuscleGroup = MuscleGroup.QUADRICEPS; isUnilateral = true
            }).exerciseId
            repeat(2) { storage.templates.addExerciseToTemplate(user, AddTemplateExerciseRequest().apply {
                templateId = routine; exerciseId = exercise; restTimeSeconds = 60
            }) }
            val rows = storage.templates.getTemplateDetail(user, routine).exercises.map { it.templateExerciseId }
            fun completed(template: Long, start: Long, duration: Long, weight: Double, reps: Int) {
                val workout = storage.workoutSessions.startWorkoutFromTemplate(user, template).id
                val row = storage.workoutSessions.getActiveWorkout(user).get().exercises.firstOrNull()?.workoutExerciseId
                    ?: storage.workoutSessions.addExerciseToWorkout(user, workout, exercise).id
                val set = storage.setEntries.addSet(user, row).id
                storage.setEntries.updateSet(user, set, UpdateSetEntryRequest().apply {
                    this.weight = weight; this.reps = reps; unit = WeightUnit.LB; completed = true
                })
                storage.workoutSessions.finishWorkout(user, workout)
                WorkoutDatabase(context, database).use { helper ->
                    helper.writableDatabase.execSQL("UPDATE logged_workout SET started_at=?,ended_at=? WHERE id=?", arrayOf(start, start + duration, workout))
                }
            }
            val day = 86_400_000L
            val base = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
            completed(older, base, 60_000, 900.0, 99)
            completed(routine, base + day, 60_000, 140.0, 5)
            completed(routine, base + 7 * day, 3_600_000, 140.0, 17)
            return Fixture(routine, older, never, rows)
        }
    }
}
