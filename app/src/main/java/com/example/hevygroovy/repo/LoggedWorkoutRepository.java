package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.LoggedWorkout;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.example.hevygroovy.dto.WorkoutHistory;



public interface LoggedWorkoutRepository {
    /** One database query; completed sets only, volume in KG times reps. */
    List<WorkoutHistory> findCompletedSummaries(long userId, int limit, int offset);

    /** Most recent completion for each routine, across all of this user's finished history. */
    Map<Long, Long> findLastCompletedAtByTemplate(long userId);

    /** Bounded routine history ordered by completion time and ID descending. */
    List<WorkoutHistory> findCompletedTemplateSummaries(long userId, long templateId, int limit);

    LoggedWorkout save(LoggedWorkout workout);

    /** Delete only after dependent exercises and sets have been removed. */
    void delete(long id);

    Optional<LoggedWorkout> findById(long id);

    List<LoggedWorkout> findByUserIdOrderByStartedAtDesc(long userId, int limit, int offset);

    /** Filter completed workouts before applying pagination; order by started time then ID descending. */
    List<LoggedWorkout> findCompletedByUserIdOrderByStartedAtDesc(long userId, int limit, int offset);

    Optional<LoggedWorkout> findActiveWorkoutByUserId(long userId);

    List<LoggedWorkout> findByUserId(long userId);


}
