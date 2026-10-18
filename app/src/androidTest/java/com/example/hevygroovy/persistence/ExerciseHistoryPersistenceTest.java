package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.ExercisePerformance;
import com.example.hevygroovy.dto.ExerciseRepRecord;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the actual single-exercise history query against an isolated SQLite database. */
@RunWith(AndroidJUnit4.class)
public class ExerciseHistoryPersistenceTest {
    private SQLiteDatabase db;
    private SQLiteLoggedWorkoutRepository workouts;
    private SQLiteWorkoutExerciseRepository exercises;
    private SQLiteSetEntryRepository sets;
    private SQLitePersonalRecordRepository records;

    @Before public void openIsolatedDatabase() {
        db = SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        WorkoutDatabase schema = new WorkoutDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try { schema.onCreate(db); } finally { schema.close(); }
        workouts = new SQLiteLoggedWorkoutRepository(db);
        exercises = new SQLiteWorkoutExerciseRepository(db);
        sets = new SQLiteSetEntryRepository(db);
        records = new SQLitePersonalRecordRepository(db);
    }

    @After public void closeIsolatedDatabase() { if (db != null) db.close(); }

    @Test public void historySpansTemplatesButNeverIncludesAnotherUserActiveOrUncompletedExercise() {
        long first = workout(1, 1100L, 10L);
        long firstSet = set(row(first, 9, 0, "First notes"), 1, 40, Unit.KG, 5, SetType.NORMAL, true);
        long second = workout(1, 2100L, 20L);
        set(row(second, 9, 0, null), 1, 60, Unit.KG, 5, SetType.NORMAL, true);
        long warmupOnly = workout(1, 3100L, null);
        set(row(warmupOnly, 9, 0, null), 1, 20, Unit.KG, 10, SetType.WARMUP, true);
        long unrelated = workout(1, 4100L, null);
        set(row(unrelated, 10, 0, null), 1, 999, Unit.KG, 5, SetType.NORMAL, true);
        set(row(unrelated, 9, 1, null), 1, 999, Unit.KG, 5, SetType.NORMAL, false);
        long other = workout(2, 5100L, null);
        set(row(other, 9, 0, null), 1, 999, Unit.KG, 5, SetType.NORMAL, true);
        long active = workout(1, null, null);
        set(row(active, 9, 0, null), 1, 999, Unit.KG, 5, SetType.NORMAL, true);

        List<ExercisePerformance> history = records.findExerciseHistory(1, 9);
        assertEquals(3, history.size());
        assertEquals(warmupOnly, history.get(0).getWorkoutId());
        assertEquals(second, history.get(1).getWorkoutId());
        assertEquals(first, history.get(2).getWorkoutId());
        assertEquals(firstSet, history.get(2).getSets().get(0).getId());
        assertEquals("Session " + second, history.get(1).getWorkoutName());
        assertEquals("First notes", history.get(2).getNotes());
        assertEquals(0, history.get(0).getTotalReps());
        assertEquals(60, ExerciseRepRecord.fromHistory(history).get(0).getWeightKg(), 0D);
        assertTrue(records.findExerciseHistory(3, 9).isEmpty());
        assertTrue(records.findExerciseHistory(1, 999).isEmpty());
    }

    @Test public void duplicateRowsAggregateOncePreserveSetMetadataAndCountBothSidesForVolume() {
        db.execSQL("INSERT INTO exercise(id,title,unilateral) VALUES(9,'Single leg',1)");
        long finished = workout(1, 2000L, 10L);
        long firstRow = row(finished, 9, 0, "First note");
        set(firstRow, 1, 999, Unit.KG, 10, SetType.NORMAL, false);
        set(firstRow, 3, 100, Unit.LB, 5, SetType.FAILURE, true);
        long warmup = set(firstRow, 2, 20, Unit.KG, 10, SetType.WARMUP, true);
        long secondRow = row(finished, 9, 1, "Second note");
        long drop = set(secondRow, 1, 50, Unit.KG, 6, SetType.DROP, true);
        List<ExercisePerformance> history = records.findExerciseHistory(1, 9);
        assertEquals(1, history.size());
        ExercisePerformance result = history.get(0);
        assertEquals(3, result.getSets().size());
        assertEquals(warmup, result.getSets().get(0).getId());
        assertEquals(drop, result.getSets().get(2).getId());
        assertEquals(Unit.LB, result.getSets().get(1).getUnit());
        assertEquals(45, (int) result.getSets().get(1).getRestSeconds());
        assertEquals(8.5f, result.getSets().get(1).getRpe(), 0f);
        assertEquals("First note\n\nSecond note", result.getNotes());
        assertTrue(result.isUnilateral());
        assertEquals(11, result.getTotalReps());
        assertEquals(50, result.getHeaviestWeightKg(), 0.000001);
        assertEquals(600, result.getBestSetVolumeKg(), 0.000001);
        assertEquals(600 + 100 * 0.45359237 * 5 * 2, result.getSessionVolumeKg(), 0.000001);
        assertThrows(UnsupportedOperationException.class, history::clear);
    }

    @Test public void equalFinishTimesSortByWorkoutIdAndRepRecordTieKeepsEarliestWorkout() {
        long first = workout(1, 2000L, 10L);
        set(row(first, 9, 0, null), 1, 100, Unit.KG, 5, SetType.NORMAL, true);
        long second = workout(1, 2000L, 20L);
        set(row(second, 9, 0, null), 1, 100 / 0.45359237, Unit.LB, 5, SetType.NORMAL, true);
        List<ExercisePerformance> history = records.findExerciseHistory(1, 9);
        assertEquals(second, history.get(0).getWorkoutId());
        assertEquals(first, ExerciseRepRecord.fromHistory(history).get(0).getWorkoutId());
    }

    private long workout(long user, Long ended, Long template) {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(user); workout.setStartedAtEpochMillis(100); workout.setEndedAtEpochMillis(ended);
        workout.setTemplateId(template); workouts.save(workout);
        workout.setNameSnapshot("Session " + workout.getId()); workouts.save(workout);
        return workout.getId();
    }

    private long row(long workout, long exercise, int order, String notes) {
        WorkoutExercise row = new WorkoutExercise();
        row.setLoggedWorkoutId(workout); row.setExerciseId(exercise); row.setOrderIndex(order);
        row.setExerciseNameSnapshot("Exercise " + exercise); row.setNotes(notes);
        return exercises.save(row).getId();
    }

    private long set(long row, int number, double weight, Unit unit, int reps, SetType type, boolean completed) {
        SetEntry set = new SetEntry();
        set.setWorkoutExerciseId(row); set.setSetNumber(number); set.setWeight(weight); set.setUnit(unit);
        set.setReps(reps); set.setSetType(type); set.setCompleted(completed); set.setCreatedAtEpochMillis(100);
        set.setRestSeconds(45); set.setRpe(8.5f);
        return sets.save(set).getId();
    }
}
