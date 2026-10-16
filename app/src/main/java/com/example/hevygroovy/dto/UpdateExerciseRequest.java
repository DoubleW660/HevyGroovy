package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.MuscleGroup;

import java.util.ArrayList;
import java.util.List;

/** Omit the description setter to preserve it; pass null or blank to clear it. */
public class UpdateExerciseRequest {

    private String title;

    private String description;
    private boolean descriptionProvided;

    public boolean hasDescription() { return descriptionProvided; }

    private MuscleGroup primaryMuscleGroup;

    private List<MuscleGroup> secondaryMuscleGroups;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        descriptionProvided = true;
        this.description = description;
    }

    public MuscleGroup getPrimaryMuscleGroup() {
        return primaryMuscleGroup;
    }

    public void setPrimaryMuscleGroup(MuscleGroup primaryMuscleGroup) {
        this.primaryMuscleGroup = primaryMuscleGroup;
    }

    public List<MuscleGroup> getSecondaryMuscleGroups() {
        return secondaryMuscleGroups;
    }

    public void setSecondaryMuscleGroups(List<MuscleGroup> secondaryMuscleGroups) {
        this.secondaryMuscleGroups = secondaryMuscleGroups;
    }

}
