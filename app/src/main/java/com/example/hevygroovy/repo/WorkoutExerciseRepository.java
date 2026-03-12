package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.WorkoutExercise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WorkoutExerciseRepository {

    WorkoutExercise save(WorkoutExercise workoutExercise);

    Optional<WorkoutExercise> findById(long id);

    int findMaxOrderIndex(long loggedWorkoutId);

    List<WorkoutExercise> findByLoggedWorkoutId(long loggedWorkoutId);

    Map<Long, String> findLastNotesForUserExercises(long userId, List<Long> exerciseIds);


}
