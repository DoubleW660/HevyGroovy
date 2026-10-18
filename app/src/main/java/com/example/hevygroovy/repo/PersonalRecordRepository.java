package com.example.hevygroovy.repo;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.dto.ExercisePerformance;
import java.util.List;

/** Aggregated record reads from immutable finished-workout performances. */
public interface PersonalRecordRepository {
    /**
     * At most four winners per exercise. Completed, non-warmup sets with positive reps qualify.
     * Weight/volume must also be positive. Combine duplicate exercise rows within each workout.
     * A non-null cutoff includes only workouts earlier by ended-at, then workout ID; a null
     * cutoff includes all finished workouts. Never include an active workout or another user.
     * Equal maxima retain the earliest finished performance, then row/set ordering.
     * The service applies a unit-conversion tolerance when deciding whether a best is improved.
     * Implement as bounded batch aggregate queries, not a query per exercise or set.
     */
    List<PersonalRecord> findBestRecords(long userId, List<Long> exerciseIds,
                                        Long beforeEndedAtEpochMillis, long beforeWorkoutId);

    /** Best Epley estimate in KG from finished, completed working sets of 1..10 reps; zero if none. */
    double findBestEstimatedOneRepMax(long userId, long exerciseId);

    /** All finished performances of this exercise across routines, newest first and user scoped.
     * Combines repeated exercise rows per workout and includes only completed sets. */
    List<ExercisePerformance> findExerciseHistory(long userId, long exerciseId);
}
