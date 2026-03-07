package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.dto.UpdateExerciseRequest;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.model.ExerciseModel;
import com.example.hevygroovy.repo.ExerciseRepository;

import java.util.List;
import java.util.Optional;

public class ExerciseServiceImpl implements ExerciseService{
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
        title = title.trim().replaceAll("\\s+"," ");
        String description = request.getDescription();
        if (description != null) {
            description = description.trim();
        }

        Exercise exercise = new Exercise();
        exercise.setTitle(title);
        exercise.setDescription(description);
        exercise.setArchived(false);

        Exercise saved = exerciseRepository.save(exercise);

        return toModel(saved);
    }

    @Override
    public ExerciseModel updateExercise(long exerciseId, UpdateExerciseRequest request) {


        if(request == null){
            throw new RuntimeException("No Update Request Found");
        }

        Exercise exercise = requireExercise(exerciseId);

        String newTitle = request.getTitle();
        String newDesc = request.getDescription();

        if (newTitle != null && !newTitle.isBlank()){

            newTitle = newTitle.trim().replaceAll("\\s+"," ");
            exercise.setTitle(newTitle);
        }

        if (newDesc != null && !newDesc.isBlank()){
            newDesc = newDesc.trim();
            exercise.setDescription(newDesc);
        }

        Exercise saved = exerciseRepository.save(exercise);

        return toModel(saved);
    }

    @Override
    public ExerciseModel getExercise(long exerciseId) {
        Exercise exercise = requireExercise(exerciseId);

        return toModel(exercise);
    }

    @Override
    public void archiveExercise(long exerciseId) {

        Exercise exercise = requireExercise(exerciseId);

        if(exercise.isArchived()){
            throw new RuntimeException("Exercise Already Archived");
        }

        exercise.setArchived(true);

        exerciseRepository.save(exercise);
    }

    @Override
    public void restoreExercise(long exerciseId) {

        Exercise exercise = requireExercise(exerciseId);

        if(!exercise.isArchived()){
            throw new RuntimeException("Exercise Already Active");
        }
        exercise.setArchived(false);

        exerciseRepository.save(exercise);
    }
    @Override
    public List<ExerciseModel> listExercises(String searchText, boolean includeArchived){
        if(searchText == null || searchText.isBlank()) {

            return exerciseRepository.findAll(includeArchived)
                    .stream()
                    .map(this::toModel)
                    .toList();
        }


            searchText = searchText.trim().replaceAll("\\s+", " ");

            return exerciseRepository.searchByTitle(searchText, includeArchived)
                    .stream()
                    .map(this::toModel)
                    .toList();


    }
    @Override
    public ExerciseModel requireActiveExercise(long exerciseId){

        Exercise exercise = requireExercise(exerciseId);

        if(exercise.isArchived()){
            throw new RuntimeException("Exercise is Archived");
        }

        return toModel(exercise);
    }

    private ExerciseModel toModel(Exercise exercise){


        ExerciseModel model = new ExerciseModel();
        model.setExerciseId(exercise.getId());
        model.setTitle(exercise.getTitle());
        model.setDescription(exercise.getDescription());
        model.setArchived(exercise.isArchived());

        return model;
    }

    public Exercise requireExercise(long exerciseId){
        if (exerciseId <= 0){
            throw new RuntimeException("Invalid Id Provided");
        }

        Optional<Exercise> exerciseOptional = exerciseRepository.findById(exerciseId);

        if(exerciseOptional.isEmpty()){
            throw new RuntimeException("Exercise Not Found");
        }

        return exerciseOptional.get();
    }
}
