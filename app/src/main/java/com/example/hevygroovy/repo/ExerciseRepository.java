package com.example.hevygroovy.repo;

import com.example.hevygroovy.model.Exercise;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository {

    Exercise save(Exercise exercise);

    Optional<Exercise> findById(long id);

    List<Exercise> findAll(boolean includeArchived);

    List<Exercise> searchByTitle(String query, boolean includeArchived);

    void archive(long id);

    void restore(long id);

    boolean existsById(long id);

}


