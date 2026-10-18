package com.example.hevygroovy.persistence;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.dto.ExercisePerformance;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.PersonalRecordRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes record winners without loading historical sets into application memory. */
public final class SQLitePersonalRecordRepository implements PersonalRecordRepository {
    private final SQLiteDatabase db;

    public SQLitePersonalRecordRepository(SQLiteDatabase db) { this.db = db; }

    @Override
    public List<ExercisePerformance> findExerciseHistory(long userId, long exerciseId) {
        // exercise_history and set_exercise_number indexes restrict this read to the selected
        // exercise. Read once, rather than loading every workout and fetching its children.
        String sql = "SELECT w.id AS history_workout_id, w.name_snapshot AS history_workout_name, "
                + "w.started_at AS history_started_at, w.ended_at AS history_ended_at, "
                + "we.exercise_name_snapshot AS history_exercise_name, we.id AS history_row_id, "
                + "we.notes AS history_notes, COALESCE(e.unilateral, 0) AS history_unilateral, s.* "
                + "FROM workout_exercise we JOIN logged_workout w ON w.id=we.logged_workout_id "
                + "JOIN set_entry s ON s.workout_exercise_id=we.id "
                + "LEFT JOIN exercise e ON e.id=we.exercise_id "
                + "WHERE we.exercise_id=? AND w.user_id=? AND w.ended_at IS NOT NULL AND s.completed=1 "
                + "ORDER BY w.ended_at DESC, w.id DESC, we.order_index, we.id, s.set_number, s.id";
        Map<Long, HistoryBuilder> performances = new LinkedHashMap<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(exerciseId), Long.toString(userId)})) {
            while (cursor.moveToNext()) {
                long workoutId = cursor.getLong(cursor.getColumnIndexOrThrow("history_workout_id"));
                HistoryBuilder performance = performances.get(workoutId);
                if (performance == null) {
                    performance = new HistoryBuilder(workoutId, exerciseId,
                            cursor.getString(cursor.getColumnIndexOrThrow("history_exercise_name")),
                            cursor.getString(cursor.getColumnIndexOrThrow("history_workout_name")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("history_started_at")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("history_ended_at")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("history_unilateral")) != 0);
                    performances.put(workoutId, performance);
                }
                long rowId = cursor.getLong(cursor.getColumnIndexOrThrow("history_row_id"));
                if (performance.lastRowId != rowId) {
                    String notes = cursor.getString(cursor.getColumnIndexOrThrow("history_notes"));
                    if (notes != null && !notes.isBlank()) performance.notes.add(notes);
                    performance.lastRowId = rowId;
                }
                SetEntry set = new SetEntry();
                set.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                set.setWorkoutExerciseId(rowId);
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
                performance.sets.add(set);
            }
        }
        List<ExercisePerformance> result = new ArrayList<>();
        for (HistoryBuilder performance : performances.values()) result.add(performance.build());
        return List.copyOf(result);
    }

    private static final class HistoryBuilder {
        final long workoutId, exerciseId, startedAt, endedAt;
        final String exerciseName, workoutName;
        final boolean unilateral;
        final List<SetEntry> sets = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        long lastRowId = -1;

        HistoryBuilder(long workoutId, long exerciseId, String exerciseName, String workoutName,
                       long startedAt, long endedAt, boolean unilateral) {
            this.workoutId = workoutId; this.exerciseId = exerciseId;
            this.exerciseName = exerciseName; this.workoutName = workoutName;
            this.startedAt = startedAt; this.endedAt = endedAt; this.unilateral = unilateral;
        }

        ExercisePerformance build() {
            return new ExercisePerformance(workoutId, exerciseId, exerciseName, workoutName,
                    startedAt, endedAt, sets, notes.isEmpty() ? null : String.join("\n\n", notes), unilateral);
        }
    }

    @Override
    public double findBestEstimatedOneRepMax(long userId, long exerciseId) {
        String sql = "SELECT COALESCE(MAX(s.weight * CASE s.unit WHEN 'LB' THEN 0.45359237 ELSE 1 END "
                + "* CASE WHEN s.reps = 1 THEN 1 ELSE (1 + s.reps / 30.0) END), 0) "
                + "FROM set_entry s JOIN workout_exercise we ON we.id = s.workout_exercise_id "
                + "JOIN logged_workout w ON w.id = we.logged_workout_id "
                + "WHERE w.user_id = ? AND we.exercise_id = ? AND w.ended_at IS NOT NULL "
                + "AND s.completed = 1 AND s.set_type <> 'WARMUP' AND s.weight > 0 AND s.reps BETWEEN 1 AND 10";
        try (Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(userId), Long.toString(exerciseId)})) {
            cursor.moveToFirst();
            double result = cursor.getDouble(0);
            return Double.isFinite(result) ? result : 0;
        }
    }

    @Override
    public List<PersonalRecord> findBestRecords(long userId, List<Long> exerciseIds,
                                               Long beforeEndedAtEpochMillis, long beforeWorkoutId) {
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(exerciseIds));
        List<PersonalRecord> result = new ArrayList<>();
        // A history request can contain a large library. Leave room for user/cutoff parameters.
        for (int start = 0; start < distinct.size(); start += 500) {
            List<Long> batch = distinct.subList(start, Math.min(start + 500, distinct.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            List<String> args = new ArrayList<>();
            args.add(Long.toString(userId));
            for (long id : batch) args.add(Long.toString(id));
            String cutoff = "";
            if (beforeEndedAtEpochMillis != null) {
                cutoff = " AND (w.ended_at < ? OR (w.ended_at = ? AND w.id < ?))";
                args.add(Long.toString(beforeEndedAtEpochMillis));
                args.add(Long.toString(beforeEndedAtEpochMillis));
                args.add(Long.toString(beforeWorkoutId));
            }
            String sql = "WITH eligible AS (SELECT we.exercise_id, we.exercise_name_snapshot AS name, "
                    + "w.id AS workout_id, we.id AS row_id, we.order_index, s.id AS set_id, s.set_number, "
                    + "s.weight, s.unit, s.reps, w.ended_at, "
                    + "s.weight * CASE s.unit WHEN 'LB' THEN 0.45359237 ELSE 1 END AS kg, "
                    + "CASE WHEN e.unilateral=1 THEN 2 ELSE 1 END AS volume_multiplier "
                    + "FROM set_entry s JOIN workout_exercise we ON we.id = s.workout_exercise_id "
                    + "LEFT JOIN exercise e ON e.id=we.exercise_id "
                    + "JOIN logged_workout w ON w.id = we.logged_workout_id "
                    + "WHERE w.user_id = ? AND w.ended_at IS NOT NULL AND we.exercise_id IN ("
                    + placeholders + ") AND s.completed = 1 AND s.set_type <> 'WARMUP' AND s.reps > 0"
                    + cutoff + "), volumes AS (SELECT eligible.*, "
                    + "SUM(kg * reps * volume_multiplier) OVER (PARTITION BY workout_id, exercise_id) AS volume, "
                    + "SUM(reps) OVER (PARTITION BY workout_id, exercise_id) AS total_reps, "
                    + "ROW_NUMBER() OVER (PARTITION BY workout_id, exercise_id "
                    + "ORDER BY order_index, row_id, set_number, set_id) AS first_row FROM eligible), "
                    + "candidates AS ("
                    + "SELECT 'HEAVIEST_WEIGHT' AS type, exercise_id, name, workout_id, row_id, set_id, "
                    + "kg AS value, weight, unit, reps, ended_at, order_index, set_number "
                    + "FROM eligible WHERE kg > 0 UNION ALL "
                    + "SELECT 'MOST_REPS', exercise_id, name, workout_id, row_id, set_id, "
                    + "reps, weight, unit, reps, ended_at, order_index, set_number FROM eligible UNION ALL "
                    + "SELECT 'BEST_SET_VOLUME', exercise_id, name, workout_id, row_id, set_id, "
                    + "kg * reps * volume_multiplier, weight, unit, reps, ended_at, order_index, set_number "
                    + "FROM eligible WHERE kg > 0 UNION ALL "
                    + "SELECT 'WORKOUT_VOLUME', exercise_id, name, workout_id, row_id, 0, "
                    + "volume, 0, 'KG', total_reps, ended_at, order_index, set_number "
                    + "FROM volumes WHERE first_row = 1 AND volume > 0), "
                    + "maxima AS (SELECT exercise_id, type, MAX(value) AS best FROM candidates "
                    + "GROUP BY exercise_id, type), ranked AS (SELECT c.*, "
                    + "ROW_NUMBER() OVER (PARTITION BY c.exercise_id, c.type "
                    + "ORDER BY c.ended_at, c.workout_id, c.order_index, c.row_id, c.set_number, c.set_id) AS winner "
                    + "FROM candidates c JOIN maxima m ON m.exercise_id = c.exercise_id AND m.type = c.type "
                    + "WHERE c.value = m.best) SELECT * FROM ranked WHERE winner = 1";
            try (Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]))) {
                while (cursor.moveToNext()) {
                    result.add(new PersonalRecord(RecordType.valueOf(cursor.getString(0)),
                            cursor.getLong(1), cursor.getString(2), cursor.getLong(3), cursor.getLong(4),
                            cursor.getLong(5), cursor.getDouble(6), cursor.getDouble(7),
                            Unit.valueOf(cursor.getString(8)), cursor.getInt(9), cursor.getLong(10)));
                }
            }
        }
        result.sort(Comparator.comparingLong(PersonalRecord::getExerciseId)
                .thenComparing(PersonalRecord::getType));
        return List.copyOf(result);
    }
}
