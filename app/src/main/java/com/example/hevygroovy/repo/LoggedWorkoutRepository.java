package com.example.hevygroovy.repo;

import com.example.hevygroovy.model.LoggedWorkout;

import java.util.List;
import java.util.Optional;

public interface LoggedWorkoutRepository {

    LoggedWorkout save(LoggedWorkout workout);

    Optional<LoggedWorkout> findById(long id);

    List<LoggedWorkout> findByUserId(long userId, int limit, int offset);

    Optional<LoggedWorkout> findActiveWorkout(long userId);

    void setEndedAt(long workoutId, long endedAtEpochMillis);

    boolean existsById(long id);
}
