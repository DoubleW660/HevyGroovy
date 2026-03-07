package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.LoggedWorkout;

public interface WorkoutSessionService {

    LoggedWorkout startEmptyWorkout(long userID);

    LoggedWorkout startWorkoutFromTemplate(long userId, long templateId);

    LoggedWorkout finishWorkout(long userId, long workoutId);
}
