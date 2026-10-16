package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.repo.ExerciseRepository;
import java.util.*;
import java.util.stream.Collectors;

public final class SQLiteExerciseRepository implements ExerciseRepository {
    private final SQLiteDatabase db;
    public SQLiteExerciseRepository(SQLiteDatabase db) { this.db = db; }

    @Override
    public Exercise save(Exercise row) {
        ContentValues values = new ContentValues();
        values.put("title", row.getTitle());
        values.put("description", row.getDescription());
        values.put("primary_muscle", row.getPrimaryMuscleGroup() == null
                ? null : row.getPrimaryMuscleGroup().name());
        values.put("secondary_muscles", row.getSecondaryMuscleGroups().stream()
                .map(Enum::name).collect(Collectors.joining(",")));
        values.put("archived", row.isArchived() ? 1 : 0);
        row.setId(SQLiteRows.save(db, "exercise", row.getId(), values));
        return row;
    }

    @Override
    public Optional<Exercise> findById(long id) {
        return query("id = ?", new String[]{Long.toString(id)}).stream().findFirst();
    }

    @Override
    public List<Exercise> findAll(boolean includeArchived) {
        return query(includeArchived ? null : "archived = 0", null);
    }

    @Override
    public List<Exercise> searchByTitle(String query, boolean includeArchived) {
        Objects.requireNonNull(query, "Search query is required");
        // Treat LIKE metacharacters as literal title characters.
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return query("title LIKE ? ESCAPE '\\'" + (includeArchived ? "" : " AND archived = 0"),
                new String[]{"%" + escaped + "%"});
    }

    private List<Exercise> query(String selection, String[] args) {
        return SQLiteRows.query(db, "exercise", selection, args, "title COLLATE NOCASE, id", this::read);
    }

    private Exercise read(Cursor cursor) {
        Exercise row = new Exercise();
        row.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        row.setTitle(cursor.getString(cursor.getColumnIndexOrThrow("title")));
        row.setDescription(cursor.getString(cursor.getColumnIndexOrThrow("description")));
        String primary = cursor.getString(cursor.getColumnIndexOrThrow("primary_muscle"));
        row.setPrimaryMuscleGroup(primary == null ? null : MuscleGroup.valueOf(primary));
        String secondary = cursor.getString(cursor.getColumnIndexOrThrow("secondary_muscles"));
        List<MuscleGroup> muscles = new ArrayList<>();
        if (!secondary.isEmpty()) {
            for (String name : secondary.split(",")) { muscles.add(MuscleGroup.valueOf(name)); }
        }
        row.setSecondaryMuscleGroups(muscles);
        row.setArchived(cursor.getInt(cursor.getColumnIndexOrThrow("archived")) != 0);
        return row;
    }
}
