package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;

import java.util.List;

public interface WorkoutHistoryQueryService {
        List<WorkoutHistory> getWorkoutHistory(long userId);

        WorkoutHistoryDetail getWorkoutHistoryDetail(
                long userId,
                long workoutId
        );
}

