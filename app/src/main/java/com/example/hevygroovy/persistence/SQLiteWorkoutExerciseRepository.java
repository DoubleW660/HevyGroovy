package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;
import java.util.*;

public final class SQLiteWorkoutExerciseRepository implements WorkoutExerciseRepository {
    private final SQLiteDatabase db;

    public SQLiteWorkoutExerciseRepository(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public WorkoutExercise save(WorkoutExercise row) {
        ContentValues values = new ContentValues();
        values.put("logged_workout_id", row.getLoggedWorkoutId());
        values.put("exercise_id", row.getExerciseId());
        values.put("exercise_name_snapshot", row.getExerciseNameSnapshot());
        values.put("order_index", row.getOrderIndex());
        values.put("rest_time_seconds", row.getRestTimeSeconds());
        values.put("notes", row.getNotes());
        if (row.getId() == 0) {
            row.setId(db.insertOrThrow("workout_exercise", null, values));
        } else if (row.getId() < 0 || db.update("workout_exercise", values,
                "id = ?", new String[]{Long.toString(row.getId())}) != 1) {
            throw new IllegalStateException("Workout Exercise Not Found");
        }
        return row;
    }

    @Override
    public void delete(long id) {
        db.delete("workout_exercise", "id = ?", new String[]{Long.toString(id)});
    }

    @Override
    public Optional<WorkoutExercise> findById(long id) {
        return query("id = ?", id).stream().findFirst();
    }

    /** Returns -1 when the workout has no exercise rows. */
    @Override
    public int findMaxOrderIndex(long loggedWorkoutId) {
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(order_index), -1) "
                + "FROM workout_exercise WHERE logged_workout_id = ?",
                new String[]{Long.toString(loggedWorkoutId)})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    @Override
    public List<WorkoutExercise> findByLoggedWorkoutId(long loggedWorkoutId) {
        return query("logged_workout_id = ?", loggedWorkoutId);
    }

    @Override
    public Map<Long, String> findLastNotesForUserExercises(long userId, List<Long> exerciseIds) {
        Map<Long, String> result = new HashMap<>();
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(exerciseIds));
        // Stay below SQLite's bound-parameter limit even for a large exercise library.
        for (int start = 0; start < distinct.size(); start += 500) {
            List<Long> batch = distinct.subList(start, Math.min(start + 500, distinct.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String[] args = new String[batch.size() + 1];
            args[0] = Long.toString(userId);
            for (int i = 0; i < batch.size(); i++) {
                args[i + 1] = Long.toString(batch.get(i));
            }
            String sql = "SELECT exercise_id, notes FROM (SELECT we.exercise_id, we.notes, "
                    + "ROW_NUMBER() OVER (PARTITION BY we.exercise_id "
                    + "ORDER BY w.ended_at DESC, w.id DESC, we.id DESC) AS rn FROM workout_exercise we "
                    + "JOIN logged_workout w ON w.id = we.logged_workout_id "
                    + "WHERE w.user_id = ? AND w.ended_at IS NOT NULL "
                    + "AND we.exercise_id IN (" + placeholders + ") "
                    + "AND EXISTS (SELECT 1 FROM set_entry s WHERE s.workout_exercise_id = we.id AND s.completed = 1) "
                    + ") WHERE rn = 1";
            try (Cursor cursor = db.rawQuery(sql, args)) {
                while (cursor.moveToNext()) {
                    long exerciseId = cursor.getLong(0);
                    // containsKey preserves a cleared (null) note from the latest performance.
                    if (!result.containsKey(exerciseId)) {
                        result.put(exerciseId, cursor.isNull(1) ? null : cursor.getString(1));
                    }
                }
            }
        }
        return result;
    }

    private List<WorkoutExercise> query(String selection, long id) {
        List<WorkoutExercise> result = new ArrayList<>();
        try (Cursor cursor = db.query("workout_exercise", null, selection,
                new String[]{Long.toString(id)}, null, null, "order_index, id")) {
            while (cursor.moveToNext()) {
                WorkoutExercise row = new WorkoutExercise();
                row.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                row.setLoggedWorkoutId(cursor.getLong(cursor.getColumnIndexOrThrow("logged_workout_id")));
                row.setExerciseId(cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id")));
                row.setExerciseNameSnapshot(cursor.getString(cursor.getColumnIndexOrThrow("exercise_name_snapshot")));
                row.setOrderIndex(cursor.getInt(cursor.getColumnIndexOrThrow("order_index")));
                row.setRestTimeSeconds(cursor.getInt(cursor.getColumnIndexOrThrow("rest_time_seconds")));
                row.setNotes(cursor.getString(cursor.getColumnIndexOrThrow("notes")));
                result.add(row);
            }
        }
        return result;
    }
}
