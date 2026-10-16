package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import java.util.List;
import java.util.stream.Collectors;

/** Maps synchronous workout services to immutable screen snapshots on one worker. */
final class ServiceWorkoutGateway implements WorkoutGateway {
    private final AppWorker worker;
    private List<WorkoutHistory> history;
    private List<ExerciseOption> exercises;

    ServiceWorkoutGateway(AppWorker worker) { this.worker = worker; }

    private void submit(Runnable mutation, Callback callback) {
        submit(mutation, callback, false, false);
    }

    private void submit(Runnable mutation, Callback callback, boolean refreshHistory, boolean refreshExercises) {
        worker.submit(() -> {
            if (mutation != null) mutation.run();
            // Invalidate before reading: a failed refresh must not make stale caches look current.
            if (refreshHistory) history = null;
            if (refreshExercises) exercises = null;
            try {
                if (history == null) history = worker.services().getHistory().getWorkoutHistory(worker.userId(), 20, 0);
                if (exercises == null) exercises = worker.services().getExercises().listExercises(null, false).stream()
                        .map(ExerciseOption::new).collect(Collectors.toUnmodifiableList());
                return new Snapshot(worker.services().getWorkoutSessions().getActiveWorkout(worker.userId()).orElse(null),
                        history, exercises);
            } catch (Exception error) {
                if (mutation != null) throw new CommittedRefreshException(error, null);
                throw error;
            }
        }, callback::onSuccess, callback::onFailure);
    }

    @Override
    public void load(Callback callback) { submit(null, callback, true, true); }

    @Override
    public void startEmpty(Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().startEmptyWorkout(worker.userId()), callback);
    }

    @Override
    public void startFromTemplate(long templateId, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().startWorkoutFromTemplate(worker.userId(), templateId), callback);
    }

    @Override
    public void discard(long workoutId, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().discardWorkout(worker.userId(), workoutId), callback);
    }

    @Override
    public void finish(long workoutId, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().finishWorkout(worker.userId(), workoutId), callback, true, false);
    }

    @Override
    public void updateWorkout(long workoutId, String name, String notes, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().updateWorkoutDetails(worker.userId(), workoutId, name, notes), callback);
    }

    @Override
    public void createExercise(String title, MuscleGroup primaryMuscle, Callback callback) {
        submit(() -> {
            CreateExerciseRequest request = new CreateExerciseRequest();
            request.setTitle(title);
            request.setPrimaryMuscleGroup(primaryMuscle);
            worker.services().getExercises().createExercise(request);
        }, callback, false, true);
    }

    @Override
    public void addExercise(long workoutId, long exerciseId, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().addExerciseToWorkout(worker.userId(), workoutId, exerciseId), callback);
    }

    @Override
    public void removeExercise(long workoutExerciseId, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().removeExerciseFromWorkout(worker.userId(), workoutExerciseId), callback);
    }

    @Override
    public void moveExercise(long workoutExerciseId, int position, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().moveExerciseInWorkout(worker.userId(), workoutExerciseId, position), callback);
    }

    @Override
    public void updateExercise(long workoutExerciseId, String notes, int restSeconds, Callback callback) {
        submit(() -> worker.services().getWorkoutSessions().updateExerciseDetails(worker.userId(), workoutExerciseId, notes, restSeconds), callback);
    }

    @Override
    public void addSet(long workoutExerciseId, Callback callback) {
        submit(() -> worker.services().getSetEntries().addSet(worker.userId(), workoutExerciseId), callback);
    }

    @Override
    public void updateSet(long setId, UpdateSetEntryRequest request, Callback callback) {
        submit(() -> worker.services().getSetEntries().updateSet(worker.userId(), setId, request), callback);
    }

    @Override
    public void deleteSet(long setId, Callback callback) {
        submit(() -> worker.services().getSetEntries().deleteSet(worker.userId(), setId), callback);
    }

}
