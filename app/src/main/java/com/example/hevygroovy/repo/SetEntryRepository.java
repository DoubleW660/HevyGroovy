package com.example.hevygroovy.repo;

import com.example.hevygroovy.model.SetEntry;

import java.util.List;
import java.util.Optional;

public interface SetEntryRepository {

    SetEntry save(SetEntry setEntry);

    List<SetEntry> findByWorkoutExerciseId(long workoutExerciseId);

    void delete(long id);

    List<SetEntry> findByLoggedWorkoutId(long loggedWorkoutId);

}
