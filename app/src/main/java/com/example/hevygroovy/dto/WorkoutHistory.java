package com.example.hevygroovy.dto;

public class WorkoutHistory {

    private long workoutId;

    private long startedAtEpochMillis;
    private Long endedAtEpochMillis;

    private long durationSeconds;

    private int totalExercises;
    private int totalSets;
    private int totalReps;

    private double totalVolume;

    private int personalRecordCount;
}
