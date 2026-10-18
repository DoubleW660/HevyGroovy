package com.example.hevygroovy.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.hevygroovy.app.CommittedRefreshException
import com.example.hevygroovy.app.LibraryGateway
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup

data class LibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val exercises: List<CatalogExercise> = emptyList(),
    val templates: List<TemplateCard> = emptyList(),
    val selectedTemplate: TemplateEditorDetail? = null,
    val selectedTemplateId: Long? = null,
    val lastCompletedAtByTemplate: Map<Long, Long> = emptyMap(),
    val templateHistory: List<WorkoutHistory> = emptyList(),
    val savedChanges: Long = 0,
    val exerciseChanges: Long = 0,
    val refreshRequired: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean get() = loading || refreshRequired
}

data class TemplateDraft(val kind: String, val templateId: Long? = null, val rowId: Long? = null,
    val title: String = "", val description: String = "", val rest: String = "0", val search: String = "")

class LibraryViewModel(private val gateway: LibraryGateway, private val savedState: SavedStateHandle = SavedStateHandle(),
    val performanceTiming: UiPerformanceTiming = UiPerformanceTiming()) : ViewModel() {
    var state by mutableStateOf(LibraryState(selectedTemplateId = savedState["selectedTemplateId"]))
        private set
    var templateSort by mutableStateOf(TemplateSort.entries.find { it.name == savedState.get<String>("templateSort") }
        ?: TemplateSort.OldestCompleted)
        private set
    fun selectTemplateSort(sort: TemplateSort) {
        templateSort = sort
        savedState["templateSort"] = sort.name
    }
    var templateDraft by mutableStateOf(savedState.get<String>("draftKind")?.let {
        TemplateDraft(it, savedState["draftTemplateId"], savedState["draftRowId"],
            savedState["draftTitle"] ?: "", savedState["draftDescription"] ?: "",
            savedState["draftRest"] ?: "0", savedState["draftSearch"] ?: "")
    })
        private set
    private var cleared = false
    private var navigationVersion = 0L

    fun refresh() = execute(mutation = false) { gateway.loadLibrary(state.selectedTemplateId, it) }
    fun showTemplateList() {
        navigationVersion++
        selectTemplate(null)
        state = state.copy(selectedTemplate = null, templateHistory = emptyList())
        closeTemplateDraft()
    }
    fun openTemplate(id: Long) {
        if (state.busy || state.templates.none { it.id == id }) return
        selectTemplate(id)
        execute(mutation = false, traceName = "template_open") { gateway.loadTemplate(id, it) }
    }

    private fun selectTemplate(id: Long?) {
        savedState["selectedTemplateId"] = id
        state = state.copy(selectedTemplateId = id)
    }

    fun beginTemplateDetails() {
        if (state.busy) return
        val template = state.selectedTemplate
        setDraft(TemplateDraft("details", template?.id, title = template?.title.orEmpty(),
            description = template?.description.orEmpty()))
    }
    fun beginTemplateExercise() { if (!state.busy) setDraft(TemplateDraft("add", state.selectedTemplateId)) }
    fun beginTemplateRest(row: TemplateEditorDetail.ExerciseRow) {
        if (!state.busy) setDraft(TemplateDraft("rest", state.selectedTemplateId, row.id, rest = row.restSeconds.toString()))
    }
    fun updateTemplateDraft(title: String? = null, description: String? = null, rest: String? = null, search: String? = null) {
        val draft = templateDraft ?: return
        setDraft(draft.copy(title = title ?: draft.title, description = description ?: draft.description,
            rest = rest ?: draft.rest, search = search ?: draft.search))
    }
    fun closeTemplateDraft() = setDraft(null)
    private fun setDraft(draft: TemplateDraft?) {
        templateDraft = draft
        savedState["draftKind"] = draft?.kind
        savedState["draftTemplateId"] = draft?.templateId
        savedState["draftRowId"] = draft?.rowId
        savedState["draftTitle"] = draft?.title
        savedState["draftDescription"] = draft?.description
        savedState["draftRest"] = draft?.rest
        savedState["draftSearch"] = draft?.search
    }

    fun saveExercise(id: Long?, title: String, description: String, muscle: MuscleGroup, secondary: List<MuscleGroup>,
        unilateral: Boolean? = null, trackingType: com.example.hevygroovy.entity.enums.TrackingType? = null) {
        if (title.isBlank()) { state = state.copy(error = "Enter an exercise name."); return }
        if (id == null) {
            val request = CreateExerciseRequest().apply {
                this.title = title; this.description = description; primaryMuscleGroup = muscle
                secondaryMuscleGroups = secondary.toList()
                isUnilateral = unilateral ?: false
                this.trackingType = trackingType ?: com.example.hevygroovy.entity.enums.TrackingType.WEIGHT_REPS
            }
            execute(exerciseMutation = true) { gateway.createCatalogExercise(request, it) }
        } else {
            val request = UpdateExerciseRequest().apply {
                this.title = title; this.description = description; primaryMuscleGroup = muscle
                secondaryMuscleGroups = secondary.toList()
                if (unilateral != null) setUnilateral(unilateral)
                if (trackingType != null) setTrackingType(trackingType)
            }
            execute(exerciseMutation = true) { gateway.updateCatalogExercise(id, request, it) }
        }
    }

    fun archiveExercise(id: Long, archived: Boolean) {
        val exercise = state.exercises.find { it.id == id } ?: return
        if (exercise.isArchived == archived) return
        execute(exerciseMutation = true) { gateway.setExerciseArchived(id, archived, it) }
    }

    fun saveTemplate(id: Long?, title: String, description: String) {
        if (title.isBlank()) { state = state.copy(error = "Enter a template name."); return }
        if (id == null) {
            val request = CreateTemplateRequest().apply { this.title = title; this.description = description }
            execute { gateway.createTemplate(request, it) }
        } else {
            if (state.templates.none { it.id == id && !it.isArchived }) return
            val request = UpdateTemplateRequest().apply { this.title = title; this.description = description }
            execute { gateway.updateTemplate(id, request, it) }
        }
    }

    fun archiveTemplate(archived: Boolean) {
        val template = state.selectedTemplate ?: return
        if (template.isArchived == archived) return
        execute { gateway.setTemplateArchived(template.id, archived, it) }
    }

    fun addExercise(exerciseId: Long, restSeconds: Int) {
        val template = editableTemplate() ?: return
        if (restSeconds < 0 || state.exercises.none { it.id == exerciseId && !it.isArchived }) return
        execute { gateway.addTemplateExercise(template.id, exerciseId, restSeconds, it) }
    }

    fun removeExercise(rowId: Long) {
        val template = editableTemplate() ?: return
        if (template.exercises.none { it.id == rowId }) return
        execute { gateway.removeTemplateExercise(template.id, rowId, it) }
    }

    fun moveExercise(rowId: Long, position: Int) {
        val template = editableTemplate() ?: return
        if (position !in template.exercises.indices || template.exercises.none { it.id == rowId }) return
        execute { gateway.moveTemplateExercise(template.id, rowId, position, it) }
    }

    fun updateRest(rowId: Long, seconds: Int) {
        val template = editableTemplate() ?: return
        if (seconds < 0 || template.exercises.none { it.id == rowId }) return
        execute { gateway.updateTemplateRest(template.id, rowId, seconds, it) }
    }

    private fun editableTemplate(): TemplateEditorDetail? = state.selectedTemplate?.takeUnless { it.isArchived }

    private fun execute(mutation: Boolean = true, traceName: String = if (mutation) "library_update" else "library_load",
        exerciseMutation: Boolean = false,
        operation: (LibraryGateway.LibraryCallback) -> Unit) {
        if (cleared || state.loading || (mutation && state.refreshRequired)) return
        val navigation = navigationVersion
        performanceTiming.begin(traceName)
        state = state.copy(loading = true, error = null)
        operation(object : LibraryGateway.LibraryCallback {
            override fun onSuccess(snapshot: LibraryGateway.LibrarySnapshot) {
                if (cleared) return
                val selected = if (navigation == navigationVersion) snapshot.selectedTemplate else null
                selectTemplate(selected?.id)
                if (mutation) closeTemplateDraft()
                state = LibraryState(loaded = true, exercises = snapshot.exercises.toList(),
                    templates = snapshot.templates.toList(), selectedTemplate = selected,
                    lastCompletedAtByTemplate = snapshot.lastCompletedAtByTemplate.toMap(),
                    templateHistory = if (selected == null) emptyList() else snapshot.templateHistory.toList(),
                    selectedTemplateId = selected?.id, savedChanges = state.savedChanges + if (mutation) 1 else 0,
                    exerciseChanges = state.exerciseChanges + if (exerciseMutation) 1 else 0)
                performanceTiming.ready()
            }
            override fun onFailure(error: Exception) {
                if (cleared) return
                performanceTiming.cancel()
                val committed = mutation && error is CommittedRefreshException
                if (committed) {
                    selectTemplate(if (navigation == navigationVersion) (error as CommittedRefreshException).selectedTemplateId else null)
                    closeTemplateDraft()
                }
                state = state.copy(loading = false, refreshRequired = state.refreshRequired || committed,
                    savedChanges = state.savedChanges + if (committed) 1 else 0,
                    exerciseChanges = state.exerciseChanges + if (committed && exerciseMutation) 1 else 0,
                    error = if (state.refreshRequired || committed) "Saved. Reload the library before making more changes."
                        else if (mutation) "Couldn't save changes. Your draft is still here. Please try again."
                        else "Couldn't load the library. Please try again.")
            }
        })
    }

    override fun onCleared() { cleared = true; performanceTiming.cancel() }

    class Factory(private val gateway: LibraryGateway,
        private val timing: UiPerformanceTiming = UiPerformanceTiming()) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == LibraryViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(gateway, extras.createSavedStateHandle(), timing) as T
        }
    }
}
