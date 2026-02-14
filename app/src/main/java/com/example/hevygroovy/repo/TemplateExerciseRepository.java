package com.example.hevygroovy.repo;

import com.example.hevygroovy.model.TemplateExercise;

import java.util.List;
import java.util.Optional;

public interface TemplateExerciseRepository {

    TemplateExercise save(TemplateExercise templateExercise);

    Optional<TemplateExercise> findById(long id);

    List<TemplateExercise> findByTempleteId(long templateWorkoutId);

    int findMaxOrderIndex(long templateWorkoutId);

    void delete(long templateExerciseId);


}
