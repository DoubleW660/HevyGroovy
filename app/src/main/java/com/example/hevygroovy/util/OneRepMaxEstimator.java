package com.example.hevygroovy.util;

import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;

/**
 * Epley estimate in KG: load * (1 + reps / 30), with a performed single using its actual load.
 * Restrict estimates to 1..10 reps; higher repetitions are deliberately not extrapolated.
 * This describes logged load only, without a bodyweight, RPE, or assisted-machine adjustment.
 */
public final class OneRepMaxEstimator {
    private OneRepMaxEstimator() { }

    /** Returns zero when the inputs do not support an estimate. */
    public static double estimateKg(double weight, Unit unit, int reps) {
        if (!Double.isFinite(weight) || weight <= 0 || unit == null || reps < 1 || reps > 10) return 0;
        double kg = weight * (unit == Unit.LB ? 0.45359237 : 1);
        double estimate = reps == 1 ? kg : kg * (1 + reps / 30.0);
        return Double.isFinite(estimate) ? estimate : 0;
    }

    /** Completed working sets qualify; warmups and unfinished sets never contribute. */
    public static double estimateKg(double weight, Unit unit, int reps, SetType type, boolean completed) {
        return !completed || type == null || type == SetType.WARMUP ? 0 : estimateKg(weight, unit, reps);
    }
}
