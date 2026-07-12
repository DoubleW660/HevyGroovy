package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.SetEntry;

import java.util.List;

public class WorkoutExerciseHistory {

    private long workoutExerciseId;
    private long exerciseId;

    private String exerciseName;

    private int orderIndex;

    private String notes;

    private int totalSets;
    private int totalReps;

    private double totalVolume;

    private double heaviestWeight;

    private double estimatedOneRepMax;

    private double bestSetVolume;

    private boolean personalRecord;

    private int personalRecordCount;

    private List<SetEntry> sets;
}
