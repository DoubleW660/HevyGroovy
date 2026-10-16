package com.example.hevygroovy.app;

import android.content.Context;
import com.example.hevygroovy.persistence.SQLiteAppStorage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import java.util.List;
import java.util.stream.Collectors;

/** Application-owned dependencies. All database access stays on one background worker. */
public final class AppContainer implements WorkoutGateway, LibraryGateway, AutoCloseable {
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Executor main;
    // Only read or written by the worker. Storage stays open for the process lifetime.
    private SQLiteAppStorage storage;
    private long userId;
    private final String databaseName;
    private List<WorkoutHistory> history = List.of();
    private List<ExerciseOption> exercises = List.of();
    private List<CatalogExercise> catalogCache;
    private List<TemplateCard> templateCache;

    public AppContainer(Context context) {
        this(context, "hevy_groovy.db");
    }

    public AppContainer(Context context, String databaseName) {
        this.context = context.getApplicationContext();
        this.databaseName = databaseName;
        main = this.context.getMainExecutor();
    }

    private void submit(Runnable mutation, Callback callback) {
        submit(mutation, callback, false, false);
    }

    private void submit(Runnable mutation, Callback callback, boolean refreshHistory, boolean refreshExercises) {
        worker.execute(() -> {
            try {
                boolean initializing = storage == null;
                ensureStorage();
                mutation.run();
                if (initializing || refreshHistory) {
                    history = storage.getHistory().getWorkoutHistory(userId, 20, 0);
                }
                if (initializing || refreshExercises) {
                    exercises = storage.getExercises().listExercises(null, false).stream()
                            .map(ExerciseOption::new).collect(Collectors.toUnmodifiableList());
                }
                Snapshot snapshot = new Snapshot(storage.getWorkoutSessions().getActiveWorkout(userId).orElse(null),
                        history, exercises);
                main.execute(() -> callback.onSuccess(snapshot));
            } catch (Exception error) {
                main.execute(() -> callback.onFailure(error));
            }
        });
    }

    @Override
    public void load(Callback callback) { submit(() -> { }, callback, true, true); }

    @Override
    public void startEmpty(Callback callback) {
        submit(() -> storage.getWorkoutSessions().startEmptyWorkout(userId), callback);
    }

    @Override
    public void startFromTemplate(long templateId, Callback callback) {
        submit(() -> storage.getWorkoutSessions().startWorkoutFromTemplate(userId, templateId), callback);
    }

    private void ensureStorage() {
        if (storage == null) {
            userId = new LocalUserIdentity(context).getUserId();
            storage = new SQLiteAppStorage(context, databaseName);
        }
    }

    private void submitLibrary(java.util.function.Supplier<Long> operation, LibraryCallback callback) {
        submitLibrary(operation, callback, false, false);
    }

    private void submitLibrary(java.util.function.Supplier<Long> operation, LibraryCallback callback,
                               boolean refreshCatalog, boolean refreshTemplates) {
        worker.execute(() -> {
            try {
                ensureStorage();
                Long selectedId = operation.get();
                if (catalogCache == null || refreshCatalog) {
                    java.util.List<com.example.hevygroovy.model.ExerciseModel> catalog = storage.getExercises().listExercises(null, true);
                    exercises = catalog.stream().filter(exercise -> !exercise.isArchived()).map(ExerciseOption::new)
                            .collect(Collectors.toUnmodifiableList());
                    catalogCache = catalog.stream().map(CatalogExercise::new).collect(Collectors.toUnmodifiableList());
                }
                if (templateCache == null || refreshTemplates) {
                    templateCache = storage.getTemplates().listTemplates(userId, true).stream().map(TemplateCard::new)
                            .collect(Collectors.toUnmodifiableList());
                }
                LibrarySnapshot snapshot = new LibrarySnapshot(catalogCache, templateCache,
                        selectedId == null ? null : new TemplateEditorDetail(storage.getTemplates().getTemplateDetail(userId, selectedId)));
                main.execute(() -> callback.onSuccess(snapshot));
            } catch (Exception error) {
                main.execute(() -> callback.onFailure(error));
            }
        });
    }

    @Override
    public void loadLibrary(Long templateId, LibraryCallback callback) { submitLibrary(() -> templateId, callback, true, true); }

    @Override
    public void createCatalogExercise(CreateExerciseRequest request, LibraryCallback callback) {
        submitLibrary(() -> { storage.getExercises().createExercise(request); return null; }, callback, true, false);
    }

    @Override
    public void updateCatalogExercise(long id, UpdateExerciseRequest request, LibraryCallback callback) {
        submitLibrary(() -> { storage.getExercises().updateExercise(id, request); return null; }, callback, true, false);
    }

    @Override
    public void setExerciseArchived(long id, boolean archived, LibraryCallback callback) {
        submitLibrary(() -> {
            if (archived) storage.getExercises().archiveExercise(id); else storage.getExercises().restoreExercise(id);
            return null;
        }, callback, true, false);
    }

