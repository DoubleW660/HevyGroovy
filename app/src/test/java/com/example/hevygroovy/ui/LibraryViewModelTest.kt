package com.example.hevygroovy.ui

import com.example.hevygroovy.app.LibraryGateway
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class LibraryViewModelTest {
    @Test
    fun refreshIsExplicitAndRepeatedRequestsAreIgnoredWhileLoading() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        assertTrue(gateway.calls.isEmpty())
        model.refresh(); model.refresh()
        assertEquals(listOf("load:null"), gateway.calls)
        gateway.succeed()
        assertTrue(model.state.loaded)
        assertFalse(model.state.loading)
    }

    @Test
    fun failedLoadCanBeRetriedWithoutDiscardingPreviousLibrary() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.refresh(); gateway.succeed()
        model.refresh(); gateway.fail()
        assertNotNull(model.state.error)
        assertEquals(1, model.state.exercises.size)
        model.refresh(); gateway.succeed()
        assertNull(model.state.error)
    }

    @Test
    fun templateCreationRequiresNameAndIgnoresDuplicateSave() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.saveTemplate(null, " ", "")
        assertTrue(gateway.calls.isEmpty())
        model.saveTemplate(null, "Upper", "Routine")
        model.saveTemplate(null, "Upper", "Routine")
        assertEquals(listOf("create:Upper"), gateway.calls)
        gateway.succeed(detail())
        assertEquals(7L, model.state.selectedTemplate?.id)
    }

    @Test
    fun templateEditsRejectUnknownRowsAndInvalidPositionsAndRest() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.refresh(); gateway.succeed(detail())
        model.moveExercise(20, -1); model.moveExercise(20, 1)
        model.removeExercise(999); model.updateRest(20, -1)
        assertEquals(listOf("load:null"), gateway.calls)
        model.updateRest(20, 90)
        assertEquals("rest:7:20:90", gateway.calls.last())
    }

    @Test
    fun archivedTemplatesCannotBeEdited() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.refresh(); gateway.succeed(detail(true))
        model.addExercise(9, 60); model.removeExercise(20); model.updateRest(20, 90)
        assertEquals(listOf("load:null"), gateway.calls)
    }

    private fun detail(archived: Boolean = false): TemplateEditorDetail {
        val model = TemplateDetailModel().apply {
            templateWorkoutId = 7L; title = "Upper"; setArchived(archived)
            exercises = listOf(TemplateExerciseItemModel().apply { templateExerciseId = 20L; exerciseId = 9L; exerciseTitle = "Bench" })
        }
        return TemplateEditorDetail(model)
    }

    private class FakeGateway : LibraryGateway by Mockito.mock(LibraryGateway::class.java) {
        val calls = mutableListOf<String>()
        private lateinit var callback: LibraryGateway.LibraryCallback
        override fun loadLibrary(templateId: Long?, callback: LibraryGateway.LibraryCallback) {
            calls += "load:$templateId"; this.callback = callback
        }
        override fun createTemplate(request: CreateTemplateRequest, callback: LibraryGateway.LibraryCallback) {
            calls += "create:${request.title}"; this.callback = callback
        }
        override fun updateTemplateRest(templateId: Long, rowId: Long, restSeconds: Int, callback: LibraryGateway.LibraryCallback) {
            calls += "rest:$templateId:$rowId:$restSeconds"; this.callback = callback
        }
        fun succeed(detail: TemplateEditorDetail? = null) {
            val exercise = CatalogExercise(ExerciseModel().apply { exerciseId = 9L; title = "Bench"; primaryMuscleGroup = MuscleGroup.CHEST })
            val template = TemplateCard(TemplateSummaryModel().apply { templateWorkoutId = 7L; title = "Upper" })
            callback.onSuccess(LibraryGateway.LibrarySnapshot(listOf(exercise), listOf(template), detail))
        }
        fun fail() { callback.onFailure(IllegalStateException("Test failure")) }
    }
}
