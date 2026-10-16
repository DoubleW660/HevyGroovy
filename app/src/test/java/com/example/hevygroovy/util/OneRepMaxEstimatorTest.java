package com.example.hevygroovy.util;

import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OneRepMaxEstimatorTest {
    @Test
    void usesEpleyForTwoToTenRepsAndActualLoadForSingles() {
        assertEquals(100, OneRepMaxEstimator.estimateKg(100, Unit.KG, 1), 0.000001);
        assertEquals(116.6666667, OneRepMaxEstimator.estimateKg(100, Unit.KG, 5), 0.000001);
        assertEquals(133.3333333, OneRepMaxEstimator.estimateKg(100, Unit.KG, 10), 0.000001);
    }

    @Test
    void conversionProducesSameKgEstimateForEquivalentLoads() {
        assertEquals(OneRepMaxEstimator.estimateKg(45.359237, Unit.KG, 8),
                OneRepMaxEstimator.estimateKg(100, Unit.LB, 8), 0.000001);
    }

    @Test
    void refusesHighRepsZeroLoadsAndInvalidInputs() {
        assertEquals(0, OneRepMaxEstimator.estimateKg(100, Unit.KG, 11));
        assertEquals(0, OneRepMaxEstimator.estimateKg(100, Unit.KG, 0));
        assertEquals(0, OneRepMaxEstimator.estimateKg(0, Unit.KG, 5));
        assertEquals(0, OneRepMaxEstimator.estimateKg(-1, Unit.KG, 5));
        assertEquals(0, OneRepMaxEstimator.estimateKg(Double.NaN, Unit.KG, 5));
        assertEquals(0, OneRepMaxEstimator.estimateKg(Double.POSITIVE_INFINITY, Unit.KG, 5));
        assertEquals(0, OneRepMaxEstimator.estimateKg(100, null, 5));
        assertEquals(0, OneRepMaxEstimator.estimateKg(Double.MAX_VALUE, Unit.KG, 10));
    }

    @Test
    void excludesWarmupsAndIncompleteSetsButAcceptsFailureAndDropSets() {
        assertEquals(0, OneRepMaxEstimator.estimateKg(100, Unit.KG, 5, SetType.WARMUP, true));
        assertEquals(0, OneRepMaxEstimator.estimateKg(100, Unit.KG, 5, SetType.NORMAL, false));
        assertEquals(0, OneRepMaxEstimator.estimateKg(100, Unit.KG, 5, null, true));
        assertEquals(116.6666667,
                OneRepMaxEstimator.estimateKg(100, Unit.KG, 5, SetType.FAILURE, true), 0.000001);
        assertEquals(116.6666667,
                OneRepMaxEstimator.estimateKg(100, Unit.KG, 5, SetType.DROP, true), 0.000001);
    }
}
