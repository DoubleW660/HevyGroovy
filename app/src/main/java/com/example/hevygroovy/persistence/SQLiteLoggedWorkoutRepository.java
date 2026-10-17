package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.example.hevygroovy.dto.WorkoutHistory;
import java.time.Duration;

public final class SQLiteLoggedWorkoutRepository implements LoggedWorkoutRepository {
    private final SQLiteDatabase db;

    /** Pass the shared database so repositories can participate in the same transaction. */
    public SQLiteLoggedWorkoutRepository(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public List<WorkoutHistory> findCompletedSummaries(long userId, int limit, int offset) {
        if (limit < 0 || offset < 0) {
            throw new IllegalArgumentException("Limit and offset cannot be negative");
        }
        String sql = "WITH page AS (SELECT * FROM logged_workout WHERE user_id = ? "
                + "AND ended_at IS NOT NULL ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?), "
                + "exercise_totals AS (SELECT we.logged_workout_id AS wid, COUNT(*) AS exercises "
                + "FROM workout_exercise we JOIN page p ON p.id = we.logged_workout_id GROUP BY wid), "
                + "set_totals AS (SELECT we.logged_workout_id AS wid, COUNT(*) AS sets, SUM(s.reps) AS reps, "
                + "SUM(s.weight * s.reps * CASE s.unit WHEN 'LB' THEN 0.45359237 ELSE 1 END "
                + "* CASE WHEN e.unilateral=1 THEN 2 ELSE 1 END) AS volume "
                + "FROM set_entry s JOIN workout_exercise we ON we.id = s.workout_exercise_id "
                + "LEFT JOIN exercise e ON e.id=we.exercise_id "
                + "JOIN page p ON p.id = we.logged_workout_id WHERE s.completed = 1 GROUP BY wid) "
                + "SELECT p.*, COALESCE(e.exercises,0), COALESCE(s.sets,0), COALESCE(s.reps,0), "
                + "COALESCE(s.volume,0) FROM page p LEFT JOIN exercise_totals e ON e.wid = p.id "
                + "LEFT JOIN set_totals s ON s.wid = p.id ORDER BY p.started_at DESC, p.id DESC";
        List<WorkoutHistory> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(userId),
                Integer.toString(limit), Integer.toString(offset)})) {
            while (cursor.moveToNext()) {
                long start = cursor.getLong(cursor.getColumnIndexOrThrow("started_at"));
                long end = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at"));
                int count = cursor.getColumnCount();
                result.add(new WorkoutHistory(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name_snapshot")), start, end,
                        Duration.ofMillis(end - start), cursor.getInt(count - 4), cursor.getInt(count - 3),
                        cursor.getInt(count - 2), cursor.getDouble(count - 1)));
            }
        }
        return result;
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
    public void delete(long id) {
        db.delete("logged_workout", "id = ?", new String[]{Long.toString(id)});
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

    @Override
    public List<LoggedWorkout> findCompletedByUserIdOrderByStartedAtDesc(long userId, int limit, int offset) {
        if (limit < 0 || offset < 0) {
            throw new IllegalArgumentException("Limit and offset cannot be negative");
        }
        return query("user_id = ? AND ended_at IS NOT NULL",
                new String[]{Long.toString(userId)}, offset + "," + limit);
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
