package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import java.util.Optional;

public interface WorkoutSessionService {
    Optional<ActiveWorkoutDetail> getActiveWorkout(long userId);

    LoggedWorkout startEmptyWorkout(long userID);

    LoggedWorkout startWorkoutFromTemplate(long userId, long templateId);

    LoggedWorkout finishWorkout(long userId, long workoutId);

    /** Name must be nonblank; surrounding and repeated whitespace is normalized. */
    LoggedWorkout renameWorkout(long userId, long workoutId, String name);

    /** Null or blank notes clear the current note. */
    LoggedWorkout updateWorkoutNotes(long userId, long workoutId, String notes);

    LoggedWorkout updateWorkoutDetails(long userId, long workoutId, String name, String notes);

    WorkoutExercise updateExerciseDetails(long userId, long workoutExerciseId, String notes, int restSeconds);

    /** Permanently removes an owned active workout and its child rows. */
    void discardWorkout(long userId, long workoutId);

    /** Permanently removes an owned finished workout and its children. */
    void deleteFinishedWorkout(long userId, long workoutId);

    WorkoutExercise addExerciseToWorkout(long userId, long workoutId, long exerciseId);

    void removeExerciseFromWorkout(long userId, long workoutExerciseId);

    void moveExerciseInWorkout(long userId, long workoutExerciseId, int orderIndex);

    /** Null or blank notes clear the current note. */
    WorkoutExercise updateExerciseNotes(long userId, long workoutExerciseId, String notes);

    WorkoutExercise updateExerciseRestTime(long userId, long workoutExerciseId, int restTimeSeconds);
}
