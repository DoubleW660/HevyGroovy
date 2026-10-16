package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.Objects;

/** Immutable record with its saved performance. Weight/volume values are normalized to KG. */
public final class PersonalRecord {
    private final RecordType type;
    private final long exerciseId;
    private final String exerciseName;
    private final long workoutId;
    private final long workoutExerciseId;
    private final long setId;
    private final double value;
    private final double weight;
    private final Unit unit;
    private final int reps;
    private final long achievedAtEpochMillis;

    public PersonalRecord(RecordType type, long exerciseId, String exerciseName, long workoutId,
                          long workoutExerciseId, long setId, double value, double weight,
                          Unit unit, int reps, long achievedAtEpochMillis) {
        this.type = Objects.requireNonNull(type);
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName == null || exerciseName.isBlank() ? "Exercise" : exerciseName;
        this.workoutId = workoutId;
        this.workoutExerciseId = workoutExerciseId;
        this.setId = setId;
        this.value = value;
        this.weight = weight;
        this.unit = Objects.requireNonNull(unit);
        this.reps = reps;
        this.achievedAtEpochMillis = achievedAtEpochMillis;
    }

    public RecordType getType() { return type; }
    public long getExerciseId() { return exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public long getWorkoutId() { return workoutId; }
    public long getWorkoutExerciseId() { return workoutExerciseId; }
    /** Zero for an exercise's total workout volume, which has no single source set. */
    public long getSetId() { return setId; }
    /** KG, KG x reps, or a rep count according to the record type. */
    public double getValue() { return value; }
    /** Source set's weight, before unit conversion; zero for total workout volume. */
    public double getWeight() { return weight; }
    public Unit getUnit() { return unit; }
    /** Source set reps, or the combined rep count for total workout volume. */
    public int getReps() { return reps; }
    public long getAchievedAtEpochMillis() { return achievedAtEpochMillis; }
}
