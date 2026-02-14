package com.example.hevygroovy.model;

public class TemplateExercise {

    public long getTemplateExerciseId() {
        return templateExerciseId;
    }

    public void setTemplateExerciseId(long templateExerciseId) {
        this.templateExerciseId = templateExerciseId;
    }

    private long templateExerciseId;

    private long templateWorkoutId;

    private long exerciseId;

    private int orderIndex;

    private int restTimeSeconds;

    public long getTemplateWorkoutId() {
        return templateWorkoutId;
    }

    public void setTemplateWorkoutId(long templateWorkoutId) {
        this.templateWorkoutId = templateWorkoutId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Integer getRestTime() {
        return restTimeSeconds;
    }

    public void setRestTime(Integer restTime) {
        this.restTimeSeconds = restTime;
    }
}
