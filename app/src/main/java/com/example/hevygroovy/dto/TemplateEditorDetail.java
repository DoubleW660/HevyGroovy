package com.example.hevygroovy.dto;

import com.example.hevygroovy.model.TemplateDetailModel;
import com.example.hevygroovy.model.TemplateExerciseItemModel;
import java.util.List;
import java.util.stream.Collectors;

public final class TemplateEditorDetail {
    private final long id;
    private final String title;
    private final String description;
    private final boolean archived;
    private final List<ExerciseRow> exercises;
    public TemplateEditorDetail(TemplateDetailModel model) {
        id = model.getTemplateWorkoutId(); title = model.getTitle(); description = model.getDescription();
        archived = model.isArchived();
        exercises = model.getExercises().stream().map(ExerciseRow::new).collect(Collectors.toUnmodifiableList());
    }
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isArchived() { return archived; }
    public List<ExerciseRow> getExercises() { return exercises; }

    public static final class ExerciseRow {
        private final long id;
        private final long exerciseId;
        private final String title;
        private final int restSeconds;
        public ExerciseRow(TemplateExerciseItemModel model) {
            id = model.getTemplateExerciseId(); exerciseId = model.getExerciseId(); title = model.getExerciseTitle();
            restSeconds = model.getRestTargetSeconds() == null ? 0 : model.getRestTargetSeconds();
        }
        public long getId() { return id; }
        public long getExerciseId() { return exerciseId; }
        public String getTitle() { return title; }
        public int getRestSeconds() { return restSeconds; }
    }
}
