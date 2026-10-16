package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;

import java.util.List;

public interface WorkoutHistoryQueryService {
        List<WorkoutHistory> getWorkoutHistory(long userId);

        /** Completed workouts only. A zero limit returns an empty page. */
        List<WorkoutHistory> getWorkoutHistory(long userId, int limit, int offset);

        WorkoutHistoryDetail getWorkoutHistoryDetail(
                long userId,
                long workoutId
        );
}

