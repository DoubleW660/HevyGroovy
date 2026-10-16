package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.AddTemplateExerciseRequest;
import com.example.hevygroovy.dto.CreateTemplateRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.model.TemplateDetailModel;
import com.example.hevygroovy.model.TemplateExerciseItemModel;
import com.example.hevygroovy.model.TemplateSummaryModel;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TemplateServiceImpl implements TemplateService {

    private final TemplateWorkoutRepository templateWorkoutRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final ExerciseService exerciseService;

    public TemplateServiceImpl(TemplateWorkoutRepository templateWorkoutRepository, TemplateExerciseRepository templateExerciseRepository, ExerciseService exerciseService){
        this.templateExerciseRepository = templateExerciseRepository;
        this.templateWorkoutRepository = templateWorkoutRepository;
        this.exerciseService = exerciseService;
    }

    @Override
    public TemplateSummaryModel createTemplate(long userId, CreateTemplateRequest request) {
        requireUserId(userId);
        if (request == null){
            throw new RuntimeException("Request is Required");
        }
        String title = request.getTitle();
        if (title == null || title.isBlank()){
            throw new RuntimeException("Template Title is Required");
        }
        title = title.trim().replaceAll("\\s+"," ");

        String description = request.getDescription();
        if (description != null) {
            description = description.trim();
        }

        long currentTime = System.currentTimeMillis();

        TemplateWorkout templateWorkout = new TemplateWorkout();
        templateWorkout.setUserId(userId);
        templateWorkout.setTitle(title);
        templateWorkout.setDescription(description);
        templateWorkout.setCreatedAtEpochMillis(currentTime);
        templateWorkout.setArchived(false);

        TemplateWorkout saved = templateWorkoutRepository.save(templateWorkout);

        return toSummaryModel(saved);
    }

    @Override
    public TemplateSummaryModel updateTemplate(long userId, long templateId, UpdateTemplateRequest request) {

        if(request == null){
            throw new RuntimeException("No Template Update Request Found");
        }

        TemplateWorkout templateWorkout = requireActiveTemplate(userId, templateId);

        String newTitle = request.getTitle();
        String newDesc = request.getDescription();

        if (newTitle != null && !newTitle.isBlank()){

            newTitle = newTitle.trim().replaceAll("\\s+"," ");
            templateWorkout.setTitle(newTitle);
        }

        if (newDesc != null && !newDesc.isBlank()){
            newDesc = newDesc.trim().replaceAll("\\s+"," ");
            templateWorkout.setDescription(newDesc);
        }

        TemplateWorkout saved = templateWorkoutRepository.save(templateWorkout);

        return toSummaryModel(saved);
    }

    @Override
    public void addExerciseToTemplate(long userId, AddTemplateExerciseRequest request) {

        if(request == null){
            throw new RuntimeException("No Template Exercise Request Found");
        }

        requireActiveTemplate(userId, request.getTemplateId());

        exerciseService.requireActiveExercise(request.getExerciseId());

        Integer restTime = request.getRestTimeSeconds();
        if (restTime == null) { restTime = 0; }
        if (restTime < 0) {
            throw new RuntimeException("Rest seconds cannot be negative");
        }
        Integer newIndex = request.getOrderIndex();
        if(newIndex == null){
            Integer maxIndex = templateExerciseRepository.findMaxOrderIndex(request.getTemplateId());
            if(maxIndex == null){
                newIndex = 0;
            } else {
                newIndex = maxIndex + 1;
            }
        } else {
            List<TemplateExercise> existing = new ArrayList<>(
                    templateExerciseRepository.findByTemplateWorkoutId(request.getTemplateId()));
            if (newIndex < 0 || newIndex > existing.size()) {
                throw new RuntimeException("Position is not available");
            }
            existing.sort(Comparator.comparingInt(TemplateExercise::getOrderIndex)
                    .thenComparingLong(TemplateExercise::getId));
            for (int i = 0; i < existing.size(); i++) {
                TemplateExercise current = existing.get(i);
                int shiftedIndex = i < newIndex ? i : i + 1;
                if (current.getOrderIndex() != shiftedIndex) {
                    current.setOrderIndex(shiftedIndex);
                    templateExerciseRepository.save(current);
                }
            }
        }


        TemplateExercise templateExercise = new TemplateExercise();

        templateExercise.setExerciseId(request.getExerciseId());
        templateExercise.setTemplateWorkoutId(request.getTemplateId());
        templateExercise.setOrderIndex(newIndex);
        templateExercise.setRestTimeSeconds(restTime);

        templateExerciseRepository.save(templateExercise);
    }

    @Override
    public void removeExerciseFromTemplate(long userId, long templateExerciseId, long templateId) {
        if(templateId <= 0){
            throw new RuntimeException("Invalid Template Id");
        }

        requireActiveTemplate(userId, templateId);

        TemplateExercise templateExercise = requireTemplateExercise(templateExerciseId);

        if (templateExercise.getTemplateWorkoutId() != templateId) {
            throw new RuntimeException("Template Exercise does not belong to this Template");
        }

        int removalIndex = templateExercise.getOrderIndex();

        templateExerciseRepository.delete(templateExerciseId);

        List<TemplateExercise> exercises = new ArrayList<>(templateExerciseRepository.findByTemplateWorkoutId(templateId));
        exercises.sort(Comparator.comparingInt(TemplateExercise::getOrderIndex));

        for (TemplateExercise current : exercises) {
            if (current.getOrderIndex() > removalIndex) {
                current.setOrderIndex(current.getOrderIndex() - 1);
                templateExerciseRepository.save(current);
            }
        }
    }

    @Override
    public void moveExerciseInTemplate(long userId, long templateExerciseId, int orderIndex) {
        requireUserId(userId);
        if(templateExerciseId <= 0){
            throw new RuntimeException("Invalid Template Id");
        }

        if(orderIndex < 0){
            throw new RuntimeException("Invalid New Index");
        }

        TemplateExercise templateExercise = requireTemplateExercise(templateExerciseId);

        long templateId = templateExercise.getTemplateWorkoutId();
        requireActiveTemplate(userId, templateId);

        List<TemplateExercise> exercises = new ArrayList<>(templateExerciseRepository.findByTemplateWorkoutId(templateId));
        exercises.sort(Comparator.comparingInt(TemplateExercise::getOrderIndex));

        if(orderIndex > exercises.size() - 1){
            throw new RuntimeException("Position is not available");
        }

        int previousIndex = templateExercise.getOrderIndex();

        if(previousIndex == orderIndex){return;}

        if(orderIndex < previousIndex){
            for (TemplateExercise current : exercises) {
                if(current.getId() == templateExerciseId){continue;}

                if (current.getOrderIndex() >= orderIndex && current.getOrderIndex() < previousIndex) {
                    current.setOrderIndex(current.getOrderIndex() + 1);
                    templateExerciseRepository.save(current);
                }
            }
        } else {
            for (TemplateExercise current : exercises) {
                if(current.getId() == templateExerciseId){continue;}

                if (current.getOrderIndex() > previousIndex && current.getOrderIndex() <= orderIndex) {
                    current.setOrderIndex(current.getOrderIndex() - 1);
                    templateExerciseRepository.save(current);
                }
            }
        }
        templateExercise.setOrderIndex(orderIndex);
        templateExerciseRepository.save(templateExercise);

    }

    @Override
    public TemplateDetailModel getTemplateDetail(long userId, long templateId) {
        if(templateId <= 0){
            throw new RuntimeException("Invalid Template Id");
        }
        TemplateWorkout templateWorkout = requireOwnedTemplate(userId, templateId);

        List<TemplateExercise> exercises = new ArrayList<>(templateExerciseRepository.findByTemplateWorkoutId(templateId));
        exercises.sort(Comparator.comparingInt(TemplateExercise::getOrderIndex));

        List<TemplateExerciseItemModel> templateExerciseItemModels = new ArrayList<>(exercises.size());

        for (TemplateExercise current : exercises) {
            Exercise exercise = exerciseService.requireExercise(current.getExerciseId());

            templateExerciseItemModels.add(toExerciseItemModel(current, exercise));
        }

        return toDetailModel(templateWorkout, templateExerciseItemModels);
    }

    @Override
    public List<TemplateSummaryModel> listTemplates(long userId, boolean includeArchived) {
        requireUserId(userId);

        if(includeArchived){
            return templateWorkoutRepository.findAll()
                    .stream()
                    .filter(template -> template.getUserId() == userId)
                    .map(this::toSummaryModel)
                    .toList();
        }

        return templateWorkoutRepository.findByArchive(false)
                .stream()
                .filter(template -> template.getUserId() == userId)
                .map(this::toSummaryModel)
                .toList();

    }

    @Override
    public void archiveTemplate(long userId, long templateId) {
        TemplateWorkout templateWorkout = requireOwnedTemplate(userId, templateId);

        if(templateWorkout.isArchived()){
            throw new RuntimeException("Template Already Archived");
        }

        templateWorkout.setArchived(true);

        templateWorkoutRepository.save(templateWorkout);
    }

    @Override
    public void restoreTemplate(long userId, long templateId) {
        TemplateWorkout templateWorkout = requireOwnedTemplate(userId, templateId);

        if(!templateWorkout.isArchived()){
            throw new RuntimeException("Template Already Active");
        }

        templateWorkout.setArchived(false);

        templateWorkoutRepository.save(templateWorkout);
    }

    private TemplateSummaryModel toSummaryModel(TemplateWorkout templateWorkout){
        TemplateSummaryModel model = new TemplateSummaryModel();
        model.setTemplateWorkoutId(templateWorkout.getId());
        model.setTitle(templateWorkout.getTitle());
        model.setDescription(templateWorkout.getDescription());
        model.setArchived(templateWorkout.isArchived());

        return model;
    }

    private TemplateExerciseItemModel toExerciseItemModel(TemplateExercise templateExercise, Exercise exercise){

        TemplateExerciseItemModel model = new TemplateExerciseItemModel();
        model.setTemplateExerciseId(templateExercise.getId());
        model.setExerciseId(templateExercise.getExerciseId());
        model.setRestTargetSeconds(templateExercise.getRestTimeSeconds());
        model.setOrderIndex(templateExercise.getOrderIndex());

        model.setExerciseTitle(exercise.getTitle());

        return model;
    }

    private TemplateDetailModel toDetailModel(TemplateWorkout templateWorkout, List<TemplateExerciseItemModel> list){

        TemplateDetailModel model = new TemplateDetailModel();
        model.setTemplateWorkoutId(templateWorkout.getId());
        model.setTitle(templateWorkout.getTitle());
        model.setDescription(templateWorkout.getDescription());
        model.setArchived(templateWorkout.isArchived());

        model.setExercises(list);

        return model;
    }

    private TemplateWorkout requireTemplate(long templateId){
        if (templateId <= 0){
            throw new RuntimeException("Invalid Id Provided");
        }

        Optional<TemplateWorkout> templateWorkoutOptional = templateWorkoutRepository.findById(templateId);

        if(templateWorkoutOptional.isEmpty()){
            throw new RuntimeException("Template Not Found");
        }

        return templateWorkoutOptional.get();
    }

    private TemplateWorkout requireActiveTemplate(long userId, long templateId){
        TemplateWorkout templateWorkout = requireOwnedTemplate(userId, templateId);

        if(templateWorkout.isArchived()){
            throw new RuntimeException("Template is Archived");
        }

        return templateWorkout;
    }

    private void requireUserId(long userId) {
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }
    }

    private TemplateWorkout requireOwnedTemplate(long userId, long templateId) {
        requireUserId(userId);
        TemplateWorkout template = requireTemplate(templateId);
        if (template.getUserId() != userId) {
            throw new RuntimeException("Template does not belong to user");
        }
        return template;
    }

    private TemplateExercise requireTemplateExercise(long templateExerciseId){

        if(templateExerciseId <= 0){
            throw new RuntimeException("Invalid Template Exercise Id");
        }

        Optional<TemplateExercise> optionalTemplateExercise = templateExerciseRepository.findById(templateExerciseId);

        if(optionalTemplateExercise.isEmpty()){
            throw new RuntimeException("No Exercise Found");
        }

        return optionalTemplateExercise.get();
    }
}
