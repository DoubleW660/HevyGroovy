package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ExerciseGuideTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longExerciseGuideScrollsToLastInstructionAndClosesBackToLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "exercise-guide-${UUID.randomUUID()}.db"
        val container = AppContainer(context, databaseName)
        lateinit var model: LibraryViewModel
        lateinit var records: ExerciseRecordsViewModel
        val lastInstruction = "Final instruction: return the equipment to its starting position."
        val description = ((1..24).map { step ->
            "$step. Demonstration instruction for this exercise. Read the complete paragraph before continuing to the next step."
        } + lastInstruction).joinToString("\n\n")
        try {
            compose.runOnUiThread {
                model = LibraryViewModel(container.library)
                records = ExerciseRecordsViewModel(container.records)
                model.saveExercise(null, "Long exercise guide", description, MuscleGroup.OTHER, emptyList())
            }
            awaitLibrary(model)
            compose.runOnUiThread {
                model.saveExercise(null, "Without a guide", "", MuscleGroup.OTHER, emptyList())
            }
            awaitLibrary(model)
            val guidedId = model.state.exercises.single { it.title == "Long exercise guide" }.id
            val blankId = model.state.exercises.single { it.title == "Without a guide" }.id
            compose.setContent { HevyGroovyTheme { ExerciseLibraryScreen(model, records) } }

            compose.onNodeWithTag("guide-exercise-$blankId").assertDoesNotExist()
            compose.onNodeWithTag("guide-exercise-$guidedId").performScrollTo().performClick()
            compose.waitUntil(10_000) { records.state.loaded && !records.state.loading }
            compose.onNodeWithTag("exercise-detail").assertIsDisplayed()
            compose.onNode(hasText("Long exercise guide") and hasAnyAncestor(hasTestTag("exercise-detail")))
                .assertIsDisplayed()
            compose.onNodeWithTag("exercise-detail-back").assertIsDisplayed()
            compose.onNodeWithText(lastInstruction).assertDoesNotExist()
            compose.onNodeWithTag("exercise-info-list").performScrollToNode(hasText(lastInstruction))
            compose.onNodeWithText(lastInstruction).assertIsDisplayed()
            compose.onNodeWithTag("exercise-detail-back").assertIsDisplayed().performClick()

            compose.onNodeWithTag("exercise-detail").assertDoesNotExist()
            compose.onNodeWithTag("guide-exercise-$guidedId").assertIsDisplayed()
            compose.onNodeWithTag("edit-exercise-$guidedId").assertIsDisplayed()
            compose.runOnIdle { assertEquals(description, model.state.exercises.single { it.id == guidedId }.description) }
        } finally {
            container.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun awaitLibrary(model: LibraryViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }
}
