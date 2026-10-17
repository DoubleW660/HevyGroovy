package com.example.hevygroovy.ui

import com.example.hevygroovy.app.LibraryGateway
import com.example.hevygroovy.app.CommittedRefreshException
import androidx.lifecycle.SavedStateHandle
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class LibraryViewModelTest {
    @Test
    fun exerciseMovementChoiceIsSavedAndSignalsDependentViewsOnlyAfterCommit() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.saveExercise(null, "Single leg press", "", MuscleGroup.QUADRICEPS, emptyList(), true)
        assertTrue(gateway.createdExercise!!.isUnilateral)
        assertEquals(0L, model.state.exerciseChanges)
        gateway.fail()
        assertEquals(0L, model.state.exerciseChanges)
        model.refresh(); gateway.succeed()
        model.saveExercise(9, "Single leg press", "", MuscleGroup.QUADRICEPS, emptyList(), false)
        assertEquals(false, gateway.updatedExercise!!.unilateral)
        gateway.succeed()
        assertEquals(1L, model.state.exerciseChanges)
        model.refresh(); gateway.succeed()
        assertEquals(1L, model.state.exerciseChanges)
        model.saveExercise(9, "Renamed", "", MuscleGroup.QUADRICEPS, emptyList())
        assertNull(gateway.updatedExercise!!.unilateral)
        gateway.fail(CommittedRefreshException(IllegalStateException("Read failed"), null))
        assertEquals(2L, model.state.exerciseChanges)
    }
    @Test
    fun failedSaveKeepsDraftAndCanBeRetried() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.beginTemplateDetails()
        model.updateTemplateDraft(title = "Upper", description = "Keep this draft")
        model.saveTemplate(null, "Upper", "Keep this draft")
        gateway.fail()
        assertEquals("Keep this draft", model.templateDraft?.description)
        assertEquals(0L, model.state.savedChanges)
        assertFalse(model.state.busy)
        model.saveTemplate(null, "Upper", "Keep this draft")
        gateway.succeed(detail())
        assertNull(model.templateDraft)
        assertEquals(1L, model.state.savedChanges)
    }

    @Test
    fun committedCreateWithRefreshFailureOnlyAllowsReadRetryAndKeepsCreatedId() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.beginTemplateDetails()
        model.saveTemplate(null, "Upper", "")
        gateway.fail(CommittedRefreshException(IllegalStateException("Read failed"), 7L))
        assertNull(model.templateDraft)
        assertTrue(model.state.refreshRequired)
        assertEquals(1L, model.state.savedChanges)
        model.saveTemplate(null, "Upper", "")
        assertEquals(listOf("create:Upper"), gateway.calls)
        model.refresh(); gateway.fail()
        assertTrue(model.state.refreshRequired)
        model.refresh(); gateway.succeed(detail())
        assertEquals(listOf("create:Upper", "load:7", "load:7"), gateway.calls)
        assertFalse(model.state.busy)
        assertEquals(7L, model.state.selectedTemplate?.id)
        assertEquals(1L, model.state.savedChanges)
    }

    @Test
    fun newViewModelRestoresTemplateSelectionAndUnsavedEditorDraftBeforeLoading() {
        val handle = SavedStateHandle()
        val firstGateway = FakeGateway()
        val first = LibraryViewModel(firstGateway, handle)
        first.refresh(); firstGateway.succeed(detail())
        first.beginTemplateDetails()
        first.updateTemplateDraft(title = "Unsaved name", description = "Unsaved notes")
        // Copy only saved primitive values into a new handle and a completely new ViewModel.
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val gateway = FakeGateway()
        val restored = LibraryViewModel(gateway, restoredHandle)
        assertEquals(7L, restored.state.selectedTemplateId)
        assertNull(restored.state.selectedTemplate)
        restored.refresh()
        assertEquals(listOf("load:7"), gateway.calls)
        gateway.succeed(detail())
        assertEquals("Unsaved name", restored.templateDraft?.title)
        assertEquals("Unsaved notes", restored.templateDraft?.description)
        restored.showTemplateList()
        assertNull(restored.state.selectedTemplateId)
        assertNull(restored.templateDraft)
    }

    @Test
    fun openingTemplateUsesDetailLoadAndDoesNotCountAsASave() {
        val gateway = FakeGateway()
        val model = LibraryViewModel(gateway)
        model.refresh(); gateway.succeed()
        model.openTemplate(7); gateway.succeed(detail())
        assertEquals(listOf("load:null", "detail:7"), gateway.calls)
        assertEquals(0L, model.state.savedChanges)
    }
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
        var createdExercise: CreateExerciseRequest? = null
        var updatedExercise: UpdateExerciseRequest? = null
        private lateinit var callback: LibraryGateway.LibraryCallback
        override fun loadLibrary(templateId: Long?, callback: LibraryGateway.LibraryCallback) {
            calls += "load:$templateId"; this.callback = callback
        }
        override fun loadTemplate(templateId: Long, callback: LibraryGateway.LibraryCallback) {
            calls += "detail:$templateId"; this.callback = callback
        }
        override fun createTemplate(request: CreateTemplateRequest, callback: LibraryGateway.LibraryCallback) {
            calls += "create:${request.title}"; this.callback = callback
        }
        override fun createCatalogExercise(request: CreateExerciseRequest, callback: LibraryGateway.LibraryCallback) {
            createdExercise = request; this.callback = callback
        }
        override fun updateCatalogExercise(id: Long, request: UpdateExerciseRequest, callback: LibraryGateway.LibraryCallback) {
            updatedExercise = request; this.callback = callback
        }
        override fun updateTemplateRest(templateId: Long, rowId: Long, restSeconds: Int, callback: LibraryGateway.LibraryCallback) {
            calls += "rest:$templateId:$rowId:$restSeconds"; this.callback = callback
        }
        fun succeed(detail: TemplateEditorDetail? = null) {
            val exercise = CatalogExercise(ExerciseModel().apply { exerciseId = 9L; title = "Bench"; primaryMuscleGroup = MuscleGroup.CHEST })
            val template = TemplateCard(TemplateSummaryModel().apply { templateWorkoutId = 7L; title = "Upper" })
            callback.onSuccess(LibraryGateway.LibrarySnapshot(listOf(exercise), listOf(template), detail))
        }
        fun fail(error: Exception = IllegalStateException("Test failure")) { callback.onFailure(error) }
    }
}
