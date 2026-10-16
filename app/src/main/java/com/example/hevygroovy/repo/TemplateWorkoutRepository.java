package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.TemplateWorkout;

import java.util.List;
import java.util.Optional;

public interface TemplateWorkoutRepository {
    List<TemplateWorkout> findByUserId(long userId, boolean includeArchived);

    TemplateWorkout save(TemplateWorkout templateWorkout);

    Optional<TemplateWorkout> findById(long id);

    List<TemplateWorkout> findAll();

    List<TemplateWorkout> findByArchive(boolean includeArchived);

    boolean existsById(long id);
}
