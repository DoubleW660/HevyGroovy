package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FinishedWorkoutDeletionTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String name = "finished-delete-" + UUID.randomUUID() + ".db";
    private SQLiteAppStorage storage;
    private long exercise;

    @Before public void open() {
        storage = new SQLiteAppStorage(context, name);
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle("Deletion bench"); request.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        exercise = storage.getExercises().createExercise(request).getExerciseId();
    }

    @After public void close() {
        if (storage != null) storage.close();
        context.deleteDatabase(name);
    }

    @Test public void deletingLatestFallsBackToEarlierRecordsAndPreviousSetsWithoutChangingActiveSession() {
        long baseline = finished(60, "Earlier notes");
        long latest = finished(100, "Later notes");
        long active = storage.getWorkoutSessions().startEmptyWorkout(1).getId();
        storage.getWorkoutSessions().addExerciseToWorkout(1, active, exercise);
        assertThrows(RuntimeException.class, () -> storage.getWorkoutSessions().deleteFinishedWorkout(2, latest));
        assertThrows(RuntimeException.class, () -> storage.getWorkoutSessions().deleteFinishedWorkout(1, active));

        storage.getWorkoutSessions().deleteFinishedWorkout(1, latest);

        assertEquals(1, storage.getHistory().getWorkoutHistory(1).size());
        assertEquals(baseline, storage.getHistory().getWorkoutHistory(1).get(0).getWorkoutId());
        assertEquals(60D, storage.getPersonalRecords().getExerciseRecords(1, exercise).stream()
                .filter(record -> record.getType() == RecordType.HEAVIEST_WEIGHT).findFirst().orElseThrow().getWeight(), 0D);
        assertEquals(60 * 0.45359237 * (1 + 5 / 30.0),
                storage.getPersonalRecords().getEstimatedOneRepMax(1, exercise), 0.000001);
        var session = storage.getWorkoutSessions().getActiveWorkout(1).orElseThrow();
        assertEquals(active, session.getWorkoutId());
        assertEquals(Long.valueOf(baseline), session.getExercises().get(0).getPreviousWorkoutId());
        assertEquals("Later notes", session.getExercises().get(0).getNotes());
        var added = storage.getWorkoutSessions().addExerciseToWorkout(1, active, exercise);
        assertEquals("Earlier notes", added.getNotes());
        assertThrows(RuntimeException.class, () -> storage.getHistory().getWorkoutHistoryDetail(1, latest));
        storage.close();
        storage = new SQLiteAppStorage(context, name);
        assertEquals(1, storage.getHistory().getWorkoutHistory(1).size());
    }

    @Test public void failedParentDeletionRollsBackAllChildDeletes() {
        long workout = finished(100, "Preserve");
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("CREATE TRIGGER reject_finished_delete BEFORE DELETE ON logged_workout "
                    + "BEGIN SELECT RAISE(ABORT,'Simulated delete failure'); END");
            assertThrows(RuntimeException.class, () -> storage.getWorkoutSessions().deleteFinishedWorkout(1, workout));
            assertEquals(1, storage.getHistory().getWorkoutHistoryDetail(1, workout).getTotalSets());
            assertEquals(1, count(db, "SELECT COUNT(*) FROM set_entry"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM workout_exercise"));
            db.execSQL("DROP TRIGGER reject_finished_delete");
        }
        storage.getWorkoutSessions().deleteFinishedWorkout(1, workout);
        assertTrue(storage.getHistory().getWorkoutHistory(1).isEmpty());
        assertTrue(storage.getPersonalRecords().getExerciseRecords(1, exercise).isEmpty());
    }

    @Test public void importedWorkoutDeletionLeavesReceiptAndIsNotRestoredOnReopen() {
        storage.close();
        storage = new SQLiteAppStorage(context, name, true, 1, true);
        assertEquals(29, storage.getHistory().getWorkoutHistory(1, 100, 0).size());
        long imported = storage.getHistory().getWorkoutHistory(1).get(0).getWorkoutId();
        storage.getWorkoutSessions().deleteFinishedWorkout(1, imported);
        storage.close();
        storage = new SQLiteAppStorage(context, name, true, 1, true);
        assertEquals(28, storage.getHistory().getWorkoutHistory(1, 100, 0).size());
        assertTrue(storage.getHistory().getWorkoutHistory(1, 100, 0).stream()
                .noneMatch(workout -> workout.getWorkoutId() == imported));
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            assertEquals(1, count(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM workout_import_entry WHERE user_id=1 AND workout_id IS NULL"));
        }
    }

    private long finished(double weight, String notes) {
        long workout = storage.getWorkoutSessions().startEmptyWorkout(1).getId();
        long row = storage.getWorkoutSessions().addExerciseToWorkout(1, workout, exercise).getId();
        storage.getWorkoutSessions().updateExerciseNotes(1, row, notes);
        long set = storage.getSetEntries().addSet(1, row).getId();
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setWeight(weight); request.setUnit(Unit.LB); request.setReps(5); request.setCompleted(true);
        storage.getSetEntries().updateSet(1, set, request);
        storage.getWorkoutSessions().finishWorkout(1, workout);
        return workout;
    }
    private long count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getLong(0); }
    }
}

