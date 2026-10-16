package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.UpdateExerciseRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import org.json.JSONException;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BundledCatalogTest {
    private Context context;
    private String name;
    private WorkoutDatabase helper;
    private SQLiteDatabase db;

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "catalog-" + UUID.randomUUID() + ".db";
        helper = new WorkoutDatabase(context, name);
        db = helper.getWritableDatabase();
    }
    @After public void cleanup() { helper.close(); context.deleteDatabase(name); }

    @Test public void bundledImportCreatesOnlyRecentExercisesAndFourLatestRoutines() {
        helper.close();
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name, true, 7L)) {
            assertEquals(48, storage.getExercises().listExercises(null, true).size());
            assertEquals(4, storage.getTemplates().listTemplates(7L, true).size());
            assertTrue(storage.getTemplates().listTemplates(1L, true).isEmpty());
            assertTrue(storage.getHistory().getWorkoutHistory(7L).isEmpty());
            assertFalse(storage.getWorkoutSessions().getActiveWorkout(7L).isPresent());
            assertTrue(storage.getExercises().listExercises("Ab Wheel", true).isEmpty());
            var custom = storage.getExercises().listExercises("African Curls", true).get(0);
            assertEquals("", custom.getDescription());
            assertEquals("BICEPS", custom.getPrimaryMuscleGroup().name());
            var guide = storage.getExercises().listExercises("Leg Press (Machine)", true).stream()
                    .filter(row -> row.getTitle().equals("Leg Press (Machine)")).findFirst().get();
            assertTrue(guide.getDescription().contains("leg press machine"));
            for (var template : storage.getTemplates().listTemplates(7L, true)) {
                var detail = storage.getTemplates().getTemplateDetail(7L, template.getTemplateWorkoutId());
                List<String> titles = new ArrayList<>();
                detail.getExercises().forEach(row -> titles.add(row.getExerciseTitle()));
                if (template.getTitle().equals("Lower 1")) {
                    assertEquals(Arrays.asList("Leg Press (Machine)", "Seated Leg Curl (Machine)",
                            "Single Leg Press (Machine)", "African Curls", "Seated Calf Raise", "Crunch (Machine)",
                            "Bicep Curl (Machine)", "Preacher Curl (Machine)"), titles);
                    assertTrue(detail.getDescription().contains("14 Oct 2026"));
                } else if (template.getTitle().equals("Upper 2")) {
                    assertEquals(Arrays.asList("Lat Pulldown (Machine)", "Seated Row (Machine)",
                            "Triceps Extension (Machine)", "Iso-Lateral Chest Press (Machine)",
                            "Butterfly (Pec Deck)", "Seated Shoulder Press (Machine)", "Crunch (Machine)"), titles);
                    assertTrue(detail.getDescription().contains("9 Oct 2026"));
                }
            }
        }
    }

    @Test public void reopeningKeepsRenamesDescriptionsArchivesAndRoutineEdits() {
        helper.close();
        long exerciseId, templateId;
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name, true)) {
            exerciseId = storage.getExercises().listExercises("African Curls", true).get(0).getExerciseId();
            UpdateExerciseRequest edit = new UpdateExerciseRequest();
            edit.setTitle("My wrist curl"); edit.setDescription("My own setup");
            storage.getExercises().updateExercise(exerciseId, edit);
            storage.getExercises().archiveExercise(exerciseId);
            templateId = storage.getTemplates().listTemplates(1L, true).get(0).getTemplateWorkoutId();
            UpdateTemplateRequest routine = new UpdateTemplateRequest(); routine.setTitle("My edited routine");
            storage.getTemplates().updateTemplate(1L, templateId, routine);
            storage.getTemplates().archiveTemplate(1L, templateId);
        }
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name, true)) {
            assertEquals(48, storage.getExercises().listExercises(null, true).size());
            assertEquals(4, storage.getTemplates().listTemplates(1L, true).size());
            var exercise = storage.getExercises().getExercise(exerciseId);
            assertEquals("My wrist curl", exercise.getTitle()); assertEquals("My own setup", exercise.getDescription());
            assertTrue(exercise.isArchived());
            var template = storage.getTemplates().getTemplateDetail(1L, templateId);
            assertEquals("My edited routine", template.getTitle()); assertTrue(template.isArchived());
        }
    }

    @Test public void existingArchivedTitleIsReusedAndSharedSourceVariantsStayDistinct() throws Exception {
        db.execSQL("INSERT INTO exercise(id,title,description,archived) VALUES(1,'  My   Curl  ','Keep this',1),(2,'MY CURL','Duplicate',0)");
        exercises(entry("curl", "my curl", "shared") + "," + entry("weighted", "My curl (Weighted)", "shared"));
        assertEquals(3, count("exercise")); assertEquals(2, count("exercise_catalog_entry"));
        try (Cursor row = db.rawQuery("SELECT exercise_id FROM exercise_catalog_entry WHERE catalog_key='curl'", null)) {
            assertTrue(row.moveToFirst()); assertEquals(1L, row.getLong(0));
        }
        try (Cursor row = db.rawQuery("SELECT description,archived FROM exercise WHERE id=1", null)) {
            assertTrue(row.moveToFirst()); assertEquals("Keep this", row.getString(0)); assertEquals(1, row.getInt(1));
        }
    }

    @Test public void invalidCatalogAndDuplicateKeysCannotPartiallyImport() throws Exception {
        String first = entry("one", "One", null);
        assertThrows(JSONException.class, () -> exercises(first + "," + entry("two", "Two", null).replace("BICEPS", "UNKNOWN")));
        assertEquals(0, count("exercise"));
        assertThrows(JSONException.class, () -> exercises(first + "," + first));
        assertEquals(0, count("exercise_catalog_entry"));
    }

    @Test public void databaseFailureRollsBackExercisesAndMarkersTogether() {
        db.execSQL("CREATE TRIGGER reject_second BEFORE INSERT ON exercise_catalog_entry "
                + "WHEN NEW.catalog_key='two' BEGIN SELECT RAISE(ABORT,'test failure'); END");
        assertThrows(android.database.SQLException.class, () -> exercises(entry("one", "One", null) + "," + entry("two", "Two", null)));
        assertEquals(0, count("exercise")); assertEquals(0, count("exercise_catalog_entry"));
    }

    @Test public void invalidRoutineReferenceCannotPartiallyCreateRoutines() throws Exception {
        exercises(entry("one", "One", null));
        assertThrows(JSONException.class, () -> routines(1L, routine("valid", "Valid", "one") + "," + routine("invalid", "Invalid", "missing")));
        assertEquals(0, count("template_workout")); assertEquals(0, count("template_exercise"));
    }

    @Test public void routineImportIsUserScopedAndPreservesExistingArchivedStructure() throws Exception {
        exercises(entry("one", "One", null));
        db.execSQL("INSERT INTO template_workout(id,user_id,title,description,created_at,archived) VALUES(1,7,'  My Routine  ','Keep me',1,1)");
        routines(7L, routine("routine", "my routine", "one"));
        routines(8L, routine("routine", "my routine", "one"));
        routines(8L, routine("routine", "my routine", "one"));
        assertEquals(2, count("template_workout")); assertEquals(1, count("template_exercise"));
        assertEquals(2, count("template_catalog_entry"));
        try (Cursor row = db.rawQuery("SELECT description,archived FROM template_workout WHERE id=1", null)) {
            assertTrue(row.moveToFirst()); assertEquals("Keep me", row.getString(0)); assertEquals(1, row.getInt(1));
        }
    }

    @Test public void namedStorageRemainsEmptyWithoutImportOptIn() {
        helper.close();
        try (SQLiteAppStorage storage = new SQLiteAppStorage(context, name)) {
            assertTrue(storage.getExercises().listExercises(null, true).isEmpty());
            assertTrue(storage.getTemplates().listTemplates(1L, true).isEmpty());
        }
    }

    private void exercises(String entries) throws Exception {
        try (var input = input("{\"schemaVersion\":1,\"exercises\":[" + entries + "]}")) {
            BundledExerciseCatalog.seed(db, input);
        }
    }
    private void routines(long userId, String entries) throws Exception {
        try (var input = input("{\"schemaVersion\":1,\"templates\":[" + entries + "]}")) {
            BundledTemplateCatalog.seed(db, userId, input);
        }
    }
    private ByteArrayInputStream input(String json) { return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)); }
    private String entry(String key, String title, String source) {
        return "{\"catalogKey\":\"" + key + "\",\"title\":\"" + title + "\",\"description\":\"Imported\","
                + "\"primaryMuscle\":\"BICEPS\",\"secondaryMuscles\":[],\"sourceId\":" + (source == null ? "null" : "\"" + source + "\"") + "}";
    }
    private String routine(String key, String title, String exercise) {
        return "{\"catalogKey\":\"" + key + "\",\"title\":\"" + title + "\",\"exercises\":[{\"exerciseKey\":\"" + exercise + "\"}]}";
    }
    private int count(String table) {
        try (Cursor row = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) { row.moveToFirst(); return row.getInt(0); }
    }
}
