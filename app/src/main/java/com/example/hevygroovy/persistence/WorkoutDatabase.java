package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local workout storage. Use one shared instance and access it off the UI thread. */
public final class WorkoutDatabase extends SQLiteOpenHelper {
    public WorkoutDatabase(Context context) {
        this(context, "hevy_groovy.db");
    }

    public WorkoutDatabase(Context context, String name) {
        super(context.getApplicationContext(), name, null, 9);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE logged_workout ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL CHECK(user_id > 0), "
                + "template_id INTEGER, name_snapshot TEXT, notes TEXT, "
                + "started_at INTEGER NOT NULL, ended_at INTEGER, "
                + "CHECK(ended_at IS NULL OR ended_at >= started_at))");
        db.execSQL("CREATE UNIQUE INDEX one_active_workout_per_user "
                + "ON logged_workout(user_id) WHERE ended_at IS NULL");
        db.execSQL("CREATE INDEX workout_user_started "
                + "ON logged_workout(user_id, started_at DESC, id DESC)");
        db.execSQL("CREATE TABLE workout_exercise ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "logged_workout_id INTEGER NOT NULL REFERENCES logged_workout(id), "
                + "exercise_id INTEGER NOT NULL CHECK(exercise_id > 0), "
                + "exercise_name_snapshot TEXT, order_index INTEGER NOT NULL CHECK(order_index >= 0), "
                + "rest_time_seconds INTEGER NOT NULL DEFAULT 0 CHECK(rest_time_seconds >= 0), notes TEXT)");
        db.execSQL("CREATE INDEX exercise_workout_order "
                + "ON workout_exercise(logged_workout_id, order_index, id)");
        db.execSQL("CREATE INDEX exercise_history ON workout_exercise(exercise_id, logged_workout_id)");
        createSetTable(db, "set_entry");
        db.execSQL("CREATE INDEX set_exercise_number ON set_entry(workout_exercise_id, set_number, id)");
        createLibraryTables(db);
        createCatalogTable(db);
        createTemplateCatalogTable(db);
        createWorkoutImportTable(db);
    }

    private void createSetTable(SQLiteDatabase db, String table) {
        db.execSQL("CREATE TABLE " + table + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "workout_exercise_id INTEGER NOT NULL REFERENCES workout_exercise(id), "
                + "set_number INTEGER NOT NULL CHECK(set_number > 0), "
                + "reps INTEGER NOT NULL CHECK(reps >= 0), weight REAL NOT NULL CHECK(weight >= 0), "
                + "unit TEXT NOT NULL CHECK(unit IN ('LB', 'KG')), "
                + "rest_seconds INTEGER CHECK(rest_seconds >= 0), "
                + "duration_seconds INTEGER CHECK(duration_seconds >= 0), distance_meters REAL CHECK(distance_meters >= 0), "
                + "rpe REAL CHECK(rpe >= 0 AND rpe <= 10), "
                + "set_type TEXT NOT NULL CHECK(set_type IN ('WARMUP', 'NORMAL', 'FAILURE', 'DROP')), "
                + "created_at INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1 || newVersion > 9 || oldVersion >= newVersion) {
            throw new IllegalStateException("Missing database migration from " + oldVersion + " to " + newVersion);
        }
        if (oldVersion < 2 && newVersion >= 2) {
            createLibraryTables(db);
        }
        if (oldVersion < 3 && newVersion >= 3) {
            db.execSQL("ALTER TABLE set_entry ADD COLUMN completed INTEGER NOT NULL DEFAULT 0 "
                    + "CHECK(completed IN (0,1))");
            // Preserve finished history; active rows require evidence of performed reps.
            db.execSQL("UPDATE set_entry SET completed = 1 WHERE reps > 0 OR workout_exercise_id IN "
                    + "(SELECT we.id FROM workout_exercise we JOIN logged_workout w "
                    + "ON w.id = we.logged_workout_id WHERE w.ended_at IS NOT NULL)");
        }
        if (oldVersion < 4 && newVersion >= 4) {
            // v3 upgrades used DEFAULT 1. Rebuild to match new installs without
            // changing explicit completion decisions already saved by v3 clients.
            createSetTable(db, "set_entry_v4");
            String columns = "id, workout_exercise_id, set_number, reps, weight, unit, "
                    + "rest_seconds, rpe, set_type, created_at, completed";
            db.execSQL("INSERT INTO set_entry_v4 (" + columns + ") SELECT " + columns + " FROM set_entry");
            db.execSQL("DROP TABLE set_entry");
            db.execSQL("ALTER TABLE set_entry_v4 RENAME TO set_entry");
            db.execSQL("CREATE INDEX set_exercise_number ON set_entry(workout_exercise_id, set_number, id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS template_user_created "
                    + "ON template_workout(user_id, created_at DESC, id DESC)");
        }
        if (oldVersion < 5 && newVersion >= 5) {
            createCatalogTable(db);
        }
        if (oldVersion < 6 && newVersion >= 6) {
            createTemplateCatalogTable(db);
        }
        if (oldVersion < 7 && newVersion >= 7) {
            createWorkoutImportTable(db);
        }
        if (oldVersion < 8 && newVersion >= 8) {
            // v1 upgrades created the current library tables above.
            if (oldVersion >= 2) {
                db.execSQL("ALTER TABLE exercise ADD COLUMN unilateral INTEGER NOT NULL DEFAULT 0 "
                        + "CHECK(unilateral IN (0,1))");
            }
            // The user confirmed per-side logging for this exercise. Stable catalog mappings
            // also cover a renamed library entry; other exercises remain user-configurable.
            db.execSQL("UPDATE exercise SET unilateral=1 WHERE id IN (SELECT exercise_id "
                    + "FROM exercise_catalog_entry WHERE catalog_key='personal/single-leg-press-machine') "
                    + "OR lower(trim(title))='single leg press (machine)'");
        }
        if (oldVersion < 9 && newVersion >= 9) {
            // Older migrations can rebuild these tables using the current schema.
            if (!hasColumn(db, "exercise", "tracking_type")) db.execSQL("ALTER TABLE exercise ADD COLUMN tracking_type TEXT NOT NULL DEFAULT 'WEIGHT_REPS'");
            if (!hasColumn(db, "set_entry", "duration_seconds")) db.execSQL("ALTER TABLE set_entry ADD COLUMN duration_seconds INTEGER CHECK(duration_seconds >= 0)");
            if (!hasColumn(db, "set_entry", "distance_meters")) db.execSQL("ALTER TABLE set_entry ADD COLUMN distance_meters REAL CHECK(distance_meters >= 0)");
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String name) {
        try (android.database.Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) if (name.equals(cursor.getString(1))) return true;
        }
        return false;
    }

    private void createWorkoutImportTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE workout_import_entry ("
                + "user_id INTEGER NOT NULL CHECK(user_id > 0), source_key TEXT NOT NULL, "
                + "workout_id INTEGER REFERENCES logged_workout(id) ON DELETE SET NULL, "
                + "PRIMARY KEY(user_id, source_key))");
        db.execSQL("CREATE INDEX imported_workout ON workout_import_entry(workout_id)");
    }

    private void createTemplateCatalogTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE template_catalog_entry ("
                + "user_id INTEGER NOT NULL CHECK(user_id > 0), catalog_key TEXT NOT NULL, "
                + "template_id INTEGER NOT NULL REFERENCES template_workout(id) ON DELETE CASCADE, "
                + "PRIMARY KEY(user_id, catalog_key))");
        db.execSQL("CREATE INDEX catalog_template ON template_catalog_entry(template_id)");
    }

    private void createCatalogTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE exercise_catalog_entry ("
                + "catalog_key TEXT NOT NULL PRIMARY KEY, "
                + "exercise_id INTEGER NOT NULL REFERENCES exercise(id) ON DELETE CASCADE, "
                + "source_id TEXT)");
        db.execSQL("CREATE INDEX catalog_exercise ON exercise_catalog_entry(exercise_id)");
    }

    private void createLibraryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, description TEXT, primary_muscle TEXT, "
                + "secondary_muscles TEXT NOT NULL DEFAULT '', archived INTEGER NOT NULL DEFAULT 0 "
                + "CHECK(archived IN (0,1)), unilateral INTEGER NOT NULL DEFAULT 0 CHECK(unilateral IN (0,1)), "
                + "tracking_type TEXT NOT NULL DEFAULT 'WEIGHT_REPS')");
        db.execSQL("CREATE INDEX exercise_title ON exercise(title COLLATE NOCASE)");
        db.execSQL("CREATE TABLE template_workout (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, "
                + "created_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE template_exercise (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "template_workout_id INTEGER NOT NULL REFERENCES template_workout(id), "
                + "exercise_id INTEGER NOT NULL REFERENCES exercise(id), "
                + "order_index INTEGER NOT NULL CHECK(order_index >= 0), "
                + "rest_time_seconds INTEGER CHECK(rest_time_seconds >= 0))");
        db.execSQL("CREATE INDEX template_user_created ON template_workout(user_id, created_at DESC, id DESC)");
        db.execSQL("CREATE INDEX template_exercise_order "
                + "ON template_exercise(template_workout_id, order_index, id)");
    }
}
