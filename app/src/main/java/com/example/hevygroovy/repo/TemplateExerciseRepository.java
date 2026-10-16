package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.model.TemplateExerciseItemModel;

import java.util.List;
import java.util.Optional;

public interface TemplateExerciseRepository {

    TemplateExercise save(TemplateExercise templateExercise);

    Optional<TemplateExercise> findById(long id);

    List<TemplateExercise> findByTemplateWorkoutId(long templateWorkoutId);

    /** Loads all rows with current exercise titles in one query, ordered by orderIndex then row ID. */
    List<TemplateExerciseItemModel> findDetailsByTemplateWorkoutId(long templateWorkoutId);

    Integer findMaxOrderIndex(long templateWorkoutId);

    void delete(long templateExerciseId);

    boolean existsInTemplate(long templateWorkoutId, long exerciseId);
}
