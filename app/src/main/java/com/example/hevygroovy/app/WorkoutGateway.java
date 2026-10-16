package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import com.example.hevygroovy.dto.WorkoutHistory;
import java.util.List;

/** Async boundary between the UI and synchronous Java services. Callbacks run on main. */
public interface WorkoutGateway {
    interface Callback {
        void onSuccess(Snapshot snapshot);
        void onFailure(Exception error);
    }

    final class Snapshot {
        private final ActiveWorkoutDetail activeWorkout;
        private final List<WorkoutHistory> history;

        public Snapshot(ActiveWorkoutDetail activeWorkout, List<WorkoutHistory> history) {
            this.activeWorkout = activeWorkout;
            this.history = List.copyOf(history);
        }

        public ActiveWorkoutDetail getActiveWorkout() { return activeWorkout; }
        public List<WorkoutHistory> getHistory() { return history; }
    }

    void load(Callback callback);
    void startEmpty(Callback callback);
    void discard(long workoutId, Callback callback);
}
