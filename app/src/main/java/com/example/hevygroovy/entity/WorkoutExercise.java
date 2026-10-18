package com.example.hevygroovy.entity;

public class WorkoutExercise {
    private com.example.hevygroovy.entity.enums.TrackingType trackingType = com.example.hevygroovy.entity.enums.TrackingType.WEIGHT_REPS;
    public com.example.hevygroovy.entity.enums.TrackingType getTrackingType() { return trackingType; }
    public void setTrackingType(com.example.hevygroovy.entity.enums.TrackingType value) { trackingType = value; }

    private long id;
    private long loggedWorkoutId;
    private long exerciseId;
    private String exerciseNameSnapshot;
    private int orderIndex;
    private String notes;
    // Read projection of the current exercise setting; applies to all historical performances.
    private boolean unilateral;

    public boolean isUnilateral() { return unilateral; }
    public void setUnilateral(boolean unilateral) { this.unilateral = unilateral; }
    public int getVolumeMultiplier() { return unilateral ? 2 : 1; }

    private int restTimeSeconds;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getExerciseNameSnapshot() {
        return exerciseNameSnapshot;
    }

    public void setExerciseNameSnapshot(String exerciseNameSnapshot) {
        this.exerciseNameSnapshot = exerciseNameSnapshot;
    }


    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public long getLoggedWorkoutId() {
        return loggedWorkoutId;
    }

    public void setLoggedWorkoutId(long loggedWorkoutId) {
        this.loggedWorkoutId = loggedWorkoutId;
    }

    public int getRestTimeSeconds() {
        return restTimeSeconds;
    }

    public void setRestTimeSeconds(int restSeconds) {
        this.restTimeSeconds = restSeconds;
    }
}
