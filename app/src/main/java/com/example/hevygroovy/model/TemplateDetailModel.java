package com.example.hevygroovy.model;

import java.util.ArrayList;
import java.util.List;

public class TemplateDetailModel {

    private long templateWorkoutId;

    private String title;

    private String description;

    private boolean archived;

    private List<TemplateExerciseItemModel> exercises = new ArrayList<>();;

    public long getTemplateWorkoutId() {
        return templateWorkoutId;
    }

    public void setTemplateWorkoutId(long templateWorkoutId) {
        this.templateWorkoutId = templateWorkoutId;
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

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public List<TemplateExerciseItemModel> getExercises() {
        return exercises;
    }

    public void setExercises(List<TemplateExerciseItemModel> exercises) {
        this.exercises = exercises;
    }
}
