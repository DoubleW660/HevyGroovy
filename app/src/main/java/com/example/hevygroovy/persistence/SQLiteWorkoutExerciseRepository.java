package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.dto.ActiveSetEntry;
import com.example.hevygroovy.dto.PreviousExercisePerformance;
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

    @Override
    public Map<Long, PreviousExercisePerformance> findPreviousPerformancesForUserExercises(
            long userId, List<Long> exerciseIds) {
        Map<Long, PreviousExercisePerformance> result = new HashMap<>();
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(exerciseIds));
        for (int start = 0; start < distinct.size(); start += 500) {
            List<Long> batch = distinct.subList(start, Math.min(start + 500, distinct.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String[] args = new String[batch.size() + 1];
            args[0] = Long.toString(userId);
            for (int i = 0; i < batch.size(); i++) args[i + 1] = Long.toString(batch.get(i));
            String sql = "WITH performances AS (SELECT DISTINCT we.exercise_id, w.id AS workout_id, w.ended_at "
                    + "FROM workout_exercise we JOIN logged_workout w ON w.id = we.logged_workout_id "
                    + "WHERE w.user_id = ? AND w.ended_at IS NOT NULL AND we.exercise_id IN ("
                    + placeholders + ") AND EXISTS (SELECT 1 FROM set_entry s "
                    + "WHERE s.workout_exercise_id = we.id AND s.completed = 1)), "
                    + "ranked AS (SELECT performances.*, ROW_NUMBER() OVER (PARTITION BY exercise_id "
                    + "ORDER BY ended_at DESC, workout_id DESC) AS rn FROM performances) "
                    + "SELECT p.exercise_id, p.workout_id, p.ended_at, we.id AS row_id, s.* "
                    + "FROM ranked p JOIN workout_exercise we ON we.logged_workout_id = p.workout_id "
                    + "AND we.exercise_id = p.exercise_id LEFT JOIN set_entry s "
                    + "ON s.workout_exercise_id = we.id AND s.completed = 1 WHERE p.rn = 1 "
                    + "ORDER BY p.exercise_id, we.order_index, we.id, s.set_number, s.id";
            Map<Long, LinkedHashMap<Long, List<ActiveSetEntry>>> occurrences = new LinkedHashMap<>();
            Map<Long, Long> workoutIds = new HashMap<>();
            Map<Long, Long> endedAt = new HashMap<>();
            try (Cursor cursor = db.rawQuery(sql, args)) {
                while (cursor.moveToNext()) {
                    long exerciseId = cursor.getLong(0);
                    workoutIds.put(exerciseId, cursor.getLong(1));
                    endedAt.put(exerciseId, cursor.getLong(2));
                    List<ActiveSetEntry> previousSets = occurrences
                            .computeIfAbsent(exerciseId, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(cursor.getLong(3), ignored -> new ArrayList<>());
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow("id"))) {
                        previousSets.add(new ActiveSetEntry(previousSet(cursor)));
                    }
                }
            }
            for (Map.Entry<Long, LinkedHashMap<Long, List<ActiveSetEntry>>> entry : occurrences.entrySet()) {
                result.put(entry.getKey(), new PreviousExercisePerformance(workoutIds.get(entry.getKey()),
                        endedAt.get(entry.getKey()), new ArrayList<>(entry.getValue().values())));
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<Long, List<List<ActiveSetEntry>>> findLastCompletedSetsForTemplateExercises(
            long userId, long templateId, List<Long> exerciseIds) {
        Map<Long, List<List<ActiveSetEntry>>> result = new HashMap<>();
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(exerciseIds));
        for (int start = 0; start < distinct.size(); start += 500) {
            List<Long> batch = distinct.subList(start, Math.min(start + 500, distinct.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String[] args = new String[batch.size() + 2];
            args[0] = Long.toString(userId);
            args[1] = Long.toString(templateId);
            for (int i = 0; i < batch.size(); i++) args[i + 2] = Long.toString(batch.get(i));
            // Number every exercise occurrence before removing rows without completed sets.
            String sql = "WITH occurrences AS (SELECT we.id AS row_id, we.exercise_id, w.id AS workout_id, "
                    + "w.ended_at, ROW_NUMBER() OVER (PARTITION BY we.logged_workout_id, we.exercise_id "
                    + "ORDER BY we.order_index, we.id) - 1 AS occurrence FROM workout_exercise we "
                    + "JOIN logged_workout w ON w.id = we.logged_workout_id WHERE w.user_id = ? "
                    + "AND w.template_id = ? AND w.ended_at IS NOT NULL AND we.exercise_id IN ("
                    + placeholders + ")), ranked AS (SELECT occurrences.*, ROW_NUMBER() OVER "
                    + "(PARTITION BY exercise_id, occurrence ORDER BY ended_at DESC, workout_id DESC, row_id DESC) AS rn "
                    + "FROM occurrences WHERE EXISTS (SELECT 1 FROM set_entry s "
                    + "WHERE s.workout_exercise_id = row_id AND s.completed = 1)) "
                    + "SELECT p.exercise_id, p.occurrence, s.* FROM ranked p JOIN set_entry s "
                    + "ON s.workout_exercise_id = p.row_id AND s.completed = 1 WHERE p.rn = 1 "
                    + "ORDER BY p.exercise_id, p.occurrence, s.set_number, s.id";
            try (Cursor cursor = db.rawQuery(sql, args)) {
                while (cursor.moveToNext()) {
                    List<List<ActiveSetEntry>> occurrences = result.computeIfAbsent(
                            cursor.getLong(0), ignored -> new ArrayList<>());
                    int occurrence = cursor.getInt(1);
                    while (occurrences.size() <= occurrence) occurrences.add(new ArrayList<>());
                    occurrences.get(occurrence).add(new ActiveSetEntry(previousSet(cursor)));
                }
            }
        }
        Map<Long, List<List<ActiveSetEntry>>> immutable = new HashMap<>();
        result.forEach((exerciseId, occurrences) -> immutable.put(exerciseId,
                occurrences.stream().map(List::copyOf).collect(java.util.stream.Collectors.toUnmodifiableList())));
        return Map.copyOf(immutable);
    }

    private SetEntry previousSet(Cursor cursor) {
        SetEntry set = new SetEntry();
        set.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        set.setWorkoutExerciseId(cursor.getLong(cursor.getColumnIndexOrThrow("workout_exercise_id")));
        set.setSetNumber(cursor.getInt(cursor.getColumnIndexOrThrow("set_number")));
        set.setReps(cursor.getInt(cursor.getColumnIndexOrThrow("reps")));
        set.setWeight(cursor.getDouble(cursor.getColumnIndexOrThrow("weight")));
        set.setUnit(Unit.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("unit"))));
        set.setSetType(SetType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("set_type"))));
        set.setCreatedAtEpochMillis(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        set.setCompleted(true);
        int rest = cursor.getColumnIndexOrThrow("rest_seconds");
        set.setRestSeconds(cursor.isNull(rest) ? null : cursor.getInt(rest));
        int rpe = cursor.getColumnIndexOrThrow("rpe");
        set.setRpe(cursor.isNull(rpe) ? null : cursor.getFloat(rpe));
        return set;
    }

    private List<WorkoutExercise> query(String selection, long id) {
        List<WorkoutExercise> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT we.*, COALESCE(e.unilateral,0) AS exercise_unilateral "
                + "FROM workout_exercise we LEFT JOIN exercise e ON e.id=we.exercise_id "
                + "WHERE we." + selection + " ORDER BY we.order_index, we.id", new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) {
                WorkoutExercise row = new WorkoutExercise();
                row.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                row.setLoggedWorkoutId(cursor.getLong(cursor.getColumnIndexOrThrow("logged_workout_id")));
                row.setExerciseId(cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id")));
                row.setExerciseNameSnapshot(cursor.getString(cursor.getColumnIndexOrThrow("exercise_name_snapshot")));
                row.setOrderIndex(cursor.getInt(cursor.getColumnIndexOrThrow("order_index")));
                row.setRestTimeSeconds(cursor.getInt(cursor.getColumnIndexOrThrow("rest_time_seconds")));
                row.setNotes(cursor.getString(cursor.getColumnIndexOrThrow("notes")));
                row.setUnilateral(cursor.getInt(cursor.getColumnIndexOrThrow("exercise_unilateral")) != 0);
                result.add(row);
            }
        }
        return result;
    }
}
