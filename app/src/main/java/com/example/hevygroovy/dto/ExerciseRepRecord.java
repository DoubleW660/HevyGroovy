package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.Unit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Heaviest completed working set for an exact repetition count, starting at five reps. */
public final class ExerciseRepRecord {
    private static final double EPSILON = 0.000001;
    private final int reps;
    private final double weightKg;
    private final double weight;
    private final Unit unit;
    private final long workoutId;
    private final long setId;
    private final long achievedAtEpochMillis;

    private ExerciseRepRecord(ExercisePerformance performance, ActiveSetEntry set) {
        reps = set.getReps();
        weightKg = ExercisePerformance.weightKg(set);
        weight = set.getWeight();
        unit = set.getUnit();
        workoutId = performance.getWorkoutId();
        setId = set.getId();
        achievedAtEpochMillis = performance.getEndedAtEpochMillis();
    }

    /** Ties retain the earliest achievement; set order breaks ties within the same workout. */
    public static List<ExerciseRepRecord> fromHistory(List<ExercisePerformance> history) {
        List<ExercisePerformance> chronological = new ArrayList<>(history);
        chronological.sort(Comparator.comparingLong(ExercisePerformance::getEndedAtEpochMillis)
                .thenComparingLong(ExercisePerformance::getWorkoutId));
        Map<Integer, ExerciseRepRecord> winners = new TreeMap<>();
        for (ExercisePerformance performance : chronological) {
            for (ActiveSetEntry set : performance.getSets()) {
                if (!ExercisePerformance.isWorkingSet(set) || set.getReps() < 5) continue;
                ExerciseRepRecord candidate = new ExerciseRepRecord(performance, set);
                ExerciseRepRecord previous = winners.get(candidate.reps);
                if (previous == null || candidate.weightKg > previous.weightKg + EPSILON) {
                    winners.put(candidate.reps, candidate);
                }
            }
        }
        return List.copyOf(winners.values());
    }

    public int getReps() { return reps; }
    public double getWeightKg() { return weightKg; }
    public double getWeight() { return weight; }
    public Unit getUnit() { return unit; }
    public long getWorkoutId() { return workoutId; }
    public long getSetId() { return setId; }
    public long getAchievedAtEpochMillis() { return achievedAtEpochMillis; }
}
