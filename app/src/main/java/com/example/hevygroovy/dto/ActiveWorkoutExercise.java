package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.util.OneRepMaxEstimator;
import java.util.List;

public final class ActiveWorkoutExercise {
    private final com.example.hevygroovy.entity.enums.TrackingType trackingType;
    public com.example.hevygroovy.entity.enums.TrackingType getTrackingType() { return trackingType; }
    private final long workoutExerciseId;
    private final long exerciseId;
    private final String exerciseNameSnapshot;
    private final int orderIndex;
    private final int restTimeSeconds;
    private final String notes;
    private final List<ActiveSetEntry> sets;
    private final List<ActiveSetEntry> previousSets;
    private final Long previousWorkoutId;
    private final Long previousEndedAtEpochMillis;
    private final double estimatedOneRepMax;
    private final boolean unilateral;

    public ActiveWorkoutExercise(WorkoutExercise exercise, List<ActiveSetEntry> sets) {
        this(exercise, sets, List.of(), null, null);
    }

    public ActiveWorkoutExercise(WorkoutExercise exercise, List<ActiveSetEntry> sets,
                                  List<ActiveSetEntry> previousSets, Long previousWorkoutId,
                                  Long previousEndedAtEpochMillis) {
        workoutExerciseId = exercise.getId();
        exerciseId = exercise.getExerciseId();
        exerciseNameSnapshot = exercise.getExerciseNameSnapshot();
        orderIndex = exercise.getOrderIndex();
        restTimeSeconds = exercise.getRestTimeSeconds();
        notes = exercise.getNotes();
        unilateral = exercise.isUnilateral();
        trackingType = exercise.getTrackingType();
        this.sets = List.copyOf(sets);
        this.previousSets = List.copyOf(previousSets);
        this.previousWorkoutId = previousWorkoutId;
        this.previousEndedAtEpochMillis = previousEndedAtEpochMillis;
        estimatedOneRepMax = sets.stream().mapToDouble(set -> OneRepMaxEstimator.estimateKg(
                set.getWeight(), set.getUnit(), set.getReps(), set.getSetType(), set.isCompleted()))
                .max().orElse(0);
    }

    public long getWorkoutExerciseId() { return workoutExerciseId; }
    public long getExerciseId() { return exerciseId; }
    public String getExerciseNameSnapshot() { return exerciseNameSnapshot; }
    public int getOrderIndex() { return orderIndex; }
    public int getRestTimeSeconds() { return restTimeSeconds; }
    public String getNotes() { return notes; }
    public List<ActiveSetEntry> getSets() { return sets; }
    public List<ActiveSetEntry> getPreviousSets() { return previousSets; }
    public Long getPreviousWorkoutId() { return previousWorkoutId; }
    public Long getPreviousEndedAtEpochMillis() { return previousEndedAtEpochMillis; }
    /** Best current completed working-set Epley estimate in KG; zero means unavailable. */
    public double getEstimatedOneRepMax() { return estimatedOneRepMax; }
    public boolean isUnilateral() { return unilateral; }
}
