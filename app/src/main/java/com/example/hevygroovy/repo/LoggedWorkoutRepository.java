package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.LoggedWorkout;

import java.util.List;
import java.util.Optional;



public interface LoggedWorkoutRepository {

    LoggedWorkout save(LoggedWorkout workout);

    Optional<LoggedWorkout> findById(long id);

    List<LoggedWorkout> findByUserIdOrderByStartedAtDesc(long userId, int limit, int offset);

    Optional<LoggedWorkout> findActiveWorkoutByUserId(long userId);


}
