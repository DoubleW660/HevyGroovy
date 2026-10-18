package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExercisePerformanceTest {
    @Test void metricsUseWorkingSetsNormalizeWeightAndDoubleOnlyUnilateralVolume() {
        SetEntry warmup = set(1, 500, Unit.KG, 30);
        warmup.setSetType(SetType.WARMUP);
        SetEntry incomplete = set(2, 999, Unit.KG, 10);
        incomplete.setCompleted(false);
        SetEntry zeroReps = set(3, 999, Unit.KG, 0);
        ExercisePerformance result = performance(7, 1000, true, List.of(warmup, incomplete, zeroReps,
                set(4, 100, Unit.LB, 5), set(5, 50, Unit.KG, 10), set(6, 0, Unit.KG, 20)));

        assertEquals(5, result.getSets().size()); // history retains completed warmups/zero-rep rows
        assertEquals(50, result.getHeaviestWeightKg(), 0.000001);
        assertEquals(50 * (1 + 10 / 30.0), result.getEstimatedOneRepMaxKg(), 0.000001);
        assertEquals(1000, result.getBestSetVolumeKg(), 0.000001);
        assertEquals(1000 + 100 * 0.45359237 * 5 * 2, result.getSessionVolumeKg(), 0.000001);
        assertEquals(35, result.getTotalReps()); // per-side reps, matching existing records
    }

    @Test void snapshotsAreImmutableAndUseDisplayableLegacyNames() {
        SetEntry set = set(1, 80, Unit.KG, 8);
        List<SetEntry> source = new ArrayList<>(List.of(set));
        ExercisePerformance result = new ExercisePerformance(7, 9, null, " ", 100, 200,
                source, "Saved note", false);
        source.clear();
        set.setWeight(999);
        assertEquals("Exercise", result.getExerciseName());
        assertEquals("Workout", result.getWorkoutName());
        assertEquals(80, result.getSets().get(0).getWeight());
        assertEquals(640, result.getSessionVolumeKg());
        assertThrows(UnsupportedOperationException.class, () -> result.getSets().clear());
    }

    @Test void exactRepRecordsStartAtFiveIncludeHigherCountsAndKeepEarliestEquivalentWeights() {
        SetEntry warmup = set(1, 999, Unit.KG, 6);
        warmup.setSetType(SetType.WARMUP);
        ExercisePerformance latest = performance(8, 2000, false, List.of(
                set(80, 100 / 0.45359237, Unit.LB, 5), set(81, 70, Unit.KG, 6),
                set(82, 30, Unit.KG, 50), set(83, 999, Unit.KG, 4), warmup));
        ExercisePerformance earliest = performance(7, 1000, false, List.of(
                set(70, 100, Unit.KG, 5), set(71, 0, Unit.KG, 20)));
        List<ExerciseRepRecord> result = ExerciseRepRecord.fromHistory(List.of(latest, earliest));
        assertEquals(List.of(5, 6, 20, 50), result.stream().map(ExerciseRepRecord::getReps)
                .collect(java.util.stream.Collectors.toList()));
        assertEquals(7, result.get(0).getWorkoutId());
        assertEquals(70, result.get(0).getSetId());
        assertEquals(1000, result.get(0).getAchievedAtEpochMillis());
        assertEquals(Unit.KG, result.get(0).getUnit());
        assertEquals(70, result.get(1).getWeightKg(), 0.000001);
        assertEquals(0, result.get(2).getWeightKg(), 0.000001);
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test void repRecordsPreferHigherWeightAndBreakChronologyTiesByWorkoutThenSetOrder() {
        ExercisePerformance laterId = performance(8, 1000, false, List.of(set(80, 60, Unit.KG, 5)));
        ExercisePerformance first = performance(7, 1000, false, List.of(
                set(71, 60, Unit.KG, 5), set(70, 60, Unit.KG, 5)));
        ExercisePerformance improvement = performance(9, 2000, false, List.of(set(90, 61, Unit.KG, 5)));
        assertEquals(71, ExerciseRepRecord.fromHistory(List.of(laterId, first)).get(0).getSetId());
        assertEquals(90, ExerciseRepRecord.fromHistory(List.of(improvement, laterId, first)).get(0).getSetId());
    }

    private ExercisePerformance performance(long workout, long ended, boolean unilateral, List<SetEntry> sets) {
        return new ExercisePerformance(workout, 9, "Bench", "Routine", 100, ended, sets, null, unilateral);
    }

    private SetEntry set(long id, double weight, Unit unit, int reps) {
        SetEntry set = new SetEntry();
        set.setId(id); set.setSetNumber((int) id); set.setWeight(weight); set.setUnit(unit);
        set.setReps(reps); set.setSetType(SetType.NORMAL); set.setCompleted(true);
        return set;
    }
}
