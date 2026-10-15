package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.SetEntry;

import java.util.List;
import java.util.Optional;

public interface SetEntryRepository {

    SetEntry save(SetEntry setEntry);

    List<SetEntry> findByWorkoutExerciseId(long workoutExerciseId);

    void delete(long id);

    Optional<SetEntry> findById(long id);
    List<SetEntry> findByLoggedWorkoutId(long loggedWorkoutId);

    /**
     * Returns whether a recorded set exists through a WorkoutExercise belonging
     * to this workout. Exercise rows alone do not make a workout nonempty.
     * Implement as an existence query without loading all sets.
     */
    boolean existsByLoggedWorkoutId(long loggedWorkoutId);

}
