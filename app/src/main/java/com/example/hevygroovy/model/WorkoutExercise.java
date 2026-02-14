package com.example.hevygroovy.model;

public class WorkoutExercise {

    private long id;
    private long loggedWorkoutId;
    private long exerciseId;
    private String exerciseNameSnapshot;
    private int orderIndex;
    private String notes;


    public long id() {
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
}
