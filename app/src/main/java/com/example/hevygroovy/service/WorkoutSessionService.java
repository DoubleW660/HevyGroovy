package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;

public interface WorkoutSessionService {

    LoggedWorkout startEmptyWorkout(long userID);

    LoggedWorkout startWorkoutFromTemplate(long userId, long templateId);

    LoggedWorkout finishWorkout(long userId, long workoutId);

    WorkoutExercise addExerciseToWorkout(long userId, long workoutId, long exerciseId);

    void removeExerciseFromWorkout(long userId, long workoutExerciseId);

    void moveExerciseInWorkout(long userId, long workoutExerciseId, int orderIndex);

    /** Null or blank notes clear the current note. */
    WorkoutExercise updateExerciseNotes(long userId, long workoutExerciseId, String notes);

    WorkoutExercise updateExerciseRestTime(long userId, long workoutExerciseId, int restTimeSeconds);
}
