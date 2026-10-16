package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Adds bundled entries once, without replacing user-owned exercise fields. Use off main. */
final class BundledExerciseCatalog {
    private static final String ASSET_NAME = "exercise_catalog.json";

    private BundledExerciseCatalog() { }

    static void seed(Context context, SQLiteDatabase db) {
        try (InputStream input = context.getAssets().open(ASSET_NAME)) {
            seed(db, input);
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Could not read the bundled exercise catalog", error);
        }
    }

    /** Does not close the supplied stream. All validation precedes database writes. */
    static void seed(SQLiteDatabase db, InputStream input) throws IOException, JSONException {
        List<Entry> entries = read(input);
        db.beginTransaction();
        try {
            Set<String> mapped = new HashSet<>();
            try (Cursor cursor = db.query("exercise_catalog_entry", new String[]{"catalog_key"},
                    null, null, null, null, null)) {
                while (cursor.moveToNext()) { mapped.add(cursor.getString(0)); }
            }
            Map<String, Long> exercisesByTitle = new HashMap<>();
            // Include archived entries. Where existing titles are duplicated, consistently use
            // the lowest ID; never merge or alter the other rows or their workout references.
            try (Cursor cursor = db.query("exercise", new String[]{"id", "title"},
                    null, null, null, null, "id ASC")) {
                while (cursor.moveToNext()) {
                    exercisesByTitle.putIfAbsent(normalizeTitle(cursor.getString(1)), cursor.getLong(0));
                }
            }
            SQLiteExerciseRepository repository = new SQLiteExerciseRepository(db);
            for (Entry entry : entries) {
                if (mapped.contains(entry.key)) { continue; }
                String titleKey = normalizeTitle(entry.title);
                Long exerciseId = exercisesByTitle.get(titleKey);
                if (exerciseId == null) {
                    Exercise exercise = new Exercise();
                    exercise.setTitle(entry.title);
                    exercise.setDescription(entry.description);
                    exercise.setPrimaryMuscleGroup(entry.primary);
                    exercise.setSecondaryMuscleGroups(entry.secondary);
                    exerciseId = repository.save(exercise).getId();
                    exercisesByTitle.put(titleKey, exerciseId);
                }
                ContentValues mapping = new ContentValues();
                mapping.put("catalog_key", entry.key);
                mapping.put("exercise_id", exerciseId);
                mapping.put("source_id", entry.sourceId);
                db.insertOrThrow("exercise_catalog_entry", null, mapping);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static List<Entry> read(InputStream input) throws IOException, JSONException {
        JSONTokener tokens = new JSONTokener(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        Object value = tokens.nextValue();
        if (!(value instanceof JSONObject) || tokens.nextClean() != 0) {
            throw new JSONException("Exercise catalog must contain one JSON object");
        }
        JSONObject root = (JSONObject) value;
        Object version = root.opt("schemaVersion");
        if (!(version instanceof Number) || ((Number) version).doubleValue() != 1D) {
            throw new JSONException("Unsupported exercise catalog schemaVersion");
        }
        JSONArray rows = root.getJSONArray("exercises");
        List<Entry> entries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String key = requiredString(row.get("catalogKey"), "catalogKey").trim();
            String title = requiredString(row.get("title"), "title").trim().replaceAll("\\s+", " ");
            if (key.isEmpty() || title.isEmpty()) {
                throw new JSONException("Catalog keys and titles cannot be blank");
            }
            if (!keys.add(key)) { throw new JSONException("Duplicate catalog key: " + key); }
            String description = nullableString(row.opt("description"), "description");
            String primary = nullableString(row.opt("primaryMuscle"), "primaryMuscle");
            String sourceId = nullableString(row.opt("sourceId"), "sourceId");
            if (sourceId != null && sourceId.trim().isEmpty()) {
                throw new JSONException("Source IDs must be nonblank or null");
            }
            JSONArray secondaryRows = row.getJSONArray("secondaryMuscles");
            List<MuscleGroup> secondary = new ArrayList<>();
            for (int muscle = 0; muscle < secondaryRows.length(); muscle++) {
                secondary.add(muscle(requiredString(secondaryRows.get(muscle), "secondaryMuscles")));
            }
            entries.add(new Entry(key, title, description,
                    primary == null ? null : muscle(primary), secondary, sourceId));
        }
        return entries;
    }

    private static String requiredString(Object value, String field) throws JSONException {
        if (!(value instanceof String)) { throw new JSONException(field + " must be a string"); }
        return (String) value;
    }

    private static String nullableString(Object value, String field) throws JSONException {
        return value == null || value == JSONObject.NULL ? null : requiredString(value, field);
    }

    private static MuscleGroup muscle(String name) throws JSONException {
        try {
            return MuscleGroup.valueOf(name);
        } catch (IllegalArgumentException error) {
            JSONException invalid = new JSONException("Unknown catalog muscle: " + name);
            invalid.initCause(error);
            throw invalid;
        }
    }

    private static String normalizeTitle(String title) {
        return title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        final String key;
        final String title;
        final String description;
        final MuscleGroup primary;
        final List<MuscleGroup> secondary;
        final String sourceId;

        Entry(String key, String title, String description, MuscleGroup primary,
              List<MuscleGroup> secondary, String sourceId) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.primary = primary;
            this.secondary = secondary;
            this.sourceId = sourceId;
        }
    }
}
