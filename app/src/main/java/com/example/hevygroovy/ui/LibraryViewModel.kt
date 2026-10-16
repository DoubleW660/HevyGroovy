package com.example.hevygroovy.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.app.LibraryGateway
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup

data class LibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val exercises: List<CatalogExercise> = emptyList(),
    val templates: List<TemplateCard> = emptyList(),
    val selectedTemplate: TemplateEditorDetail? = null,
    val error: String? = null,
)

class LibraryViewModel(private val gateway: LibraryGateway) : ViewModel() {
    var state by mutableStateOf(LibraryState())
        private set
    private var cleared = false

    fun refresh() = execute { gateway.loadLibrary(state.selectedTemplate?.id, it) }
    fun showTemplateList() { if (!state.loading) state = state.copy(selectedTemplate = null) }
    fun openTemplate(id: Long) {
        if (state.templates.none { it.id == id }) return
        execute { gateway.loadLibrary(id, it) }
    }

    fun saveExercise(id: Long?, title: String, description: String, muscle: MuscleGroup, secondary: List<MuscleGroup>) {
        if (title.isBlank()) { state = state.copy(error = "Enter an exercise name."); return }
        if (id == null) {
            val request = CreateExerciseRequest().apply {
                this.title = title; this.description = description; primaryMuscleGroup = muscle
                secondaryMuscleGroups = secondary.toList()
            }
            execute { gateway.createCatalogExercise(request, it) }
        } else {
            if (state.exercises.none { it.id == id }) return
            val request = UpdateExerciseRequest().apply {
                this.title = title; this.description = description; primaryMuscleGroup = muscle
                secondaryMuscleGroups = secondary.toList()
            }
            execute { gateway.updateCatalogExercise(id, request, it) }
        }
    }

    fun archiveExercise(id: Long, archived: Boolean) {
        val exercise = state.exercises.find { it.id == id } ?: return
        if (exercise.isArchived == archived) return
        execute { gateway.setExerciseArchived(id, archived, it) }
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

    private fun execute(operation: (LibraryGateway.LibraryCallback) -> Unit) {
        if (cleared || state.loading) return
        state = state.copy(loading = true, error = null)
        operation(object : LibraryGateway.LibraryCallback {
            override fun onSuccess(snapshot: LibraryGateway.LibrarySnapshot) {
                if (cleared) return
                state = LibraryState(loaded = true, exercises = snapshot.exercises.toList(),
                    templates = snapshot.templates.toList(), selectedTemplate = snapshot.selectedTemplate)
            }
            override fun onFailure(error: Exception) {
                if (cleared) return
                state = state.copy(loading = false, error = "Couldn't update the library. Please try again.")
            }
        })
    }

    override fun onCleared() { cleared = true }

    class Factory(private val gateway: LibraryGateway) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == LibraryViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(gateway) as T
        }
    }
}
