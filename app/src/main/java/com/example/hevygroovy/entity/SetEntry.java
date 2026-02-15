package com.example.hevygroovy.entity;

import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;

public class SetEntry {

    private long id;

    private long workoutExerciseId;

    private int setNumber;

    private int reps;

    private double weight;

    private Unit unit;

    private Integer restSeconds;

    private Float rpe;

    private SetType setType;

    private long createdAtEpochMillis;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getWorkoutExerciseId() {
        return workoutExerciseId;
    }

    public void setWorkoutExerciseId(long workoutExerciseId) {
        this.workoutExerciseId = workoutExerciseId;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(int setNumber) {
        this.setNumber = setNumber;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public void setRestSeconds(Integer restSeconds) {
        this.restSeconds = restSeconds;
    }

    public Float getRpe() {
        return rpe;
    }

    public void setRpe(Float rpe) {
        this.rpe = rpe;
    }

    public SetType getSetType() {
        return setType;
    }

    public void setSetType(SetType setType) {
        this.setType = setType;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public void setCreatedAtEpochMillis(long createdAtEpochMillis) {
        this.createdAtEpochMillis = createdAtEpochMillis;
    }
}
