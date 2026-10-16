package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.model.TemplateExerciseItemModel;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import java.util.List;
import java.util.Optional;

public final class SQLiteTemplateExerciseRepository implements TemplateExerciseRepository {
    private final SQLiteDatabase db;
    public SQLiteTemplateExerciseRepository(SQLiteDatabase db) { this.db = db; }

    @Override
    public TemplateExercise save(TemplateExercise row) {
        ContentValues values = new ContentValues();
        values.put("template_workout_id", row.getTemplateWorkoutId());
        values.put("exercise_id", row.getExerciseId());
        values.put("order_index", row.getOrderIndex());
        values.put("rest_time_seconds", row.getRestTimeSeconds());
        row.setId(SQLiteRows.save(db, "template_exercise", row.getId(), values));
        return row;
    }

    @Override
    public Optional<TemplateExercise> findById(long id) {
        return query("id = ?", new String[]{Long.toString(id)}).stream().findFirst();
    }

    @Override
    public List<TemplateExercise> findByTemplateWorkoutId(long id) {
        return query("template_workout_id = ?", new String[]{Long.toString(id)});
    }

    @Override
    public List<TemplateExerciseItemModel> findDetailsByTemplateWorkoutId(long id) {
        List<TemplateExerciseItemModel> rows = new java.util.ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT te.id, te.exercise_id, te.order_index, "
                + "te.rest_time_seconds, e.title FROM template_exercise te "
                + "JOIN exercise e ON e.id = te.exercise_id "
                + "WHERE te.template_workout_id = ? ORDER BY te.order_index, te.id",
                new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) {
                TemplateExerciseItemModel row = new TemplateExerciseItemModel();
                row.setTemplateExerciseId(cursor.getLong(0));
                row.setExerciseId(cursor.getLong(1));
                row.setOrderIndex(cursor.getInt(2));
                row.setRestTargetSeconds(cursor.isNull(3) ? null : cursor.getInt(3));
                row.setExerciseTitle(cursor.getString(4));
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public Integer findMaxOrderIndex(long id) {
        try (Cursor cursor = db.rawQuery("SELECT MAX(order_index) FROM template_exercise "
                + "WHERE template_workout_id = ?", new String[]{Long.toString(id)})) {
            cursor.moveToFirst();
            return cursor.isNull(0) ? null : cursor.getInt(0);
        }
    }

    @Override
    public void delete(long id) {
        db.delete("template_exercise", "id = ?", new String[]{Long.toString(id)});
    }

    @Override
    public boolean existsInTemplate(long templateId, long exerciseId) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM template_exercise "
                + "WHERE template_workout_id = ? AND exercise_id = ? LIMIT 1",
                new String[]{Long.toString(templateId), Long.toString(exerciseId)})) {
            return cursor.moveToFirst();
        }
    }

    private List<TemplateExercise> query(String selection, String[] args) {
        return SQLiteRows.query(db, "template_exercise", selection, args, "order_index, id", this::read);
    }

    private TemplateExercise read(Cursor cursor) {
        TemplateExercise row = new TemplateExercise();
        row.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        row.setTemplateWorkoutId(cursor.getLong(cursor.getColumnIndexOrThrow("template_workout_id")));
        row.setExerciseId(cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id")));
        row.setOrderIndex(cursor.getInt(cursor.getColumnIndexOrThrow("order_index")));
        int rest = cursor.getColumnIndexOrThrow("rest_time_seconds");
        row.setRestTimeSeconds(cursor.isNull(rest) ? null : cursor.getInt(rest));
        return row;
    }
}
