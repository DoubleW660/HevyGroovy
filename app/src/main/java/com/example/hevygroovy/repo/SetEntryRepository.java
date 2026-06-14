package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.SetEntry;

import java.util.List;

public interface SetEntryRepository {

    SetEntry save(SetEntry setEntry);

    List<SetEntry> findByWorkoutExerciseId(long workoutExerciseId);

    void delete(long id);

    List<SetEntry> findByLoggedWorkoutId(long loggedWorkoutId);

    boolean existsByLoggedWorkoutId(long loggedWorkoutId);

}
