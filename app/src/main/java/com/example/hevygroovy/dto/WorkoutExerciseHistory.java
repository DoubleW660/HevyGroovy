package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.Unit;

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

    private final List<ActiveSetEntry> sets;

    public WorkoutExerciseHistory(
            long workoutExerciseId,
            long exerciseId,
            String exerciseName,
            int orderIndex,
            String notes,
            int totalSets,
            int totalReps,
            double totalVolume,
            double heaviestWeight,
            double estimatedOneRepMax,
            double bestSetVolume,
            boolean personalRecord,
            int personalRecordCount,
            List<SetEntry> sets) {
        this.workoutExerciseId = workoutExerciseId;
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName;
        this.orderIndex = orderIndex;
        this.notes = notes;
        this.totalSets = totalSets;
        this.totalReps = totalReps;
        this.totalVolume = totalVolume;
        this.heaviestWeight = heaviestWeight;
        this.estimatedOneRepMax = estimatedOneRepMax;
        this.bestSetVolume = bestSetVolume;
        this.personalRecord = personalRecord;
        this.personalRecordCount = personalRecordCount;
        this.sets = sets.stream().map(ActiveSetEntry::new)
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    public long getWorkoutExerciseId() {
        return workoutExerciseId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getNotes() {
        return notes;
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

    /** Aggregate weights use KG; individual sets retain their original units. */
    public Unit getWeightUnit() { return Unit.KG; }

    public double getHeaviestWeight() {
        return heaviestWeight;
    }

    public double getEstimatedOneRepMax() {
        return estimatedOneRepMax;
    }

    public double getBestSetVolume() {
        return bestSetVolume;
    }

    public boolean isPersonalRecord() {
        return personalRecord;
    }

    public int getPersonalRecordCount() {
        return personalRecordCount;
    }

    public List<ActiveSetEntry> getSets() {
        return sets;
    }
}
