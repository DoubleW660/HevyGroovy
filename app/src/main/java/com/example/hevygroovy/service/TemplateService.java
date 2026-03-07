package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.AddTemplateExerciseRequest;
import com.example.hevygroovy.dto.CreateTemplateRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import com.example.hevygroovy.model.TemplateDetailModel;
import com.example.hevygroovy.model.TemplateSummaryModel;

import java.util.List;

public interface TemplateService {

    TemplateSummaryModel createTemplate(CreateTemplateRequest request);

    TemplateSummaryModel updateTemplate(long templateId, UpdateTemplateRequest request);

    void addExerciseToTemplate(AddTemplateExerciseRequest request);

    void removeExerciseFromTemplate(long templateExerciseId, long templateId);

    void moveExerciseInTemplate(long templateExerciseId, int orderIndex);

    TemplateDetailModel getTemplateDetail(long templateId);

    List<TemplateSummaryModel> listTemplates(boolean includeArchived);

    void archiveTemplate(long templateId);

    void restoreTemplate(long templateId);
}
