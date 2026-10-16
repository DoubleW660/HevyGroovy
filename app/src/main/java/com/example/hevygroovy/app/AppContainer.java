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
public final class AppContainer implements WorkoutGateway, AutoCloseable {
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Executor main;
    // Only read or written by the worker. Storage stays open for the process lifetime.
    private SQLiteAppStorage storage;
    private long userId;
    private final String databaseName;
    private List<WorkoutHistory> history = List.of();
    private List<ExerciseOption> exercises = List.of();

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
                if (storage == null) {
                    userId = new LocalUserIdentity(context).getUserId();
                    storage = new SQLiteAppStorage(context, databaseName);
                }
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
