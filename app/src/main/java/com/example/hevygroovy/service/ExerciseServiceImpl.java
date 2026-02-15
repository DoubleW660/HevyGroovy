package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.model.ExerciseModel;
import com.example.hevygroovy.repo.ExerciseRepository;

import java.util.List;

public class ExerciseServiceImpl {
    private final ExerciseRepository exerciseRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public ExerciseModel createExercise(CreateExerciseRequest request){

        if (request == null){
            throw new RuntimeException("Request is Required");
        }
        String title = request.getTitle();
        if (title == null || title.isBlank()){
            throw new RuntimeException("Exercise Title is Required");
        }
        title = title.trim().replaceAll("\\s+"," ")
        String description = request.getDescription();
        if (description != null) {
            description = description.trim();
        }

        Exercise exercise = new Exercise();
        exercise.setTitle(title);
        exercise.setDescription(description);
        exercise.setArchived(false);

        Exercise saved = exerciseRepository.save(exercise);

        ExerciseModel model = new ExerciseModel();
        model.setExerciseId(saved.getId());
        model.setTitle(saved.getTitle());
        model.setDescription(saved.getDescription());
        model.setArchived(saved.isArchived());

        return model;
    }

    @Override
    public List<ExerciseModel> listExercises(String searchText, boolean includeArchived){
        return exerciseRepository.findAll(includeArchived)
                .stream()
                .map(this::ExerciseModel)
                .toList();

        return null;
    }

    private ExerciseModel toResponse(Exercise exercise) {
        return new ExerciseModel(exercise.getId(), exercise.getTitle());
    }
}
