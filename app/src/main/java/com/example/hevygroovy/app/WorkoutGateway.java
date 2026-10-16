package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.ExerciseOption;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import java.util.List;

/** Async boundary between the UI and synchronous Java services. Callbacks run on main. */
public interface WorkoutGateway {
    interface Callback {
        void onSuccess(Snapshot snapshot);
        /** CommittedRefreshException means the write succeeded and only a reload should be retried. */
        void onFailure(Exception error);
    }

    final class Snapshot {
        private final ActiveWorkoutDetail activeWorkout;
        private final List<WorkoutHistory> history;
        private final List<ExerciseOption> exercises;

        public Snapshot(ActiveWorkoutDetail activeWorkout, List<WorkoutHistory> history) {
            this(activeWorkout, history, List.of());
        }

        public Snapshot(ActiveWorkoutDetail activeWorkout, List<WorkoutHistory> history, List<ExerciseOption> exercises) {
            this.activeWorkout = activeWorkout;
            this.history = List.copyOf(history);
            this.exercises = List.copyOf(exercises);
        }

        public ActiveWorkoutDetail getActiveWorkout() { return activeWorkout; }
        public List<WorkoutHistory> getHistory() { return history; }
        public List<ExerciseOption> getExercises() { return exercises; }
    }

    void load(Callback callback);
    void startEmpty(Callback callback);
    void startFromTemplate(long templateId, Callback callback);
    void discard(long workoutId, Callback callback);
    void finish(long workoutId, Callback callback);
    void updateWorkout(long workoutId, String name, String notes, Callback callback);
    void createExercise(String title, MuscleGroup primaryMuscle, Callback callback);
    void addExercise(long workoutId, long exerciseId, Callback callback);
    void removeExercise(long workoutExerciseId, Callback callback);
    void moveExercise(long workoutExerciseId, int position, Callback callback);
    void updateExercise(long workoutExerciseId, String notes, int restSeconds, Callback callback);
    void addSet(long workoutExerciseId, Callback callback);
    void updateSet(long setId, UpdateSetEntryRequest request, Callback callback);
    void deleteSet(long setId, Callback callback);
}
