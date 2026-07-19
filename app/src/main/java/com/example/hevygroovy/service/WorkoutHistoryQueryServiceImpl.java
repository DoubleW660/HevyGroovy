package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.time.Duration;
import java.util.ArrayList;
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
            throw new IllegalArgumentException("Invalid user ID");
        }

        List<LoggedWorkout> workouts =
                loggedWorkoutRepository.findByUserId(userId);

        List<WorkoutHistory> workoutHistoryList = new ArrayList<>();

        for (LoggedWorkout workout : workouts) {
            if (workout.getEndedAtEpochMillis() == null) {
                continue;
            }

            long workoutId = workout.getId();

            List<WorkoutExercise> workoutExercises =
                    workoutExerciseRepository.findByLoggedWorkoutId(workoutId);

            List<SetEntry> setEntries =
                    setEntryRepository.findByLoggedWorkoutId(workoutId);

            int totalReps = 0;
            double totalVolume = 0.0;

            for (SetEntry setEntry : setEntries) {
                totalReps += setEntry.getReps();
                totalVolume += setEntry.getWeight() * setEntry.getReps();
            }

            WorkoutHistory dto = new WorkoutHistory(
                    workoutId,
                    workout.getNameSnapshot(),
                    workout.getStartedAtEpochMillis(),
                    workout.getEndedAtEpochMillis(),
                    Duration.ofMillis(
                            workout.getEndedAtEpochMillis()
                                    - workout.getStartedAtEpochMillis()
                    ),
                    workoutExercises.size(),
                    setEntries.size(),
                    totalReps,
                    totalVolume
            );

            workoutHistoryList.add(dto);
        }

        return workoutHistoryList;
    }

    @Override
    public WorkoutHistoryDetail getWorkoutHistoryDetail(
            long userId,
            long workoutId
    ) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }

        if (workoutId <= 0) {
            throw new IllegalArgumentException("Invalid workout ID");
        }


        return null;
    }


}

