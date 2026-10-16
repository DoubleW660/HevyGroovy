package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.Unit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalRecordTest {
    @Test void legacyMissingSnapshotNamesHaveADisplayableFallback() {
        PersonalRecord row = new PersonalRecord(RecordType.MOST_REPS, 1, null, 2, 3, 4,
                8, 50, Unit.LB, 8, 1000);
        assertEquals("Exercise", row.getExerciseName());
    }
}
