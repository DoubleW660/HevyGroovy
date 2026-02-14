package com.example.hevygroovy.model;

public class Exercise {

    private long exerciseId;

    private String title;

    private String description;

    private String[] exerciseLog;

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
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

    public String[] getExerciseLog() {
        return exerciseLog;
    }

    public void setExerciseLog(String[] exerciseLog) {
        this.exerciseLog = exerciseLog;
    }
}
