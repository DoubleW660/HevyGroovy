package com.example.hevygroovy.model;

public class TemplateSummaryModel {

    private long templateWorkoutId;
    private String title;
    private String description;

    private boolean archived;

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

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
}
