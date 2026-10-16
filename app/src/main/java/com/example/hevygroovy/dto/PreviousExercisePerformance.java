package com.example.hevygroovy.dto;

import java.util.List;
import java.util.stream.Collectors;

/** Latest finished performance, retaining separate repeated exercise rows in workout order. */
public final class PreviousExercisePerformance {
    private final long workoutId;
    private final long endedAtEpochMillis;
    private final List<List<ActiveSetEntry>> setsByOccurrence;

    public PreviousExercisePerformance(long workoutId, long endedAtEpochMillis,
                                       List<List<ActiveSetEntry>> setsByOccurrence) {
        this.workoutId = workoutId;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.setsByOccurrence = setsByOccurrence.stream().map(List::copyOf)
                .collect(Collectors.toUnmodifiableList());
    }

    public long getWorkoutId() { return workoutId; }
    public long getEndedAtEpochMillis() { return endedAtEpochMillis; }
    public List<List<ActiveSetEntry>> getSetsByOccurrence() { return setsByOccurrence; }
}
