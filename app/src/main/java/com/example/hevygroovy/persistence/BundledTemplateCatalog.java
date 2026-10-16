package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Imports the latest routine structures once per local user, preserving later edits. */
final class BundledTemplateCatalog {
    private BundledTemplateCatalog() { }

    static void seed(Context context, SQLiteDatabase db, long userId) {
        try (InputStream input = context.getAssets().open("template_catalog.json")) {
            seed(db, userId, input);
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Could not read the bundled routine catalog", error);
        }
    }

    static void seed(SQLiteDatabase db, long userId, InputStream input) throws IOException, JSONException {
        if (userId <= 0) { throw new IllegalArgumentException("User ID must be positive"); }
        List<Routine> routines = read(input);
        db.beginTransaction();
        try {
            Map<String, Long> exercises = new HashMap<>();
            try (Cursor cursor = db.query("exercise_catalog_entry", new String[]{"catalog_key", "exercise_id"},
                    null, null, null, null, null)) {
                while (cursor.moveToNext()) { exercises.put(cursor.getString(0), cursor.getLong(1)); }
            }
            // Check all references before creating any template or child row.
            for (Routine routine : routines) {
                for (String key : routine.exercises) {
                    if (!exercises.containsKey(key)) { throw new JSONException("Unknown routine exercise: " + key); }
                }
            }
            Set<String> mapped = new HashSet<>();
            try (Cursor cursor = db.query("template_catalog_entry", new String[]{"catalog_key"},
                    "user_id = ?", new String[]{Long.toString(userId)}, null, null, null)) {
                while (cursor.moveToNext()) { mapped.add(cursor.getString(0)); }
            }
            Map<String, Long> existing = new HashMap<>();
            try (Cursor cursor = db.query("template_workout", new String[]{"id", "title"},
                    "user_id = ?", new String[]{Long.toString(userId)}, null, null, "id ASC")) {
                while (cursor.moveToNext()) { existing.putIfAbsent(normalize(cursor.getString(1)), cursor.getLong(0)); }
            }
            SQLiteTemplateWorkoutRepository templates = new SQLiteTemplateWorkoutRepository(db);
            SQLiteTemplateExerciseRepository rows = new SQLiteTemplateExerciseRepository(db);
            for (Routine routine : routines) {
                if (mapped.contains(routine.key)) { continue; }
                Long id = existing.get(normalize(routine.title));
                if (id == null) {
                    TemplateWorkout template = new TemplateWorkout();
                    template.setUserId(userId);
                    template.setTitle(routine.title);
                    template.setDescription(routine.description);
                    template.setCreatedAtEpochMillis(System.currentTimeMillis());
                    id = templates.save(template).getId();
                    for (int index = 0; index < routine.exercises.size(); index++) {
                        TemplateExercise row = new TemplateExercise();
                        row.setTemplateWorkoutId(id);
                        row.setExerciseId(exercises.get(routine.exercises.get(index)));
                        row.setOrderIndex(index);
                        row.setRestTimeSeconds(routine.rest.get(index));
                        rows.save(row);
                    }
                    existing.put(normalize(routine.title), id);
                }
                ContentValues mapping = new ContentValues();
                mapping.put("user_id", userId);
                mapping.put("catalog_key", routine.key);
                mapping.put("template_id", id);
                db.insertOrThrow("template_catalog_entry", null, mapping);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static List<Routine> read(InputStream input) throws IOException, JSONException {
        JSONTokener tokens = new JSONTokener(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        Object value = tokens.nextValue();
        if (!(value instanceof JSONObject) || tokens.nextClean() != 0) { throw new JSONException("Invalid routine catalog"); }
        JSONObject root = (JSONObject) value;
        Object version = root.opt("schemaVersion");
        if (!(version instanceof Number) || ((Number) version).doubleValue() != 1D) {
            throw new JSONException("Unsupported routine catalog version");
        }
        JSONArray entries = root.getJSONArray("templates");
        List<Routine> routines = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            String key = text(entry.get("catalogKey"));
            if (!keys.add(key)) { throw new JSONException("Duplicate routine key: " + key); }
            String title = text(entry.get("title")).replaceAll("\\s+", " ");
            Object descriptionValue = entry.opt("description");
            String description = descriptionValue == null || descriptionValue == JSONObject.NULL ? null : text(descriptionValue);
            JSONArray children = entry.getJSONArray("exercises");
            if (children.length() == 0) { throw new JSONException("Imported routines require an exercise"); }
            List<String> exerciseKeys = new ArrayList<>();
            List<Integer> rest = new ArrayList<>();
            for (int child = 0; child < children.length(); child++) {
                JSONObject row = children.getJSONObject(child);
                exerciseKeys.add(text(row.get("exerciseKey")));
                Object seconds = row.opt("restTimeSeconds");
                if (seconds == null || seconds == JSONObject.NULL) { rest.add(null); }
                else if (seconds instanceof Number && ((Number) seconds).doubleValue() >= 0
                        && ((Number) seconds).doubleValue() <= Integer.MAX_VALUE
                        && ((Number) seconds).doubleValue() == ((Number) seconds).intValue()) {
                    rest.add(((Number) seconds).intValue());
                } else { throw new JSONException("Invalid routine rest timer"); }
            }
            routines.add(new Routine(key, title, description, exerciseKeys, rest));
        }
        return routines;
    }

    private static String text(Object value) throws JSONException {
        if (!(value instanceof String) || ((String) value).isBlank()) { throw new JSONException("Expected nonblank text"); }
        return ((String) value).trim();
    }

    private static String normalize(String title) { return title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }

    private static final class Routine {
        final String key, title, description;
        final List<String> exercises;
        final List<Integer> rest;
        Routine(String key, String title, String description, List<String> exercises, List<Integer> rest) {
            this.key = key; this.title = title; this.description = description; this.exercises = exercises; this.rest = rest;
        }
    }
}
