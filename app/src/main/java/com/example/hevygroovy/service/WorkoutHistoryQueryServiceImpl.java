package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.util.List;

public class WorkoutHistoryQueryServiceImpl implements WorkoutHistoryQueryService{
    private final LoggedWorkoutRepository loggedWorkoutRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final SetEntryRepository setEntryRepository;

    public WorkoutHistoryQueryServiceImpl(
            LoggedWorkoutRepository loggedWorkoutRepository,
            WorkoutExerciseRepository workoutExerciseRepository,
            SetEntryRepository setEntryRepository
    ){
        this.loggedWorkoutRepository = loggedWorkoutRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.setEntryRepository = setEntryRepository;
    }

    @Override
    public List<WorkoutHistory> getWorkoutHistory(long userId) {
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }

        List<LoggedWorkout> workouts =
                loggedWorkoutRepository.findByUserId(userId);

        for (LoggedWorkout workout : workouts) {

            if (workout.getEndedAtEpochMillis() == null) {
                continue;
            }

            long workoutId = workout.getId();
            long startedAt = workout.getStartedAtEpochMillis();
            long endedAt = workout.getEndedAtEpochMillis();

        }
        
        return null;
    }

    @Override
    public WorkoutHistoryDetail getWorkoutHistoryDetail(
            long userId,
            long workoutId
    ) {
        return null;
    }
}
