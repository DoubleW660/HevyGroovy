package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.entity.enums.Unit;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Imports real history into isolated files and exercises rollback, receipts, and recovery. */
public class PersonalWorkoutHistoryImportTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String name = "personal-history-" + UUID.randomUUID() + ".db";

    @After public void removeIsolatedFile() { context.deleteDatabase(name); }

    @Test public void bundledHistoryPreservesActiveSessionAndRoundTripsDatesNotesPoundsAndAllSets() throws Exception {
        long activeId;
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name)) {
            CreateExerciseRequest exercise = new CreateExerciseRequest();
            exercise.setTitle("My ongoing exercise");
            exercise.setPrimaryMuscleGroup(MuscleGroup.CHEST);
            long exerciseId = storage.getExercises().createExercise(exercise).getExerciseId();
            activeId = storage.getWorkoutSessions().startEmptyWorkout(1).getId();
            storage.getWorkoutSessions().updateWorkoutDetails(1, activeId, "Keep this session", "Unsaved training");
            long row = storage.getWorkoutSessions().addExerciseToWorkout(1, activeId, exerciseId).getId();
            long set = storage.getSetEntries().addSet(1, row).getId();
            UpdateSetEntryRequest update = new UpdateSetEntryRequest();
            update.setWeight(222D); update.setUnit(Unit.LB); update.setReps(5); update.setCompleted(false);
            storage.getSetEntries().updateSet(1, set, update);
        }

        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name, true, 1, true)) {
            assertEquals(activeId, storage.getWorkoutSessions().getActiveWorkout(1).orElseThrow().getWorkoutId());
            assertEquals("Keep this session", storage.getWorkoutSessions().getActiveWorkout(1).orElseThrow().getNameSnapshot());
            assertEquals(222, storage.getWorkoutSessions().getActiveWorkout(1).orElseThrow()
                    .getExercises().get(0).getSets().get(0).getWeight(), 0D);
            List<WorkoutHistory> history = storage.getHistory().getWorkoutHistory(1, 100, 0);
            assertEquals(29, history.size());
            assertEquals(384, history.stream().mapToInt(WorkoutHistory::getTotalSets).sum());
            assertEquals(3423, history.stream().mapToInt(WorkoutHistory::getTotalReps).sum());
            assertEquals(205827.743715825, history.stream().mapToDouble(WorkoutHistory::getTotalVolume).sum(), 0.000001);
            assertEquals(16, history.get(0).getTotalSets());
            assertTrue(storage.getHistory().getWorkoutHistory(33).isEmpty());
        }

        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(29, count(db, "SELECT COUNT(*) FROM workout_import_entry WHERE user_id=1"));
            assertEquals(192, count(db, "SELECT COUNT(*) FROM workout_exercise we JOIN logged_workout w "
                    + "ON w.id=we.logged_workout_id WHERE w.ended_at IS NOT NULL"));
            assertEquals(384, count(db, "SELECT COUNT(*) FROM set_entry WHERE completed=1 AND unit='LB'"));
            assertEquals(34, count(db, "SELECT COUNT(*) FROM set_entry WHERE completed=1 AND set_type='WARMUP'"));
            assertEquals(14, count(db, "SELECT COUNT(*) FROM set_entry WHERE completed=1 AND set_type='DROP'"));
            assertEquals(89, count(db, "SELECT COUNT(*) FROM set_entry WHERE completed=1 AND set_type='FAILURE'"));
            assertEquals(5, count(db, "SELECT COUNT(*) FROM set_entry WHERE completed=1 AND reps=0"));
            assertEquals(29, count(db, "SELECT COUNT(*) FROM logged_workout WHERE ended_at IS NOT NULL AND template_id IS NOT NULL"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM logged_workout WHERE ended_at IS NOT NULL AND notes IS NOT NULL AND notes<>''"));
            assertEquals(5, count(db, "SELECT COUNT(*) FROM workout_exercise we JOIN logged_workout w ON w.id=we.logged_workout_id "
                    + "WHERE w.ended_at IS NOT NULL AND we.notes IS NOT NULL AND we.notes<>''"));
            JSONObject bundle;
            try (var input = context.getAssets().open("workout_history.json")) {
                bundle = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            JSONArray workouts = bundle.getJSONArray("workouts");
            for (int i = 0; i < workouts.length(); i++) {
                JSONObject source = workouts.getJSONObject(i);
                try (Cursor row = db.rawQuery("SELECT w.name_snapshot,w.started_at,w.ended_at,w.notes FROM logged_workout w "
                        + "JOIN workout_import_entry e ON e.workout_id=w.id WHERE e.user_id=1 AND e.source_key=?",
                        new String[]{source.getString("sourceKey")})) {
                    assertTrue(row.moveToFirst());
                    assertEquals(source.getString("title"), row.getString(0));
                    assertEquals(source.getLong("startedAtEpochMillis"), row.getLong(1));
                    assertEquals(source.getLong("endedAtEpochMillis"), row.getLong(2));
                    assertEquals(source.isNull("notes") ? null : source.getString("notes"), row.isNull(3) ? null : row.getString(3));
                }
            }
        }
        try (SQLiteAppStorage reopened = new SQLiteAppStorage(context, name, true, 1, true)) {
            assertEquals(29, reopened.getHistory().getWorkoutHistory(1, 100, 0).size());
            assertEquals(activeId, reopened.getWorkoutSessions().getActiveWorkout(1).orElseThrow().getWorkoutId());
        }
    }

    @Test public void receiptsPreserveEditsAndDeletedWorkoutsStayDeletedAcrossReimport() throws Exception {
        try (WorkoutDatabase helper = prepared()) {
            SQLiteDatabase db = helper.getWritableDatabase();
            seed(db, 7, fixture("first", "one", 20));
            db.execSQL("UPDATE logged_workout SET notes='Edited in app' WHERE user_id=7");
            seed(db, 7, fixture("first", "one", 999));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM logged_workout WHERE user_id=7"));
            assertEquals("Edited in app", text(db, "SELECT notes FROM logged_workout WHERE user_id=7"));
            assertEquals(20, number(db, "SELECT weight FROM set_entry"), 0D);
            db.execSQL("DELETE FROM set_entry");
            db.execSQL("DELETE FROM workout_exercise");
            db.execSQL("DELETE FROM logged_workout WHERE user_id=7");
            assertEquals(1, count(db, "SELECT COUNT(*) FROM workout_import_entry WHERE user_id=7 AND workout_id IS NULL"));
            db.execSQL("DELETE FROM exercise_catalog_entry WHERE catalog_key='one'");
            seed(db, 7, fixture("first", "one", 20));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM logged_workout"));
        }
    }

    @Test public void invalidLaterWorkoutAndUnknownExerciseRejectWholeBatchWithoutChangingOtherUsers() throws Exception {
        try (WorkoutDatabase helper = prepared()) {
            SQLiteDatabase db = helper.getWritableDatabase();
            JSONObject valid = fixture("good", "one", 20);
            seed(db, 2, valid);
            JSONObject batch = fixture("first", "one", 20);
            JSONObject invalid = fixture("second", "missing", 30).getJSONArray("workouts").getJSONObject(0);
            batch.getJSONArray("workouts").put(invalid);
            assertThrows(Exception.class, () -> seed(db, 1, batch));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM logged_workout WHERE user_id=1"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM workout_import_entry WHERE user_id=1"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM logged_workout WHERE user_id=2"));
            JSONObject malformed = fixture("bad", "one", 20);
            malformed.getJSONArray("workouts").getJSONObject(0).put("endedAtEpochMillis", 1);
            assertThrows(Exception.class, () -> seed(db, 1, malformed));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM logged_workout WHERE user_id=1"));
        }
    }

    @Test public void writeFailureRollsBackWorkoutsChildrenAndReceiptsAndRetrySucceeds() throws Exception {
        try (WorkoutDatabase helper = prepared()) {
            SQLiteDatabase db = helper.getWritableDatabase();
            JSONObject batch = fixture("first", "one", 20);
            batch.getJSONArray("workouts").put(fixture("second", "one", 30).getJSONArray("workouts").getJSONObject(0));
            db.execSQL("CREATE TRIGGER reject_history_set BEFORE INSERT ON set_entry WHEN NEW.weight=30 "
                    + "BEGIN SELECT RAISE(ABORT,'Simulated import write failure'); END");
            assertThrows(Exception.class, () -> seed(db, 1, batch));
            for (String table : List.of("logged_workout", "workout_exercise", "set_entry", "workout_import_entry")) {
                assertEquals(table, 0, count(db, "SELECT COUNT(*) FROM " + table));
            }
            db.execSQL("DROP TRIGGER reject_history_set");
            seed(db, 1, batch);
            assertEquals(2, count(db, "SELECT COUNT(*) FROM logged_workout"));
            assertEquals(2, count(db, "SELECT COUNT(*) FROM workout_import_entry"));
        }
    }

    @Test public void versionSixFileAddsImportLedgerWithoutChangingAnActiveWorkout() {
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO logged_workout(user_id,started_at,name_snapshot,notes) VALUES(1,100,'Existing','Keep me')");
            db.execSQL("DROP TABLE workout_import_entry");
            db.setVersion(6);
        }
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(7, db.getVersion());
            assertEquals("Keep me", text(db, "SELECT notes FROM logged_workout"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM logged_workout WHERE ended_at IS NULL"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM workout_import_entry"));
        }
    }

    private WorkoutDatabase prepared() {
        WorkoutDatabase helper = new WorkoutDatabase(context, name);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO exercise(id,title,primary_muscle) VALUES(1,'Imported exercise','CHEST')");
        db.execSQL("INSERT INTO exercise_catalog_entry(catalog_key,exercise_id) VALUES('one',1)");
        return helper;
    }

    private static JSONObject fixture(String key, String exerciseKey, double weight) throws Exception {
        JSONObject set = new JSONObject().put("setNumber", 1).put("weight", weight).put("unit", "LB")
                .put("reps", 8).put("setType", "NORMAL").put("rpe", JSONObject.NULL).put("restSeconds", JSONObject.NULL);
        JSONObject exercise = new JSONObject().put("exerciseKey", exerciseKey).put("nameSnapshot", "Imported exercise")
                .put("notes", JSONObject.NULL).put("sets", new JSONArray().put(set));
        JSONObject workout = new JSONObject().put("sourceKey", key).put("title", "Imported")
                .put("startedAtEpochMillis", key.equals("second") ? 3000 : 1000)
                .put("endedAtEpochMillis", key.equals("second") ? 4000 : 2000)
                .put("notes", JSONObject.NULL).put("templateKey", JSONObject.NULL)
                .put("exercises", new JSONArray().put(exercise));
        return new JSONObject().put("schemaVersion", 1).put("since", "2026-08-01")
                .put("sourceTimezone", "America/New_York").put("workouts", new JSONArray().put(workout));
    }

    private static void seed(SQLiteDatabase db, long userId, JSONObject json) throws Exception {
        try (var input = new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8))) {
            BundledWorkoutHistory.seed(db, userId, input);
        }
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getLong(0); }
    }
    private static double number(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getDouble(0); }
    }
    private static String text(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getString(0); }
    }
}

