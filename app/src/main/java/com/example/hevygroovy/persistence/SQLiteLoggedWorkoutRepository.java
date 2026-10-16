package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SQLiteLoggedWorkoutRepository implements LoggedWorkoutRepository {
    private final SQLiteDatabase db;

    /** Pass the shared database so repositories can participate in the same transaction. */
    public SQLiteLoggedWorkoutRepository(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public LoggedWorkout save(LoggedWorkout workout) {
        ContentValues values = new ContentValues();
        values.put("user_id", workout.getUserId());
        values.put("template_id", workout.getTemplateId());
        values.put("name_snapshot", workout.getNameSnapshot());
        values.put("notes", workout.getNotes());
        values.put("started_at", workout.getStartedAtEpochMillis());
        values.put("ended_at", workout.getEndedAtEpochMillis());
        if (workout.getId() == 0) {
            workout.setId(db.insertOrThrow("logged_workout", null, values));
        } else if (workout.getId() < 0 || db.update("logged_workout", values,
                "id = ?", new String[]{Long.toString(workout.getId())}) != 1) {
            throw new IllegalStateException("Workout Not Found");
        }
        return workout;
    }

    @Override
    public Optional<LoggedWorkout> findById(long id) {
        List<LoggedWorkout> result = query("id = ?", new String[]{Long.toString(id)}, "1");
        return result.stream().findFirst();
    }

    @Override
    public Optional<LoggedWorkout> findActiveWorkoutByUserId(long userId) {
        return query("user_id = ? AND ended_at IS NULL",
                new String[]{Long.toString(userId)}, "1").stream().findFirst();
    }

    @Override
    public List<LoggedWorkout> findByUserId(long userId) {
        return query("user_id = ?", new String[]{Long.toString(userId)}, null);
    }

    @Override
    public List<LoggedWorkout> findByUserIdOrderByStartedAtDesc(long userId, int limit, int offset) {
        if (limit < 0 || offset < 0) {
            throw new IllegalArgumentException("Limit and offset cannot be negative");
        }
        return query("user_id = ?", new String[]{Long.toString(userId)}, offset + "," + limit);
    }

    private List<LoggedWorkout> query(String selection, String[] args, String limit) {
        List<LoggedWorkout> result = new ArrayList<>();
        try (Cursor cursor = db.query("logged_workout", null, selection, args,
                null, null, "started_at DESC, id DESC", limit)) {
            while (cursor.moveToNext()) {
                LoggedWorkout workout = new LoggedWorkout();
                workout.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                workout.setUserId(cursor.getLong(cursor.getColumnIndexOrThrow("user_id")));
                int templateColumn = cursor.getColumnIndexOrThrow("template_id");
                workout.setTemplateId(cursor.isNull(templateColumn) ? null : cursor.getLong(templateColumn));
                workout.setNameSnapshot(cursor.getString(cursor.getColumnIndexOrThrow("name_snapshot")));
                workout.setNotes(cursor.getString(cursor.getColumnIndexOrThrow("notes")));
                workout.setStartedAtEpochMillis(cursor.getLong(cursor.getColumnIndexOrThrow("started_at")));
                int endColumn = cursor.getColumnIndexOrThrow("ended_at");
                workout.setEndedAtEpochMillis(cursor.isNull(endColumn) ? null : cursor.getLong(endColumn));
                result.add(workout);
            }
        }
        return result;
    }
}
