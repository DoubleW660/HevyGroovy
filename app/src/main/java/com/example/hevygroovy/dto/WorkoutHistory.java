package com.example.hevygroovy.dto;

import java.time.Duration;

public class WorkoutHistory {

    private long workoutId;
    private String nameSnapshot;

    private long startedAtEpochMillis;
    private Long endedAtEpochMillis;

    private long durationSeconds;

    private int totalExercises;
    private int totalSets;
    private int totalReps;

    private double totalVolume;

    private int personalRecordCount;

    public WorkoutHistory(
            long workoutId,
            String nameSnapshot,
            long startedAtEpochMillis,
            Long endedAtEpochMillis,
            Duration duration,
            int totalExercises,
            int totalSets,
            int totalReps,
            double totalVolume
    ) {
        this.workoutId = workoutId;
        this.nameSnapshot = nameSnapshot;
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.durationSeconds = duration.getSeconds();
        this.totalExercises = totalExercises;
        this.totalSets = totalSets;
        this.totalReps = totalReps;
        this.totalVolume = totalVolume;
        this.personalRecordCount = 0;
    }

    public long getWorkoutId() {
        return workoutId;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public Long getEndedAtEpochMillis() {
        return endedAtEpochMillis;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public int getTotalExercises() {
        return totalExercises;
    }

    public int getTotalSets() {
        return totalSets;
    }

    public int getTotalReps() {
        return totalReps;
    }

    public double getTotalVolume() {
        return totalVolume;
    }

    public int getPersonalRecordCount() {
        return personalRecordCount;
    }
}