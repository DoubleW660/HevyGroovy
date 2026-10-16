package com.example.hevygroovy.dto;

/** Omit the description setter to preserve it; pass null or blank to clear it. */
public class UpdateTemplateRequest {

    private String title;

    private String description;
    private boolean descriptionProvided;

    public boolean hasDescription() { return descriptionProvided; }

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
        descriptionProvided = true;
        this.description = description;
    }
}
