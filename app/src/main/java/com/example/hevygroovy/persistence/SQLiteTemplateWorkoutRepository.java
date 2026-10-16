package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import java.util.List;
import java.util.Optional;

public final class SQLiteTemplateWorkoutRepository implements TemplateWorkoutRepository {
    private final SQLiteDatabase db;
    public SQLiteTemplateWorkoutRepository(SQLiteDatabase db) { this.db = db; }

    @Override
    public TemplateWorkout save(TemplateWorkout row) {
        ContentValues values = new ContentValues();
        values.put("user_id", row.getUserId());
        values.put("title", row.getTitle());
        values.put("description", row.getDescription());
        values.put("created_at", row.getCreatedAtEpochMillis());
        values.put("archived", row.isArchived() ? 1 : 0);
        row.setId(SQLiteRows.save(db, "template_workout", row.getId(), values));
        return row;
    }

    @Override
    public Optional<TemplateWorkout> findById(long id) {
        return query("id = ?", new String[]{Long.toString(id)}).stream().findFirst();
    }

    @Override
    public List<TemplateWorkout> findAll() { return query(null, null); }

    /** This legacy method filters by the archived flag; findAll includes both states. */
    @Override
    public List<TemplateWorkout> findByArchive(boolean archived) {
        return query("archived = ?", new String[]{archived ? "1" : "0"});
    }

    @Override
    public boolean existsById(long id) { return findById(id).isPresent(); }

    private List<TemplateWorkout> query(String selection, String[] args) {
        return SQLiteRows.query(db, "template_workout", selection, args, "created_at DESC, id DESC", this::read);
    }

    private TemplateWorkout read(Cursor cursor) {
        TemplateWorkout row = new TemplateWorkout();
        row.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        row.setUserId(cursor.getLong(cursor.getColumnIndexOrThrow("user_id")));
        row.setTitle(cursor.getString(cursor.getColumnIndexOrThrow("title")));
        row.setDescription(cursor.getString(cursor.getColumnIndexOrThrow("description")));
        row.setCreatedAtEpochMillis(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        row.setArchived(cursor.getInt(cursor.getColumnIndexOrThrow("archived")) != 0);
        return row;
    }
}
