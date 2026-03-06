package com.example.hevygroovy.model;

public class TemplateExerciseItemModel {

    private long templateExerciseId;

    private long exerciseId;

    private String exerciseTitle;

    private int orderIndex;

    private Integer restTargetSeconds;

    public long getTemplateExerciseId() {
        return templateExerciseId;
    }

    public void setTemplateExerciseId(long templateExerciseId) {
        this.templateExerciseId = templateExerciseId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseTitle() {
        return exerciseTitle;
    }

    public void setExerciseTitle(String exerciseTitle) {
        this.exerciseTitle = exerciseTitle;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Integer getRestTargetSeconds() {
        return restTargetSeconds;
    }

    public void setRestTargetSeconds(int restTargetSeconds) {
        this.restTargetSeconds = restTargetSeconds;
    }
}
