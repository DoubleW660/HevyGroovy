package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.*;
import java.util.List;

/** Async library operations; immutable results are delivered on the main thread. */
public interface LibraryGateway {
    interface LibraryCallback {
        void onSuccess(LibrarySnapshot snapshot);
        void onFailure(Exception error);
    }
    final class LibrarySnapshot {
        private final List<CatalogExercise> exercises;
        private final List<TemplateCard> templates;
        private final TemplateEditorDetail selectedTemplate;
        public LibrarySnapshot(List<CatalogExercise> exercises, List<TemplateCard> templates, TemplateEditorDetail selectedTemplate) {
            this.exercises = List.copyOf(exercises); this.templates = List.copyOf(templates);
            this.selectedTemplate = selectedTemplate;
        }
        public List<CatalogExercise> getExercises() { return exercises; }
        public List<TemplateCard> getTemplates() { return templates; }
        public TemplateEditorDetail getSelectedTemplate() { return selectedTemplate; }
    }
    void loadLibrary(Long templateId, LibraryCallback callback);
    void createCatalogExercise(CreateExerciseRequest request, LibraryCallback callback);
    void updateCatalogExercise(long id, UpdateExerciseRequest request, LibraryCallback callback);
    void setExerciseArchived(long id, boolean archived, LibraryCallback callback);
    void createTemplate(CreateTemplateRequest request, LibraryCallback callback);
    void updateTemplate(long id, UpdateTemplateRequest request, LibraryCallback callback);
    void setTemplateArchived(long id, boolean archived, LibraryCallback callback);
    void addTemplateExercise(long templateId, long exerciseId, int restSeconds, LibraryCallback callback);
    void removeTemplateExercise(long templateId, long rowId, LibraryCallback callback);
    void moveTemplateExercise(long templateId, long rowId, int position, LibraryCallback callback);
    void updateTemplateRest(long templateId, long rowId, int restSeconds, LibraryCallback callback);
}
