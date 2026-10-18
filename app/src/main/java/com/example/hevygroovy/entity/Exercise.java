package com.example.hevygroovy.entity;

import com.example.hevygroovy.entity.enums.MuscleGroup;

import java.util.ArrayList;
import java.util.List;

public class Exercise {
    private com.example.hevygroovy.entity.enums.TrackingType trackingType = com.example.hevygroovy.entity.enums.TrackingType.WEIGHT_REPS;
    public com.example.hevygroovy.entity.enums.TrackingType getTrackingType() { return trackingType; }
    public void setTrackingType(com.example.hevygroovy.entity.enums.TrackingType value) { trackingType = java.util.Objects.requireNonNull(value); }

    private long id;

    private String title;

    private String description;



    private MuscleGroup primaryMuscleGroup;

    private List<MuscleGroup> secondaryMuscleGroups = new ArrayList<>();

    private boolean archived;
    private boolean unilateral;

    public boolean isUnilateral() { return unilateral; }
    public void setUnilateral(boolean unilateral) { this.unilateral = unilateral; }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

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


    public List<MuscleGroup> getSecondaryMuscleGroups() {
        return secondaryMuscleGroups;
    }

    public void setSecondaryMuscleGroups(List<MuscleGroup> secondaryMuscleGroups) {
        this.secondaryMuscleGroups = secondaryMuscleGroups;
    }

    public MuscleGroup getPrimaryMuscleGroup() {
        return primaryMuscleGroup;
    }

    public void setPrimaryMuscleGroup(MuscleGroup primaryMuscleGroup) {
        this.primaryMuscleGroup = primaryMuscleGroup;
    }
}
