package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local workout storage. Use one shared instance and access it off the UI thread. */
public final class WorkoutDatabase extends SQLiteOpenHelper {
    public WorkoutDatabase(Context context) {
        super(context.getApplicationContext(), "hevy_groovy.db", null, 2);
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
        db.execSQL("CREATE TABLE set_entry ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "workout_exercise_id INTEGER NOT NULL REFERENCES workout_exercise(id), "
                + "set_number INTEGER NOT NULL CHECK(set_number > 0), "
                + "reps INTEGER NOT NULL CHECK(reps >= 0), weight REAL NOT NULL CHECK(weight >= 0), "
                + "unit TEXT NOT NULL CHECK(unit IN ('LB', 'KG')), "
                + "rest_seconds INTEGER CHECK(rest_seconds >= 0), "
                + "rpe REAL CHECK(rpe >= 0 AND rpe <= 10), "
                + "set_type TEXT NOT NULL CHECK(set_type IN ('WARMUP', 'NORMAL', 'FAILURE', 'DROP')), "
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX set_exercise_number ON set_entry(workout_exercise_id, set_number, id)");
        createLibraryTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            createLibraryTables(db);
            return;
        }
        throw new IllegalStateException("Missing database migration from " + oldVersion + " to " + newVersion);
    }

    private void createLibraryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, description TEXT, primary_muscle TEXT, "
                + "secondary_muscles TEXT NOT NULL DEFAULT '', archived INTEGER NOT NULL DEFAULT 0 "
                + "CHECK(archived IN (0,1)))");
        db.execSQL("CREATE INDEX exercise_title ON exercise(title COLLATE NOCASE)");
        db.execSQL("CREATE TABLE template_workout (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, "
                + "created_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE template_exercise (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "template_workout_id INTEGER NOT NULL REFERENCES template_workout(id), "
                + "exercise_id INTEGER NOT NULL REFERENCES exercise(id), "
                + "order_index INTEGER NOT NULL CHECK(order_index >= 0), "
                + "rest_time_seconds INTEGER CHECK(rest_time_seconds >= 0))");
        db.execSQL("CREATE INDEX template_exercise_order "
                + "ON template_exercise(template_workout_id, order_index, id)");
    }
}
