package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;

public class UpdateSetEntryRequest {
    // Null leaves completion unchanged; false marks the set as a placeholder again.
    private Boolean completed;

    public Boolean getCompleted() { return completed; }

    public void setCompleted(Boolean completed) { this.completed = completed; }

    private Integer reps;
    private Double weight;
    private Unit unit;
    private Float rpe;
    private SetType setType;
    private Integer restSeconds;

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
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

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public void setRestSeconds(Integer restSeconds) {
        this.restSeconds = restSeconds;
    }
}