    @Override
    public void createTemplate(CreateTemplateRequest request, LibraryCallback callback) {
        submitLibrary(() -> storage.getTemplates().createTemplate(userId, request).getTemplateWorkoutId(), callback, false, true);
    }

    @Override
    public void updateTemplate(long id, UpdateTemplateRequest request, LibraryCallback callback) {
        submitLibrary(() -> { storage.getTemplates().updateTemplate(userId, id, request); return id; }, callback, false, true);
    }

    @Override
    public void setTemplateArchived(long id, boolean archived, LibraryCallback callback) {
        submitLibrary(() -> {
            if (archived) storage.getTemplates().archiveTemplate(userId, id); else storage.getTemplates().restoreTemplate(userId, id);
            return id;
        }, callback, false, true);
    }

    @Override
    public void addTemplateExercise(long templateId, long exerciseId, int restSeconds, LibraryCallback callback) {
        submitLibrary(() -> {
            AddTemplateExerciseRequest request = new AddTemplateExerciseRequest();
            request.setTemplateId(templateId); request.setExerciseId(exerciseId); request.setRestTimeSeconds(restSeconds);
            storage.getTemplates().addExerciseToTemplate(userId, request); return templateId;
        }, callback);
    }

    @Override
    public void removeTemplateExercise(long templateId, long rowId, LibraryCallback callback) {
        submitLibrary(() -> { storage.getTemplates().removeExerciseFromTemplate(userId, rowId, templateId); return templateId; }, callback);
    }

    @Override
    public void moveTemplateExercise(long templateId, long rowId, int position, LibraryCallback callback) {
        submitLibrary(() -> { storage.getTemplates().moveExerciseInTemplate(userId, rowId, position); return templateId; }, callback);
    }

    @Override
    public void updateTemplateRest(long templateId, long rowId, int restSeconds, LibraryCallback callback) {
        submitLibrary(() -> { storage.getTemplates().updateExerciseRestTime(userId, rowId, restSeconds); return templateId; }, callback);
    }

    @Override
    public void discard(long workoutId, Callback callback) {
        submit(() -> storage.getWorkoutSessions().discardWorkout(userId, workoutId), callback);
    }

    @Override
    public void finish(long workoutId, Callback callback) {
        submit(() -> storage.getWorkoutSessions().finishWorkout(userId, workoutId), callback, true, false);
    }

    @Override
    public void updateWorkout(long workoutId, String name, String notes, Callback callback) {
        submit(() -> storage.getWorkoutSessions().updateWorkoutDetails(userId, workoutId, name, notes), callback);
    }

    @Override
    public void createExercise(String title, MuscleGroup primaryMuscle, Callback callback) {
        submit(() -> {
            CreateExerciseRequest request = new CreateExerciseRequest();
            request.setTitle(title);
            request.setPrimaryMuscleGroup(primaryMuscle);
            storage.getExercises().createExercise(request);
        }, callback, false, true);
    }

    @Override
    public void addExercise(long workoutId, long exerciseId, Callback callback) {
        submit(() -> storage.getWorkoutSessions().addExerciseToWorkout(userId, workoutId, exerciseId), callback);
    }

    @Override
    public void removeExercise(long workoutExerciseId, Callback callback) {
        submit(() -> storage.getWorkoutSessions().removeExerciseFromWorkout(userId, workoutExerciseId), callback);
    }

    @Override
    public void moveExercise(long workoutExerciseId, int position, Callback callback) {
        submit(() -> storage.getWorkoutSessions().moveExerciseInWorkout(userId, workoutExerciseId, position), callback);
    }

    @Override
    public void updateExercise(long workoutExerciseId, String notes, int restSeconds, Callback callback) {
        submit(() -> storage.getWorkoutSessions().updateExerciseDetails(userId, workoutExerciseId, notes, restSeconds), callback);
    }

    @Override
    public void addSet(long workoutExerciseId, Callback callback) {
        submit(() -> storage.getSetEntries().addSet(userId, workoutExerciseId), callback);
    }

    @Override
    public void updateSet(long setId, UpdateSetEntryRequest request, Callback callback) {
        submit(() -> storage.getSetEntries().updateSet(userId, setId, request), callback);
    }

    @Override
    public void deleteSet(long setId, Callback callback) {
        submit(() -> storage.getSetEntries().deleteSet(userId, setId), callback);
    }

    /** For explicit shutdown outside main; normal app storage lives until process exit. */
    @Override
    public void close() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("Close storage off the main thread");
        }
        java.util.concurrent.Future<?> closing = worker.submit(() -> { if (storage != null) storage.close(); });
        worker.shutdown();
        try {
            closing.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing storage", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException("Could not close storage", error.getCause());
        }
    }
}
