package com.example.hevygroovy.dto;

public final class HistoryImportResult {
    private final int importedWorkouts, skippedWorkouts, importedSets, createdExercises;
    public HistoryImportResult(int importedWorkouts, int skippedWorkouts, int importedSets, int createdExercises) {
        this.importedWorkouts = importedWorkouts; this.skippedWorkouts = skippedWorkouts;
        this.importedSets = importedSets; this.createdExercises = createdExercises;
    }
    public int getImportedWorkouts() { return importedWorkouts; }
    public int getSkippedWorkouts() { return skippedWorkouts; }
    public int getImportedSets() { return importedSets; }
    public int getCreatedExercises() { return createdExercises; }
}
