package com.example.hevygroovy.dto;

import com.example.hevygroovy.model.ExerciseModel;

/** Immutable exercise-library entry used by the workout picker. */
public final class ExerciseOption {
    private final long id;
    private final String title;

    public ExerciseOption(ExerciseModel exercise) {
        id = exercise.getExerciseId();
        title = exercise.getTitle();
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
}
