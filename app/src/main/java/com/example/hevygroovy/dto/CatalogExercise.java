package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.model.ExerciseModel;
import java.util.List;

public final class CatalogExercise {
    private final long id;
    private final String title;
    private final String description;
    private final MuscleGroup primaryMuscle;
    private final List<MuscleGroup> secondaryMuscles;
    private final boolean archived;
    private final boolean unilateral;

    public CatalogExercise(ExerciseModel model) {
        id = model.getExerciseId(); title = model.getTitle(); description = model.getDescription();
        primaryMuscle = model.getPrimaryMuscleGroup();
        secondaryMuscles = List.copyOf(model.getSecondaryMuscleGroups()); archived = model.isArchived();
        unilateral = model.isUnilateral();
    }
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public MuscleGroup getPrimaryMuscle() { return primaryMuscle; }
    public List<MuscleGroup> getSecondaryMuscles() { return secondaryMuscles; }
    public boolean isArchived() { return archived; }
    public boolean isUnilateral() { return unilateral; }
}
