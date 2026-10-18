package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.HistoryImportResult;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.*;
import com.example.hevygroovy.importer.HevyCsvParser;
import com.example.hevygroovy.service.WorkoutImportService;
import java.io.InputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;

/** Adds finished workouts atomically; receipts protect edited and deliberately deleted history. */
public final class HevyHistoryImporter implements WorkoutImportService {
    private final SQLiteDatabase db;
    public HevyHistoryImporter(SQLiteDatabase db) { this.db = db; }

    @Override public HistoryImportResult importCsv(long userId, InputStream input, ZoneId timezone) throws IOException {
        if (userId <= 0) throw new IllegalArgumentException("Invalid user ID");
        List<HevyCsvParser.Workout> source = new HevyCsvParser().parse(input, timezone);
        db.beginTransaction();
        try {
            Set<String> receipts = new HashSet<>();
            try (Cursor cursor = db.rawQuery("SELECT source_key FROM workout_import_entry WHERE user_id=?", new String[]{Long.toString(userId)})) {
                while (cursor.moveToNext()) receipts.add(cursor.getString(0));
            }
            Map<Long, List<ExistingWorkout>> existing = new HashMap<>();
            try (Cursor cursor = db.rawQuery("SELECT id,name_snapshot,started_at,ended_at FROM logged_workout WHERE user_id=? AND ended_at IS NOT NULL", new String[]{Long.toString(userId)})) {
                while (cursor.moveToNext()) existing.computeIfAbsent(cursor.getLong(2) / 60_000, ignored -> new ArrayList<>())
                        .add(new ExistingWorkout(cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3)));
            }
            Map<String, Long> exerciseNames = new HashMap<>(), exerciseAliases = new HashMap<>(), templateNames = new HashMap<>(), templateAliases = new HashMap<>();
            try (Cursor cursor = db.rawQuery("SELECT id,title FROM exercise", null)) {
                while (cursor.moveToNext()) { addName(exerciseNames, name(cursor.getString(1)), cursor.getLong(0)); addName(exerciseAliases, slug(cursor.getString(1)), cursor.getLong(0)); }
            }
            try (Cursor cursor = db.rawQuery("SELECT exercise_id,catalog_key FROM exercise_catalog_entry", null)) {
                while (cursor.moveToNext()) addName(exerciseAliases, slug(tail(cursor.getString(1))), cursor.getLong(0));
            }
            try (Cursor cursor = db.rawQuery("SELECT id,title FROM template_workout WHERE user_id=?", new String[]{Long.toString(userId)})) {
                while (cursor.moveToNext()) addName(templateNames, name(cursor.getString(1)), cursor.getLong(0));
            }
            try (Cursor cursor = db.rawQuery("SELECT c.template_id,c.catalog_key FROM template_catalog_entry c JOIN template_workout t ON t.id=c.template_id WHERE c.user_id=? AND t.user_id=?", new String[]{Long.toString(userId), Long.toString(userId)})) {
                while (cursor.moveToNext()) addName(templateAliases, slug(tail(cursor.getString(1))), cursor.getLong(0));
            }
            SQLiteExerciseRepository exercises = new SQLiteExerciseRepository(db);
            SQLiteLoggedWorkoutRepository workouts = new SQLiteLoggedWorkoutRepository(db);
            SQLiteWorkoutExerciseRepository rows = new SQLiteWorkoutExerciseRepository(db);
            SQLiteSetEntryRepository sets = new SQLiteSetEntryRepository(db);
            int imported = 0, skipped = 0, setCount = 0, created = 0;
            for (HevyCsvParser.Workout original : source) {
                if (receipts.contains(original.sourceKey)) { skipped++; continue; }
                ExistingWorkout match = existing.getOrDefault(original.started / 60_000, List.of()).stream()
                        .filter(workout -> workout.matches(original)).findFirst().orElse(null);
                if (match != null) { receipt(userId, original.sourceKey, match.id); skipped++; continue; }
                LoggedWorkout workout = new LoggedWorkout();
                workout.setUserId(userId); workout.setNameSnapshot(original.title); workout.setNotes(original.notes);
                workout.setStartedAtEpochMillis(original.started); workout.setEndedAtEpochMillis(original.ended);
                Long template = resolve(templateNames, templateAliases, original.title);
                workout.setTemplateId(template != null && template > 0 ? template : null);
                long workoutId = workouts.save(workout).getId();
                for (int i = 0; i < original.exercises.size(); i++) {
                    HevyCsvParser.Exercise sourceRow = original.exercises.get(i);
                    Long exerciseId = resolve(exerciseNames, exerciseAliases, sourceRow.title);
                    if (exerciseId != null && exerciseId < 0) throw new IllegalArgumentException("Multiple exercises match '" + sourceRow.title + "'. Give them distinct names before importing.");
                    if (exerciseId == null) {
                        Exercise exercise = new Exercise(); exercise.setTitle(sourceRow.title); exercise.setPrimaryMuscleGroup(MuscleGroup.OTHER);
                        exercise.setTrackingType(tracking(sourceRow.sets));
                        exerciseId = exercises.save(exercise).getId(); created++;
                        addName(exerciseNames, name(sourceRow.title), exerciseId); addName(exerciseAliases, slug(sourceRow.title), exerciseId);
                    }
                    WorkoutExercise row = new WorkoutExercise(); row.setLoggedWorkoutId(workoutId); row.setExerciseId(exerciseId);
                    row.setExerciseNameSnapshot(sourceRow.title); row.setOrderIndex(i); row.setNotes(sourceRow.notes);
                    long rowId = rows.save(row).getId();
                    for (SetEntry set : sourceRow.sets) { set.setWorkoutExerciseId(rowId); sets.save(set); setCount++; }
                }
                receipt(userId, original.sourceKey, workoutId); imported++;
            }
            db.setTransactionSuccessful();
            return new HistoryImportResult(imported, skipped, setCount, created);
        } finally { db.endTransaction(); }
    }

    private void receipt(long userId, String key, long workoutId) {
        ContentValues values = new ContentValues(); values.put("user_id", userId); values.put("source_key", key); values.put("workout_id", workoutId);
        db.insertOrThrow("workout_import_entry", null, values);
    }
    private static void addName(Map<String, Long> names, String key, long id) {
        names.merge(key, id, (previous, next) -> previous.equals(next) ? previous : -1L);
    }
    private static Long resolve(Map<String, Long> names, Map<String, Long> aliases, String title) {
        Long exact = names.get(name(title)); return exact != null ? exact : aliases.get(slug(title));
    }
    private static String name(String text) { return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static String slug(String text) { return name(text).replaceAll("[^\\p{L}\\p{N}]", ""); }
    private static String tail(String key) { return key.substring(key.lastIndexOf('/') + 1); }
    private static TrackingType tracking(List<SetEntry> sets) {
        boolean reps = sets.stream().anyMatch(set -> set.getReps() > 0);
        boolean weight = sets.stream().anyMatch(set -> set.getWeight() > 0);
        boolean time = sets.stream().anyMatch(set -> set.getDurationSeconds() != null && set.getDurationSeconds() > 0);
        boolean distance = sets.stream().anyMatch(set -> set.getDistanceMeters() != null && set.getDistanceMeters() > 0);
        if (distance) return time ? TrackingType.DISTANCE_TIME : TrackingType.DISTANCE;
        if (time && !reps) return weight ? TrackingType.WEIGHT_TIME : TrackingType.TIME;
        return weight ? TrackingType.WEIGHT_REPS : TrackingType.REPS;
    }
    private static final class ExistingWorkout {
        final long id, started, ended; final String title;
        ExistingWorkout(long id, String title, long started, long ended) { this.id = id; this.title = title; this.started = started; this.ended = ended; }
        boolean matches(HevyCsvParser.Workout other) {
            // Hevy's CSV rounds timestamps to minutes; native sessions can retain seconds.
            return (started / 60_000 == other.started / 60_000 && name(title).equals(name(other.title))) ||
                    (started == other.started && ended == other.ended);
        }
    }
}
