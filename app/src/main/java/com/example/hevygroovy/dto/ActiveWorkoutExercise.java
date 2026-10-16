package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.WorkoutExercise;
import java.util.List;

public final class ActiveWorkoutExercise {
    private final long workoutExerciseId;
    private final long exerciseId;
    private final String exerciseNameSnapshot;
    private final int orderIndex;
    private final int restTimeSeconds;
    private final String notes;
    private final List<ActiveSetEntry> sets;

    public ActiveWorkoutExercise(WorkoutExercise exercise, List<ActiveSetEntry> sets) {
        workoutExerciseId = exercise.getId();
        exerciseId = exercise.getExerciseId();
        exerciseNameSnapshot = exercise.getExerciseNameSnapshot();
        orderIndex = exercise.getOrderIndex();
        restTimeSeconds = exercise.getRestTimeSeconds();
        notes = exercise.getNotes();
        this.sets = List.copyOf(sets);
    }

    public long getWorkoutExerciseId() { return workoutExerciseId; }
    public long getExerciseId() { return exerciseId; }
    public String getExerciseNameSnapshot() { return exerciseNameSnapshot; }
    public int getOrderIndex() { return orderIndex; }
    public int getRestTimeSeconds() { return restTimeSeconds; }
    public String getNotes() { return notes; }
    public List<ActiveSetEntry> getSets() { return sets; }
}
