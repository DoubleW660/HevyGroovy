package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.LoggedWorkout;
import java.util.List;

/** Persisted session state; includes placeholders so the editor can resume exactly. */
public final class ActiveWorkoutDetail {
    private final long workoutId;
    private final Long templateId;
    private final String nameSnapshot;
    private final String notes;
    private final long startedAtEpochMillis;
    private final List<ActiveWorkoutExercise> exercises;

    public ActiveWorkoutDetail(LoggedWorkout workout, List<ActiveWorkoutExercise> exercises) {
        workoutId = workout.getId();
        templateId = workout.getTemplateId();
        nameSnapshot = workout.getNameSnapshot();
        notes = workout.getNotes();
        startedAtEpochMillis = workout.getStartedAtEpochMillis();
        this.exercises = List.copyOf(exercises);
    }

    public long getWorkoutId() { return workoutId; }
    public Long getTemplateId() { return templateId; }
    public String getNameSnapshot() { return nameSnapshot; }
    public String getNotes() { return notes; }
    public long getStartedAtEpochMillis() { return startedAtEpochMillis; }
    public List<ActiveWorkoutExercise> getExercises() { return exercises; }
}
