package com.example.hevygroovy.service;

import java.util.List;

public class ExerciseService {
    ExerciseModel createExercise(CreateExerciseRequest request);

    ExerciseModel updateExercise(CreateExerciseRequest request);

    List<ExerciseModel> listExercises(String searchText, boolean includeArchived);

    ExerciseModel getExercise(long exerciseId);

    void archiveExercise(long exerciseId);

    void restoreExercise(long exerciseId);

}
