package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.enums.*;
import com.example.hevygroovy.importer.HevyCsvParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class HevyCsvImportTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String name = "csv-import-" + UUID.randomUUID() + ".db";
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final String HEADER = "title,start_time,end_time,description,exercise_title,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n";
    @After public void cleanUp() { context.deleteDatabase(name); }
    private String row(String title, String date, String exercise, int index, String weight, String reps, String distance, String duration) {
        return title + ",\"" + date + ", 17:00\",\"" + date + ", 18:00\",Note," + exercise + "," + index + ",normal," + weight + "," + reps + "," + distance + "," + duration + ",\n";
    }
    private HistoryImportResult run(SQLiteDatabase db, long user, String csv) throws Exception {
        return new HevyHistoryImporter(db).importCsv(user, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), ZONE);
    }
    private long count(SQLiteDatabase db, String sql) { try (Cursor c = db.rawQuery(sql, null)) { assertTrue(c.moveToFirst()); return c.getLong(0); } }

    @Test public void addsOnlyMissingWorkoutsAndRetainsEditsDeletedReceiptsAndActiveWorkout() throws Exception {
        String first = row("Upper", "14 Oct 2026", "Bench", 0, "100", "5", "", "");
        String second = row("Run", "15 Oct 2026", "Running", 0, "", "", "2.5", "1200");
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO logged_workout(user_id,started_at,name_snapshot) VALUES(1,1,'Ongoing')");
            HistoryImportResult initial = run(db, 1, HEADER + first);
            assertEquals(1, initial.getImportedWorkouts()); assertEquals(1, initial.getCreatedExercises());
            db.execSQL("UPDATE logged_workout SET name_snapshot='Renamed',notes='Keep edits' WHERE ended_at IS NOT NULL");
            HistoryImportResult next = run(db, 1, HEADER + first + second);
            assertEquals(1, next.getImportedWorkouts()); assertEquals(1, next.getSkippedWorkouts());
            assertEquals(1, count(db, "SELECT COUNT(*) FROM logged_workout WHERE notes='Keep edits'"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM logged_workout WHERE ended_at IS NULL AND name_snapshot='Ongoing'"));
            db.execSQL("DELETE FROM set_entry WHERE workout_exercise_id IN (SELECT we.id FROM workout_exercise we JOIN logged_workout w ON w.id=we.logged_workout_id WHERE w.name_snapshot='Renamed')");
            db.execSQL("DELETE FROM workout_exercise WHERE logged_workout_id IN (SELECT id FROM logged_workout WHERE name_snapshot='Renamed')");
            db.execSQL("DELETE FROM logged_workout WHERE name_snapshot='Renamed'");
            assertEquals(1, count(db, "SELECT COUNT(*) FROM workout_import_entry WHERE workout_id IS NULL"));
            HistoryImportResult repeat = run(db, 1, HEADER + first + second);
            assertEquals(0, repeat.getImportedWorkouts()); assertEquals(2, repeat.getSkippedWorkouts());
            assertEquals(1, run(db, 2, HEADER + first).getImportedWorkouts());
        }
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name)) {
            var history = storage.getHistory().getWorkoutHistory(1);
            assertEquals(1, history.size());
            var detail = storage.getHistory().getWorkoutHistoryDetail(1, history.get(0).getWorkoutId());
            var set = detail.getExercises().get(0).getSets().get(0);
            assertEquals(4023.36, set.getDistanceMeters(), .00001); assertEquals(Integer.valueOf(1200), set.getDurationSeconds());
            long exercise = detail.getExercises().get(0).getExerciseId();
            assertEquals(TrackingType.DISTANCE_TIME, storage.getExercises().getExercise(exercise).getTrackingType());
            assertEquals(1200, storage.getPersonalRecords().getExerciseHistory(1, exercise).get(0).getSets().get(0).getDurationSeconds().intValue());
        }
    }
    @Test public void matchesUnreceiptedHistoryByMinuteAndExistingRenamedCatalogExercises() throws Exception {
        String csv = HEADER + row("Upper", "14 Oct 2026", "Bench", 0, "100", "5", "", "");
        var source = new HevyCsvParser().parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), ZONE).get(0);
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO logged_workout(user_id,name_snapshot,started_at,ended_at,notes) VALUES(1,'Upper',?,?, 'Keep native')", new Object[]{source.started + 1234, source.ended + 1234});
            assertEquals(1, run(db, 1, csv).getSkippedWorkouts());
            assertEquals(1, count(db, "SELECT COUNT(*) FROM workout_import_entry"));
            db.execSQL("INSERT INTO exercise(id,title,tracking_type) VALUES(1,'Renamed bench','WEIGHT_REPS')");
            db.execSQL("INSERT INTO exercise_catalog_entry(catalog_key,exercise_id) VALUES('personal/bench',1)");
            var result = run(db, 1, HEADER + row("Next", "15 Oct 2026", "Bench", 0, "120", "6", "", ""));
            assertEquals(0, result.getCreatedExercises());
            assertEquals(1, count(db, "SELECT exercise_id FROM workout_exercise"));
        }
    }
    @Test public void reusesBundledSourceKeysEvenWhenImportedWorkoutsWereDeleted() throws Exception {
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name, true, 1, true)) { }
        JSONObject original;
        try (var input = context.getAssets().open("workout_history.json")) {
            original = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("workouts").getJSONObject(0);
        }
        String csv = HEADER + original.getString("title") + "," +
                java.time.Instant.ofEpochMilli(original.getLong("startedAtEpochMillis")) + "," +
                java.time.Instant.ofEpochMilli(original.getLong("endedAtEpochMillis")) + ",,Unknown exercise,0,normal,10,5,,,\n";
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(1, run(db, 1, csv).getSkippedWorkouts());
            db.execSQL("DELETE FROM set_entry"); db.execSQL("DELETE FROM workout_exercise"); db.execSQL("DELETE FROM logged_workout");
            assertEquals(1, run(db, 1, csv).getSkippedWorkouts());
            assertEquals(0, count(db, "SELECT COUNT(*) FROM logged_workout"));
        }
    }
    @Test public void rollbackIncludesEarlierWorkoutsNewExercisesAndReceiptsAndRetrySucceeds() throws Exception {
        String csv = HEADER + row("Upper", "14 Oct 2026", "Bench", 0, "10", "5", "", "") +
                row("Lower", "15 Oct 2026", "Squat", 0, "30", "5", "", "");
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("CREATE TRIGGER reject_csv BEFORE INSERT ON set_entry WHEN NEW.weight=30 BEGIN SELECT RAISE(ABORT,'Simulated failure'); END");
            assertThrows(Exception.class, () -> run(db, 1, csv));
            for (String table : new String[]{"logged_workout", "exercise", "workout_exercise", "set_entry", "workout_import_entry"})
                assertEquals(table, 0, count(db, "SELECT COUNT(*) FROM " + table));
            db.execSQL("DROP TRIGGER reject_csv");
            assertEquals(2, run(db, 1, csv).getImportedWorkouts());
            assertEquals(2, run(db, 1, csv).getSkippedWorkouts());
        }
    }
    @Test public void exerciseSettingsAndMeasurementsRoundTripAcrossReopenAndSeedNextRoutine() throws Exception {
        long exercise, template;
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name)) {
            var request = new CreateExerciseRequest(); request.setTitle("Carry"); request.setPrimaryMuscleGroup(MuscleGroup.OTHER);
            request.setTrackingType(TrackingType.WEIGHT_TIME); request.setUnilateral(true);
            exercise = storage.getExercises().createExercise(request).getExerciseId();
            var routine = new CreateTemplateRequest(); routine.setTitle("Carry day");
            template = storage.getTemplates().createTemplate(1, routine).getTemplateWorkoutId();
            var addExercise = new AddTemplateExerciseRequest(); addExercise.setTemplateId(template);
            addExercise.setExerciseId(exercise); addExercise.setRestTimeSeconds(60);
            storage.getTemplates().addExerciseToTemplate(1, addExercise);
            long workout = storage.getWorkoutSessions().startWorkoutFromTemplate(1, template).getId();
            var active = storage.getWorkoutSessions().getActiveWorkout(1).orElseThrow();
            long row = active.getExercises().get(0).getWorkoutExerciseId();
            long set = storage.getSetEntries().addSet(1, row).getId();
            var update = new UpdateSetEntryRequest(); update.setWeight(45D); update.setDurationSeconds(90); update.setDistanceMeters(25.5); update.setCompleted(true);
            storage.getSetEntries().updateSet(1, set, update); storage.getWorkoutSessions().finishWorkout(1, workout);
        }
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name)) {
            assertEquals(TrackingType.WEIGHT_TIME, storage.getExercises().getExercise(exercise).getTrackingType());
            storage.getWorkoutSessions().startWorkoutFromTemplate(1, template);
            var row = storage.getWorkoutSessions().getActiveWorkout(1).orElseThrow().getExercises().get(0);
            assertEquals(TrackingType.WEIGHT_TIME, row.getTrackingType()); assertTrue(row.isUnilateral());
            assertEquals(1, row.getSets().size()); assertFalse(row.getSets().get(0).isCompleted());
            assertEquals(Integer.valueOf(90), row.getSets().get(0).getDurationSeconds());
            assertEquals(25.5, row.getSets().get(0).getDistanceMeters(), 0);
        }
    }
}
