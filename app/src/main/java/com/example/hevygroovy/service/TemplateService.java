package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.AddTemplateExerciseRequest;
import com.example.hevygroovy.dto.CreateTemplateRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import com.example.hevygroovy.model.TemplateDetailModel;
import com.example.hevygroovy.model.TemplateSummaryModel;

import java.util.List;

public interface TemplateService {

    TemplateSummaryModel createTemplate(long userId, CreateTemplateRequest request);

    TemplateSummaryModel updateTemplate(long userId, long templateId, UpdateTemplateRequest request);

    void addExerciseToTemplate(long userId, AddTemplateExerciseRequest request);

    void removeExerciseFromTemplate(long userId, long templateExerciseId, long templateId);

    void moveExerciseInTemplate(long userId, long templateExerciseId, int orderIndex);
    void updateExerciseRestTime(long userId, long templateExerciseId, int restSeconds);

    TemplateDetailModel getTemplateDetail(long userId, long templateId);

    List<TemplateSummaryModel> listTemplates(long userId, boolean includeArchived);

    void archiveTemplate(long userId, long templateId);

    void restoreTemplate(long userId, long templateId);
}
