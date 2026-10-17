package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

/** Opens real versioned files through SQLiteOpenHelper, rather than calling onUpgrade directly. */
@RunWith(AndroidJUnit4.class)
public class SQLiteFileMigrationTest {
    private Context context;
    private String name;
    private WorkoutDatabase helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "migration-" + UUID.randomUUID() + ".db";
    }

    @After
    public void tearDown() {
        if (helper != null) { helper.close(); }
        context.deleteDatabase(name);
    }

    @Test
    public void versionOneFileMigratesWithoutCompletingActivePlaceholders() { verifyLegacyMigration(1); }

    @Test
    public void versionTwoFileMigratesWithoutCompletingActivePlaceholders() { verifyLegacyMigration(2); }

    @Test
    public void versionThreePreservesExplicitCompletionAndFixesDefault() {
        prepareLegacyFile(3);
        SQLiteDatabase db = openCurrent();
        assertEquals(0, completed(db, 1));
        assertEquals(1, completed(db, 2));
        assertEquals("0", completionDefault(db));
        assertEquals(7, db.getVersion());
    }

    @Test
    public void freshAndUpgradedSchemasHaveSameCompletionDefault() {
        SQLiteDatabase fresh = openCurrent();
        assertEquals("0", completionDefault(fresh));
        helper.close();
        context.deleteDatabase(name);
        prepareLegacyFile(2);
        assertEquals("0", completionDefault(openCurrent()));
    }

    private void verifyLegacyMigration(int version) {
        prepareLegacyFile(version);
        SQLiteDatabase db = openCurrent();
        assertEquals(0, completed(db, 1)); // Active zero-rep placeholder.
        assertEquals(1, completed(db, 2)); // Active recorded reps, including bodyweight.
        assertEquals(1, completed(db, 3)); // Preserve finished zero-rep history.
        assertEquals("0", completionDefault(db));
        assertTrue(db.isWriteAheadLoggingEnabled());
        try (Cursor check = db.rawQuery("PRAGMA foreign_key_check", null)) {
            assertFalse(check.moveToFirst());
        }
        helper.close();
        db = openCurrent();
        assertEquals(0, completed(db, 1));
        assertEquals(1, completed(db, 2));
        db.execSQL("INSERT INTO set_entry (workout_exercise_id, set_number, reps, weight, unit, set_type, created_at) "
                + "VALUES (1, 3, 0, 0, 'LB', 'NORMAL', 10)");
        assertEquals(0, completed(db, 4));
    }

    private SQLiteDatabase openCurrent() {
        helper = new WorkoutDatabase(context, name);
        return helper.getWritableDatabase();
    }

    private void prepareLegacyFile(int version) {
        // Use production workout/library tables, then restore the exact legacy set schema.
        WorkoutDatabase seed = new WorkoutDatabase(context, name);
        SQLiteDatabase db = seed.getWritableDatabase();
        db.execSQL("DROP TABLE template_catalog_entry");
        db.execSQL("DROP TABLE exercise_catalog_entry");
        db.execSQL("DROP TABLE workout_import_entry");
        db.execSQL("DROP TABLE set_entry");
        db.execSQL("CREATE TABLE set_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "workout_exercise_id INTEGER NOT NULL REFERENCES workout_exercise(id), "
                + "set_number INTEGER NOT NULL CHECK(set_number > 0), reps INTEGER NOT NULL CHECK(reps >= 0), "
                + "weight REAL NOT NULL CHECK(weight >= 0), unit TEXT NOT NULL CHECK(unit IN ('LB','KG')), "
                + "rest_seconds INTEGER CHECK(rest_seconds >= 0), rpe REAL CHECK(rpe >= 0 AND rpe <= 10), "
                + "set_type TEXT NOT NULL CHECK(set_type IN ('NORMAL','WARMUP','FAILURE','DROP')), created_at INTEGER NOT NULL"
                + (version == 3 ? ", completed INTEGER NOT NULL DEFAULT 1 CHECK(completed IN (0,1))" : "") + ")");
        db.execSQL("CREATE INDEX set_exercise_number ON set_entry(workout_exercise_id, set_number, id)");
        db.execSQL("INSERT INTO logged_workout (id,user_id,started_at,ended_at) VALUES (1,1,1,NULL),(2,2,1,10)");
        db.execSQL("INSERT INTO workout_exercise (id,logged_workout_id,exercise_id,order_index) VALUES (1,1,9,0),(2,2,9,0)");
        String columns = "id,workout_exercise_id,set_number,reps,weight,unit,set_type,created_at";
        db.execSQL("INSERT INTO set_entry (" + columns + ") VALUES "
                + "(1,1,1,0,0,'LB','NORMAL',1),(2,1,2,5,0,'LB','NORMAL',1),(3,2,1,0,0,'LB','FAILURE',1)");
        if (version == 3) { db.execSQL("UPDATE set_entry SET completed = 0 WHERE id = 1"); }
        if (version == 1) {
            db.execSQL("DROP TABLE template_exercise");
            db.execSQL("DROP TABLE template_workout");
            db.execSQL("DROP TABLE exercise");
        }
        db.setVersion(version);
        seed.close();
    }

    @Test
    public void versionFourAddsCatalogsWithoutChangingExistingLibraryOrSession() {
        helper = new WorkoutDatabase(context, name);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO exercise(id,title,description,archived) VALUES(1,'My custom curl','Keep my notes',1)");
        db.execSQL("INSERT INTO logged_workout(id,user_id,started_at,notes) VALUES(1,1,100,'Still active')");
        db.execSQL("DROP TABLE template_catalog_entry");
        db.execSQL("DROP TABLE exercise_catalog_entry");
        db.execSQL("DROP TABLE workout_import_entry");
        db.setVersion(4);
        helper.close();
        db = openCurrent();
        assertEquals(7, db.getVersion());
        try (Cursor row = db.rawQuery("SELECT description,archived FROM exercise WHERE id=1", null)) {
            assertTrue(row.moveToFirst()); assertEquals("Keep my notes", row.getString(0)); assertEquals(1, row.getInt(1));
        }
        try (Cursor row = db.rawQuery("SELECT notes,ended_at FROM logged_workout WHERE id=1", null)) {
            assertTrue(row.moveToFirst()); assertEquals("Still active", row.getString(0)); assertTrue(row.isNull(1));
        }
        db.execSQL("INSERT INTO exercise_catalog_entry VALUES('test',1,NULL)");
        helper.close();
        db = openCurrent();
        try (Cursor row = db.rawQuery("SELECT exercise_id FROM exercise_catalog_entry WHERE catalog_key='test'", null)) {
            assertTrue(row.moveToFirst()); assertEquals(1, row.getLong(0));
        }
    }

    @Test
    public void versionFiveRetainsExerciseMappingsWhileAddingRoutineMappings() {
        helper = new WorkoutDatabase(context, name);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO exercise(id,title) VALUES(1,'Existing')");
        db.execSQL("INSERT INTO exercise_catalog_entry VALUES('existing',1,'source')");
        db.execSQL("DROP TABLE template_catalog_entry");
        db.execSQL("DROP TABLE workout_import_entry");
        db.setVersion(5);
        helper.close();
        db = openCurrent();
        assertEquals(7, db.getVersion());
        try (Cursor row = db.rawQuery("SELECT source_id FROM exercise_catalog_entry WHERE catalog_key='existing'", null)) {
            assertTrue(row.moveToFirst()); assertEquals("source", row.getString(0));
        }
        try (Cursor row = db.rawQuery("SELECT COUNT(*) FROM template_catalog_entry", null)) {
            assertTrue(row.moveToFirst()); assertEquals(0, row.getInt(0));
        }
    }

    private int completed(SQLiteDatabase db, long id) {
        try (Cursor cursor = db.rawQuery("SELECT completed FROM set_entry WHERE id = ?",
                new String[]{Long.toString(id)})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private String completionDefault(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(set_entry)", null)) {
            while (cursor.moveToNext()) {
                if ("completed".equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"));
                }
            }
        }
        throw new AssertionError("Missing completion column");
    }
}
