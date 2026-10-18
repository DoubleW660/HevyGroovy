package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.util.OneRepMaxEstimator;
import java.util.List;
import java.util.stream.Collectors;

/** One exercise's immutable completed performance, combining duplicate rows within a workout. */
public final class ExercisePerformance {
    private final long workoutId;
    private final long exerciseId;
    private final String exerciseName;
    private final String workoutName;
    private final long startedAtEpochMillis;
    private final long endedAtEpochMillis;
    private final List<ActiveSetEntry> sets;
    private final String notes;
    private final boolean unilateral;
    private final double heaviestWeightKg;
    private final double estimatedOneRepMaxKg;
    private final double bestSetVolumeKg;
    private final double sessionVolumeKg;
    private final int totalReps;

    public ExercisePerformance(long workoutId, long exerciseId, String exerciseName, String workoutName,
                               long startedAtEpochMillis, long endedAtEpochMillis, List<SetEntry> sets,
                               String notes, boolean unilateral) {
        this.workoutId = workoutId;
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName == null || exerciseName.isBlank() ? "Exercise" : exerciseName;
        this.workoutName = workoutName == null || workoutName.isBlank() ? "Workout" : workoutName;
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.sets = sets.stream().filter(SetEntry::isCompleted).map(ActiveSetEntry::new)
                .collect(Collectors.toUnmodifiableList());
        this.notes = notes;
        this.unilateral = unilateral;
        double heaviest = 0, estimated = 0, bestSet = 0, volume = 0;
        int reps = 0;
        for (ActiveSetEntry set : this.sets) {
            if (!isWorkingSet(set)) continue;
            double kg = weightKg(set);
            double setVolume = kg * set.getReps() * (unilateral ? 2 : 1);
            if (!Double.isFinite(setVolume)) throw new IllegalStateException("Exercise volume must be finite");
            heaviest = Math.max(heaviest, kg);
            estimated = Math.max(estimated, OneRepMaxEstimator.estimateKg(set.getWeight(),
                    set.getUnit(), set.getReps()));
            bestSet = Math.max(bestSet, setVolume);
            volume += setVolume;
            reps = Math.addExact(reps, set.getReps());
        }
        if (!Double.isFinite(volume)) throw new IllegalStateException("Exercise volume must be finite");
        heaviestWeightKg = heaviest;
        estimatedOneRepMaxKg = estimated;
        bestSetVolumeKg = bestSet;
        sessionVolumeKg = volume;
        totalReps = reps;
    }

    static boolean isWorkingSet(ActiveSetEntry set) {
        return set.isCompleted() && set.getReps() > 0 && set.getSetType() != null
                && set.getSetType() != SetType.WARMUP;
    }

    static double weightKg(ActiveSetEntry set) {
        if (set.getUnit() == null || !Double.isFinite(set.getWeight()) || set.getWeight() < 0) {
            throw new IllegalStateException("Exercise history requires a valid weight and unit");
        }
        return set.getWeight() * (set.getUnit() == Unit.LB ? 0.45359237 : 1);
    }

    public long getWorkoutId() { return workoutId; }
    public long getExerciseId() { return exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public String getWorkoutName() { return workoutName; }
    public long getStartedAtEpochMillis() { return startedAtEpochMillis; }
    public long getEndedAtEpochMillis() { return endedAtEpochMillis; }
    /** Includes completed warmups; working sets alone contribute to graph metrics and records. */
    public List<ActiveSetEntry> getSets() { return sets; }
    public String getNotes() { return notes; }
    public boolean isUnilateral() { return unilateral; }
    public double getHeaviestWeightKg() { return heaviestWeightKg; }
    public double getEstimatedOneRepMaxKg() { return estimatedOneRepMaxKg; }
    public double getBestSetVolumeKg() { return bestSetVolumeKg; }
    public double getSessionVolumeKg() { return sessionVolumeKg; }
    /** Logged reps remain per side for unilateral exercises, consistent with workout records. */
    public int getTotalReps() { return totalReps; }
}
