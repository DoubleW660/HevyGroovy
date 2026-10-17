package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Library caching and screen projections, separate from application dependency wiring. */
final class ServiceLibraryGateway implements LibraryGateway {
    private final AppWorker worker;
    private List<CatalogExercise> catalogCache;
    private List<TemplateCard> templateCache;

    ServiceLibraryGateway(AppWorker worker) { this.worker = worker; }

    private void submitLibrary(Supplier<Long> mutation, LibraryCallback callback) {
        submitLibrary(mutation, callback, false, false);
    }

    private void submitLibrary(Supplier<Long> mutation, LibraryCallback callback, boolean catalog, boolean templates) {
        submitLibrary(mutation, null, callback, catalog, templates);
    }

    private void submitLibrary(Supplier<Long> mutation, Long selectedId, LibraryCallback callback,
                               boolean refreshCatalog, boolean refreshTemplates) {
        worker.submit(() -> {
            Long targetId = mutation == null ? selectedId : mutation.get();
            if (refreshCatalog) catalogCache = null;
            if (refreshTemplates) templateCache = null;
            try {
                if (catalogCache == null) catalogCache = worker.services().getExercises().listExercises(null, true).stream()
                        .map(CatalogExercise::new).collect(Collectors.toUnmodifiableList());
                if (templateCache == null) templateCache = worker.services().getTemplates().listTemplates(worker.userId(), true).stream()
                        .map(TemplateCard::new).collect(Collectors.toUnmodifiableList());
                return new LibrarySnapshot(catalogCache, templateCache,
                        targetId == null ? null : new TemplateEditorDetail(worker.services().getTemplates()
                                .getTemplateDetail(worker.userId(), targetId)),
                        worker.services().getHistory().getLastCompletedAtByTemplate(worker.userId()),
                        targetId == null ? List.of() : worker.services().getHistory()
                                .getTemplateWorkoutHistory(worker.userId(), targetId, 12));
            } catch (Exception error) {
                if (mutation != null) throw new CommittedRefreshException(error, targetId);
                throw error;
            }
        }, callback::onSuccess, callback::onFailure);
    }

    @Override
    public void loadTemplate(long templateId, LibraryCallback callback) {
        submitLibrary(null, templateId, callback, false, false);
    }

    @Override
    public void loadLibrary(Long templateId, LibraryCallback callback) { submitLibrary(null, templateId, callback, true, true); }

    @Override
    public void createCatalogExercise(CreateExerciseRequest request, LibraryCallback callback) {
        submitLibrary(() -> { worker.services().getExercises().createExercise(request); return null; }, callback, true, false);
    }

    @Override
    public void updateCatalogExercise(long id, UpdateExerciseRequest request, LibraryCallback callback) {
        submitLibrary(() -> { worker.services().getExercises().updateExercise(id, request); return null; }, callback, true, false);
    }

    @Override
    public void setExerciseArchived(long id, boolean archived, LibraryCallback callback) {
        submitLibrary(() -> {
            if (archived) worker.services().getExercises().archiveExercise(id); else worker.services().getExercises().restoreExercise(id);
            return null;
        }, callback, true, false);
    }

    @Override
    public void createTemplate(CreateTemplateRequest request, LibraryCallback callback) {
        submitLibrary(() -> worker.services().getTemplates().createTemplate(worker.userId(), request).getTemplateWorkoutId(), callback, false, true);
    }

    @Override
    public void updateTemplate(long id, UpdateTemplateRequest request, LibraryCallback callback) {
        submitLibrary(() -> { worker.services().getTemplates().updateTemplate(worker.userId(), id, request); return id; }, callback, false, true);
    }

    @Override
    public void setTemplateArchived(long id, boolean archived, LibraryCallback callback) {
        submitLibrary(() -> {
            if (archived) worker.services().getTemplates().archiveTemplate(worker.userId(), id); else worker.services().getTemplates().restoreTemplate(worker.userId(), id);
            return id;
        }, callback, false, true);
    }

    @Override
    public void addTemplateExercise(long templateId, long exerciseId, int restSeconds, LibraryCallback callback) {
        submitLibrary(() -> {
            AddTemplateExerciseRequest request = new AddTemplateExerciseRequest();
            request.setTemplateId(templateId); request.setExerciseId(exerciseId); request.setRestTimeSeconds(restSeconds);
            worker.services().getTemplates().addExerciseToTemplate(worker.userId(), request); return templateId;
        }, callback);
    }

    @Override
    public void removeTemplateExercise(long templateId, long rowId, LibraryCallback callback) {
        submitLibrary(() -> { worker.services().getTemplates().removeExerciseFromTemplate(worker.userId(), rowId, templateId); return templateId; }, callback);
    }

    @Override
    public void moveTemplateExercise(long templateId, long rowId, int position, LibraryCallback callback) {
        submitLibrary(() -> { worker.services().getTemplates().moveExerciseInTemplate(worker.userId(), rowId, position); return templateId; }, callback);
    }

    @Override
    public void updateTemplateRest(long templateId, long rowId, int restSeconds, LibraryCallback callback) {
        submitLibrary(() -> { worker.services().getTemplates().updateExerciseRestTime(worker.userId(), rowId, restSeconds); return templateId; }, callback);
    }

}
