package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.MuscleGroup;

import java.util.ArrayList;
import java.util.List;

public class CreateExerciseRequest {
    private com.example.hevygroovy.entity.enums.TrackingType trackingType = com.example.hevygroovy.entity.enums.TrackingType.WEIGHT_REPS;
    public com.example.hevygroovy.entity.enums.TrackingType getTrackingType() { return trackingType; }
    public void setTrackingType(com.example.hevygroovy.entity.enums.TrackingType value) { trackingType = java.util.Objects.requireNonNull(value); }

    private String title;
    private boolean unilateral;

    public boolean isUnilateral() { return unilateral; }
    public void setUnilateral(boolean unilateral) { this.unilateral = unilateral; }

    private String description;

    private MuscleGroup primaryMuscleGroup;

    private List<MuscleGroup> secondaryMuscleGroups = new ArrayList<>();

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
