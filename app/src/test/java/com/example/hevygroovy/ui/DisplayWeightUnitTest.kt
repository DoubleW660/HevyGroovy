package com.example.hevygroovy.ui

import com.example.hevygroovy.dto.PersonalRecord
import com.example.hevygroovy.entity.enums.RecordType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DisplayWeightUnitTest {
    @Test fun normalizedMixedUnitTotalsConvertToPoundsWithoutChangingTheirStoredValue() {
        // 100 lb × 10 plus 20 kg × 5 = 1,220.462262 lb × reps.
        val kg = 100 * 10 * 0.45359237 + 20 * 5
        assertEquals(1220.46226218, kg.inWeightUnit(WeightUnit.LB), 0.000001)
        assertEquals(kg, kg.inWeightUnit(WeightUnit.KG))
        assertEquals(0.0, 0.0.inWeightUnit(WeightUnit.LB))
    }

    @Test fun volumeRecordsUseDisplayUnitWhileOriginalSetLoadsStayRecorded() {
        val record = PersonalRecord(RecordType.BEST_SET_VOLUME, 1, "Bench", 2, 3, 4,
            226.796185, 100.0, WeightUnit.LB, 5, 1000)
        assertEquals("500 lb × reps", record.recordValue(WeightUnit.LB))
        assertTrue(record.recordValue(WeightUnit.KG).endsWith(" kg × reps"))
        assertEquals(226.796185, record.value)
        assertEquals(100.0, record.weight)
        assertEquals(WeightUnit.LB, record.unit)
    }
}
