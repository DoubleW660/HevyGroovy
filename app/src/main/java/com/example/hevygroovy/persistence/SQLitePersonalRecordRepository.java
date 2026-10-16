package com.example.hevygroovy.persistence;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.PersonalRecordRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Computes record winners without loading historical sets into application memory. */
public final class SQLitePersonalRecordRepository implements PersonalRecordRepository {
    private final SQLiteDatabase db;

    public SQLitePersonalRecordRepository(SQLiteDatabase db) { this.db = db; }

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
                    + "s.weight * CASE s.unit WHEN 'LB' THEN 0.45359237 ELSE 1 END AS kg "
                    + "FROM set_entry s JOIN workout_exercise we ON we.id = s.workout_exercise_id "
                    + "JOIN logged_workout w ON w.id = we.logged_workout_id "
                    + "WHERE w.user_id = ? AND w.ended_at IS NOT NULL AND we.exercise_id IN ("
                    + placeholders + ") AND s.completed = 1 AND s.set_type <> 'WARMUP' AND s.reps > 0"
                    + cutoff + "), volumes AS (SELECT eligible.*, "
                    + "SUM(kg * reps) OVER (PARTITION BY workout_id, exercise_id) AS volume, "
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
                    + "kg * reps, weight, unit, reps, ended_at, order_index, set_number "
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
