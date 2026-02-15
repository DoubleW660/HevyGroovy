package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.model.ExerciseModel;

import java.util.List;

public interface ExerciseService {
    ExerciseModel createExercise(CreateExerciseRequest request);

    ExerciseModel updateExercise(long exerciseId, UpdateExerciseRequest request);

    List<ExerciseModel> listExercises(String searchText, boolean includeArchived);

    ExerciseModel getExercise(long exerciseId);

    void archiveExercise(long exerciseId);

    void restoreExercise(long exerciseId);

    ExerciseModel requireActiveExercise(long exerciseId);
}
