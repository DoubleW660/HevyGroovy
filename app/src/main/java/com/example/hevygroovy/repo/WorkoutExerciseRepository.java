package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.WorkoutExercise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WorkoutExerciseRepository {

    WorkoutExercise save(WorkoutExercise workoutExercise);

    /** Delete after dependent SetEntry rows have been removed. */
    void delete(long id);

    Optional<WorkoutExercise> findById(long id);

    int findMaxOrderIndex(long loggedWorkoutId);

    List<WorkoutExercise> findByLoggedWorkoutId(long loggedWorkoutId);

    /**
     * Returns notes keyed by exercise ID from the user's latest completed
     * performance across all templates, including workouts without a template.
     * A performance requires at least one SetEntry for that WorkoutExercise.
     * Choose the latest by endedAtEpochMillis, then workout ID, then
     * workout-exercise ID (all descending) to resolve ties.
     * Preserve null or blank notes from the latest performance; do not fall
     * back to an older nonblank note. Omit IDs with no qualifying history.
     * Returns a non-null map, empty when exerciseIds is empty.
     */
    Map<Long, String> findLastNotesForUserExercises(long userId, List<Long> exerciseIds);


}
