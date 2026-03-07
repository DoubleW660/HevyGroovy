package com.example.hevygroovy.entity;

public class TemplateExercise {

    private long id;

    private long templateWorkoutId;

    private long exerciseId;

    private int orderIndex;

    private Integer restTimeSeconds;


    public Integer getRestTimeSeconds() {
        return restTimeSeconds;
    }

    public void setRestTimeSeconds(Integer restTimeSeconds) {
        this.restTimeSeconds = restTimeSeconds;
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

    public long getTemplateWorkoutId() {
        return templateWorkoutId;
    }

    public void setTemplateWorkoutId(long templateWorkoutId) {
        this.templateWorkoutId = templateWorkoutId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
