package com.example.hevygroovy.repo;

import com.example.hevygroovy.model.TemplateWorkout;

import java.util.List;
import java.util.Optional;

public interface TemplateWorkoutRepository {

    TemplateWorkout save(TemplateWorkout templateWorkout);

    Optional<TemplateWorkout> findById(long id);

    List<TemplateWorkout> findAll(boolean includeArchived);

    void archive(long id);

    void restore(long id);

    boolean existsById(long id);
}
