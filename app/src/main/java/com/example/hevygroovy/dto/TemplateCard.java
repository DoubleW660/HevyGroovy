package com.example.hevygroovy.dto;

import com.example.hevygroovy.model.TemplateSummaryModel;

public final class TemplateCard {
    private final long id;
    private final String title;
    private final String description;
    private final boolean archived;
    public TemplateCard(TemplateSummaryModel model) {
        id = model.getTemplateWorkoutId(); title = model.getTitle();
        description = model.getDescription(); archived = model.isArchived();
    }
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isArchived() { return archived; }
}
