package com.example.hevygroovy.dto;

import java.util.List;
import java.time.Duration;

public class WorkoutHistoryDetail extends WorkoutHistory {
    private final String notes;
    private final List<WorkoutExerciseHistory> exercises;
    private final List<PersonalRecord> personalRecords;

    public WorkoutHistoryDetail(WorkoutHistory summary, String notes, List<WorkoutExerciseHistory> exercises) {
        this(summary, notes, exercises, List.of());
    }

    public WorkoutHistoryDetail(WorkoutHistory summary, String notes, List<WorkoutExerciseHistory> exercises,
                                List<PersonalRecord> personalRecords) {
        super(summary.getWorkoutId(), summary.getNameSnapshot(), summary.getStartedAtEpochMillis(),
                summary.getEndedAtEpochMillis(), Duration.ofSeconds(summary.getDurationSeconds()),
                summary.getTotalExercises(), summary.getTotalSets(), summary.getTotalReps(),
                summary.getTotalVolume());
        this.notes = notes;
        this.exercises = List.copyOf(exercises);
        this.personalRecords = List.copyOf(personalRecords);
    }

    public String getNotes() { return notes; }
    public List<WorkoutExerciseHistory> getExercises() { return exercises; }
    public List<PersonalRecord> getPersonalRecords() { return personalRecords; }
    @Override
    public int getPersonalRecordCount() { return personalRecords.size(); }
}
