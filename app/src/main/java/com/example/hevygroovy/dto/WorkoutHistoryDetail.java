package com.example.hevygroovy.dto;

import java.util.List;
import java.time.Duration;

public class WorkoutHistoryDetail extends WorkoutHistory {
    private final String notes;
    private final List<WorkoutExerciseHistory> exercises;

    public WorkoutHistoryDetail(WorkoutHistory summary, String notes, List<WorkoutExerciseHistory> exercises) {
        super(summary.getWorkoutId(), summary.getNameSnapshot(), summary.getStartedAtEpochMillis(),
                summary.getEndedAtEpochMillis(), Duration.ofSeconds(summary.getDurationSeconds()),
                summary.getTotalExercises(), summary.getTotalSets(), summary.getTotalReps(),
                summary.getTotalVolume());
        this.notes = notes;
        this.exercises = List.copyOf(exercises);
    }

    public String getNotes() { return notes; }
    public List<WorkoutExerciseHistory> getExercises() { return exercises; }
}
