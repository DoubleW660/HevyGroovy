package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.SetEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SQLiteSetEntryRepository implements SetEntryRepository {
    private final SQLiteDatabase db;

    public SQLiteSetEntryRepository(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public SetEntry save(SetEntry set) {
        if (!Double.isFinite(set.getWeight()) || (set.getRpe() != null && !Float.isFinite(set.getRpe()))) {
            throw new IllegalArgumentException("Weight and RPE must be finite");
        }
        ContentValues values = new ContentValues();
        values.put("workout_exercise_id", set.getWorkoutExerciseId());
        values.put("set_number", set.getSetNumber());
        values.put("reps", set.getReps());
        values.put("weight", set.getWeight());
        values.put("unit", set.getUnit() == null ? null : set.getUnit().name());
        values.put("rest_seconds", set.getRestSeconds());
        validateMeasurements(set);
        values.put("duration_seconds", set.getDurationSeconds());
        values.put("distance_meters", set.getDistanceMeters());
        values.put("rpe", set.getRpe());
        values.put("set_type", set.getSetType() == null ? null : set.getSetType().name());
        values.put("created_at", set.getCreatedAtEpochMillis());
        values.put("completed", set.isCompleted() ? 1 : 0);
        if (set.getId() == 0) {
            set.setId(db.insertOrThrow("set_entry", null, values));
        } else if (set.getId() < 0 || db.update("set_entry", values,
                "id = ?", new String[]{Long.toString(set.getId())}) != 1) {
            throw new IllegalStateException("Set Entry Not Found");
        }
        return set;
    }

    @Override
    public void delete(long id) {
        db.delete("set_entry", "id = ?", new String[]{Long.toString(id)});
    }

    @Override
    public Optional<SetEntry> findById(long id) {
        return query("SELECT s.* FROM set_entry s WHERE s.id = ?", id).stream().findFirst();
    }

    @Override
    public List<SetEntry> findByWorkoutExerciseId(long id) {
        return query("SELECT s.* FROM set_entry s WHERE s.workout_exercise_id = ? "
                + "ORDER BY s.set_number, s.id", id);
    }

    @Override
    public List<SetEntry> findByLoggedWorkoutId(long id) {
        return query("SELECT s.* FROM set_entry s JOIN workout_exercise we "
                + "ON we.id = s.workout_exercise_id WHERE we.logged_workout_id = ? "
                + "ORDER BY we.order_index, we.id, s.set_number, s.id", id);
    }

    @Override
    public boolean existsByLoggedWorkoutId(long id) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM set_entry s JOIN workout_exercise we "
                + "ON we.id = s.workout_exercise_id WHERE we.logged_workout_id = ? AND s.completed = 1 LIMIT 1",
                new String[]{Long.toString(id)})) {
            return cursor.moveToFirst();
        }
    }

    static void validateMeasurements(SetEntry set) {
        if ((set.getDurationSeconds() != null && set.getDurationSeconds() < 0) ||
                (set.getDistanceMeters() != null && (!Double.isFinite(set.getDistanceMeters()) || set.getDistanceMeters() < 0))) {
            throw new IllegalArgumentException("Time and distance must be finite and nonnegative");
        }
    }

    static void readMeasurements(Cursor cursor, SetEntry set) {
        int duration = cursor.getColumnIndexOrThrow("duration_seconds");
        int distance = cursor.getColumnIndexOrThrow("distance_meters");
        set.setDurationSeconds(cursor.isNull(duration) ? null : cursor.getInt(duration));
        set.setDistanceMeters(cursor.isNull(distance) ? null : cursor.getDouble(distance));
    }

    private List<SetEntry> query(String sql, long id) {
        List<SetEntry> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) {
                SetEntry set = new SetEntry();
                set.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                set.setWorkoutExerciseId(cursor.getLong(cursor.getColumnIndexOrThrow("workout_exercise_id")));
                set.setSetNumber(cursor.getInt(cursor.getColumnIndexOrThrow("set_number")));
                set.setReps(cursor.getInt(cursor.getColumnIndexOrThrow("reps")));
                set.setWeight(cursor.getDouble(cursor.getColumnIndexOrThrow("weight")));
                set.setUnit(Unit.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("unit"))));
                set.setSetType(SetType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("set_type"))));
                set.setCreatedAtEpochMillis(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                set.setCompleted(cursor.getInt(cursor.getColumnIndexOrThrow("completed")) != 0);
                int rest = cursor.getColumnIndexOrThrow("rest_seconds");
                set.setRestSeconds(cursor.isNull(rest) ? null : cursor.getInt(rest));
                int rpe = cursor.getColumnIndexOrThrow("rpe");
                set.setRpe(cursor.isNull(rpe) ? null : cursor.getFloat(rpe));
                readMeasurements(cursor, set);
                result.add(set);
            }
        }
        return result;
    }
}
