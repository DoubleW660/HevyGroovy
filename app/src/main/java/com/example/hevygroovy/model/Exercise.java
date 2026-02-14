package com.example.hevygroovy.model;

public class Exercise {

    private long id;

    private String title;

    private String description;

    private String[] exerciseLog;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
