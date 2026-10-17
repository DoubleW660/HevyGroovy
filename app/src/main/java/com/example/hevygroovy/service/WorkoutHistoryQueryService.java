package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;

import java.util.List;
import java.util.Map;

public interface WorkoutHistoryQueryService {
        List<WorkoutHistory> getWorkoutHistory(long userId);

        /** Completed workouts only. A zero limit returns an empty page. */
        List<WorkoutHistory> getWorkoutHistory(long userId, int limit, int offset);

        Map<Long, Long> getLastCompletedAtByTemplate(long userId);

        List<WorkoutHistory> getTemplateWorkoutHistory(long userId, long templateId, int limit);

        WorkoutHistoryDetail getWorkoutHistoryDetail(
                long userId,
                long workoutId
        );
}

