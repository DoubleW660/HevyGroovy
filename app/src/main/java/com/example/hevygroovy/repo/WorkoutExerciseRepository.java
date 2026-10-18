package com.example.hevygroovy.repo;

import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.dto.PreviousExercisePerformance;
import com.example.hevygroovy.dto.ActiveSetEntry;

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
     * A performance requires at least one completed SetEntry for that WorkoutExercise.
     * Choose the latest by endedAtEpochMillis, then workout ID, then
     * workout-exercise ID (all descending) to resolve ties.
     * Preserve null or blank notes from the latest performance; do not fall
     * back to an older nonblank note. Omit IDs with no qualifying history.
     * Returns a non-null map, empty when exerciseIds is empty.
     */
    Map<Long, String> findLastNotesForUserExercises(long userId, List<Long> exerciseIds);

    /**
     * Latest finished workout with completed sets, by ended-at then workout ID, for each exercise.
     * Search across all templates and only this user. Retain all repeated exercise rows in that
     * workout, ordered by order-index/id, including empty occurrences; only completed sets are
     * returned within each row, ordered by original set-number/id, with original units/types.
     * Omit exercises without a completed performance. Implement in bounded batches.
     */
    Map<Long, PreviousExercisePerformance> findPreviousPerformancesForUserExercises(
            long userId, List<Long> exerciseIds);

    /**
     * Completed sets from the latest finished performance in this user's same template, by
     * exercise and ordered occurrence. Empty/skipped occurrences fall back to their own older
     * completed performance; never shift a later occurrence into an earlier slot. Preserve
     * set order, type, weight, reps and unit. Omit exercises with no qualifying history.
     */
    Map<Long, List<List<ActiveSetEntry>>> findLastCompletedSetsForTemplateExercises(
            long userId, long templateId, List<Long> exerciseIds);


}
