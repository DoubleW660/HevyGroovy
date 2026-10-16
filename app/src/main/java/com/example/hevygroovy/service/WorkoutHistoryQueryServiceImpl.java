package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;
import com.example.hevygroovy.dto.WorkoutExerciseHistory;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

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

        return buildHistory(userId, workouts);
    }

    @Override
    public List<WorkoutHistory> getWorkoutHistory(long userId, int limit, int offset) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        if (limit < 0 || offset < 0) {
            throw new IllegalArgumentException("Limit and offset cannot be negative");
        }
        if (limit == 0) {
            return List.of();
        }
        return buildHistory(userId, loggedWorkoutRepository
                .findCompletedByUserIdOrderByStartedAtDesc(userId, limit, offset));
    }

    private List<WorkoutHistory> buildHistory(long userId, List<LoggedWorkout> workouts) {
        List<WorkoutHistory> workoutHistoryList = new ArrayList<>();

        workouts = new ArrayList<>(workouts);
        workouts.sort(Comparator.comparingLong(LoggedWorkout::getStartedAtEpochMillis).reversed()
                .thenComparing(Comparator.comparingLong(LoggedWorkout::getId).reversed()));
        for (LoggedWorkout workout : workouts) {
            if (workout.getUserId() != userId) {
                continue;
            }
            if (workout.getEndedAtEpochMillis() == null) {
                continue;
            }

            long workoutId = workout.getId();

            List<WorkoutExercise> workoutExercises =
                    workoutExerciseRepository.findByLoggedWorkoutId(workoutId);

            List<SetEntry> setEntries =
                    setEntryRepository.findByLoggedWorkoutId(workoutId).stream()
                            .filter(SetEntry::isCompleted).collect(Collectors.toList());

            int totalReps = 0;
            double totalVolume = 0.0;

            for (SetEntry setEntry : setEntries) {
                totalReps += setEntry.getReps();
                totalVolume += weightInKg(setEntry) * setEntry.getReps();
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


        LoggedWorkout workout = loggedWorkoutRepository.findById(workoutId)
                .orElseThrow(() -> new RuntimeException("Workout Not Found"));
        if (workout.getUserId() != userId) {
            throw new RuntimeException("Workout does not belong to user");
        }
        if (workout.getEndedAtEpochMillis() == null) {
            throw new RuntimeException("Workout has not been completed");
        }
        List<WorkoutExercise> rows = new ArrayList<>(
                workoutExerciseRepository.findByLoggedWorkoutId(workoutId));
        rows.sort(Comparator.comparingInt(WorkoutExercise::getOrderIndex)
                .thenComparingLong(WorkoutExercise::getId));
        Map<Long, List<SetEntry>> setsByExercise = setEntryRepository.findByLoggedWorkoutId(workoutId)
                .stream().filter(SetEntry::isCompleted)
                .collect(Collectors.groupingBy(SetEntry::getWorkoutExerciseId));
        List<WorkoutExerciseHistory> details = new ArrayList<>();
        int totalSets = 0;
        int totalReps = 0;
        double totalVolume = 0;
        for (WorkoutExercise row : rows) {
            List<SetEntry> sets = new ArrayList<>(setsByExercise.getOrDefault(row.getId(), List.of()));
            sets.sort(Comparator.comparingInt(SetEntry::getSetNumber).thenComparingLong(SetEntry::getId));
            int reps = 0;
            double volume = 0;
            double heaviest = 0;
            double bestVolume = 0;
            for (SetEntry set : sets) {
                double weight = weightInKg(set);
                double setVolume = weight * set.getReps();
                reps += set.getReps();
                volume += setVolume;
                heaviest = Math.max(heaviest, weight);
                bestVolume = Math.max(bestVolume, setVolume);
            }
            // Estimated 1RM and personal-record evaluation remain deferred.
            details.add(new WorkoutExerciseHistory(row.getId(), row.getExerciseId(),
                    row.getExerciseNameSnapshot(), row.getOrderIndex(), row.getNotes(),
                    sets.size(), reps, volume, heaviest, 0, bestVolume, false, 0, sets));
            totalSets += sets.size();
            totalReps += reps;
            totalVolume += volume;
        }
        WorkoutHistory summary = new WorkoutHistory(workoutId, workout.getNameSnapshot(),
                workout.getStartedAtEpochMillis(), workout.getEndedAtEpochMillis(),
                Duration.ofMillis(workout.getEndedAtEpochMillis() - workout.getStartedAtEpochMillis()),
                rows.size(), totalSets, totalReps, totalVolume);
        return new WorkoutHistoryDetail(summary, workout.getNotes(), details);
    }
    private double weightInKg(SetEntry set) {
        if (set.getUnit() == null) {
            throw new IllegalStateException("Set unit is required for history totals");
        }
        return set.getUnit() == Unit.LB ? set.getWeight() * 0.45359237 : set.getWeight();
    }
}

