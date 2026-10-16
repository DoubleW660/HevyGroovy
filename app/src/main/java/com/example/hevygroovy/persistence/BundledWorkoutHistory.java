package com.example.hevygroovy.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Imports finished source sessions once per user without changing local workouts or drafts. */
final class BundledWorkoutHistory {
    private BundledWorkoutHistory() { }

    static void seed(Context context, SQLiteDatabase db, long userId) {
        try (InputStream input = context.getAssets().open("workout_history.json")) {
            seed(db, userId, input);
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Could not import the bundled workout history", error);
        }
    }

    /** Does not close the stream. Parse everything, then validate pending references before writing. */
    static void seed(SQLiteDatabase db, long userId, InputStream input) throws IOException, JSONException {
        if (userId <= 0) throw new IllegalArgumentException("User ID must be positive");
        List<Workout> source = read(input);
        db.beginTransaction();
        try {
            Set<String> imported = new HashSet<>();
            try (Cursor cursor = db.query("workout_import_entry", new String[]{"source_key"},
                    "user_id = ?", new String[]{Long.toString(userId)}, null, null, null)) {
                while (cursor.moveToNext()) imported.add(cursor.getString(0));
            }
            List<Workout> pending = new ArrayList<>();
            for (Workout workout : source) {
                // A receipt with a null workout ID is a deletion tombstone, not a request to restore it.
                if (!imported.contains(workout.key)) pending.add(workout);
            }
            if (pending.isEmpty()) {
                db.setTransactionSuccessful();
                return;
            }
            Map<String, Long> exerciseIds = new HashMap<>();
            try (Cursor cursor = db.rawQuery("SELECT c.catalog_key, c.exercise_id FROM exercise_catalog_entry c "
                    + "JOIN exercise e ON e.id = c.exercise_id", null)) {
                while (cursor.moveToNext()) exerciseIds.put(cursor.getString(0), cursor.getLong(1));
            }
            for (Workout workout : pending) {
                for (Exercise row : workout.exercises) {
                    if (!exerciseIds.containsKey(row.key)) {
                        throw new JSONException("Unknown history exercise catalog key: " + row.key);
                    }
                }
            }
            Map<String, Long> templateIds = new HashMap<>();
            try (Cursor cursor = db.rawQuery("SELECT c.catalog_key, c.template_id FROM template_catalog_entry c "
                    + "JOIN template_workout t ON t.id = c.template_id "
                    + "WHERE c.user_id = ? AND t.user_id = ?",
                    new String[]{Long.toString(userId), Long.toString(userId)})) {
                while (cursor.moveToNext()) templateIds.put(cursor.getString(0), cursor.getLong(1));
            }
            SQLiteLoggedWorkoutRepository workouts = new SQLiteLoggedWorkoutRepository(db);
            SQLiteWorkoutExerciseRepository exercises = new SQLiteWorkoutExerciseRepository(db);
            SQLiteSetEntryRepository sets = new SQLiteSetEntryRepository(db);
            for (Workout sourceWorkout : pending) {
                LoggedWorkout workout = new LoggedWorkout();
                workout.setUserId(userId);
                workout.setTemplateId(templateIds.get(sourceWorkout.templateKey));
                workout.setNameSnapshot(sourceWorkout.title);
                workout.setNotes(sourceWorkout.notes);
                workout.setStartedAtEpochMillis(sourceWorkout.started);
                workout.setEndedAtEpochMillis(sourceWorkout.ended);
                long workoutId = workouts.save(workout).getId();
                for (int index = 0; index < sourceWorkout.exercises.size(); index++) {
                    Exercise sourceRow = sourceWorkout.exercises.get(index);
                    WorkoutExercise row = new WorkoutExercise();
                    row.setLoggedWorkoutId(workoutId);
                    row.setExerciseId(exerciseIds.get(sourceRow.key));
                    row.setExerciseNameSnapshot(sourceRow.name);
                    row.setOrderIndex(index);
                    row.setRestTimeSeconds(0);
                    row.setNotes(sourceRow.notes);
                    long rowId = exercises.save(row).getId();
                    for (SetEntry set : sourceRow.sets) {
                        set.setWorkoutExerciseId(rowId);
                        sets.save(set);
                    }
                }
                ContentValues receipt = new ContentValues();
                receipt.put("user_id", userId);
                receipt.put("source_key", sourceWorkout.key);
                receipt.put("workout_id", workoutId);
                db.insertOrThrow("workout_import_entry", null, receipt);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static List<Workout> read(InputStream input) throws IOException, JSONException {
        JSONTokener tokens = new JSONTokener(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        Object value = tokens.nextValue();
        if (!(value instanceof JSONObject) || tokens.nextClean() != 0) {
            throw new JSONException("Workout history must contain one JSON object");
        }
        JSONObject root = (JSONObject) value;
        if (integer(root.get("schemaVersion"), "schemaVersion", 1, 1) != 1) {
            throw new JSONException("Unsupported workout history schemaVersion");
        }
        LocalDate since;
        ZoneId timezone;
        try {
            since = LocalDate.parse(text(root.get("since"), "since"));
            timezone = ZoneId.of(text(root.get("sourceTimezone"), "sourceTimezone"));
        } catch (DateTimeException invalid) {
            throw invalid("Invalid workout history date or source timezone", invalid);
        }
        JSONArray rows = root.getJSONArray("workouts");
        Set<String> keys = new HashSet<>();
        List<Workout> workouts = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String key = text(row.get("sourceKey"), "sourceKey");
            if (!keys.add(key)) throw new JSONException("Duplicate workout history sourceKey: " + key);
            String title = text(row.get("title"), "title");
            long started = integer(row.get("startedAtEpochMillis"), "startedAtEpochMillis", 0, Long.MAX_VALUE);
            long ended = integer(row.get("endedAtEpochMillis"), "endedAtEpochMillis", started, Long.MAX_VALUE);
            if (Instant.ofEpochMilli(started).atZone(timezone).toLocalDate().isBefore(since)) {
                throw new JSONException("Workout history contains a session before its declared cutoff");
            }
            String notes = nullableText(row.opt("notes"), "notes");
            String templateKey = nullableText(row.opt("templateKey"), "templateKey");
            if (templateKey != null && templateKey.isBlank()) throw new JSONException("templateKey must be nonblank or null");
            JSONArray children = row.getJSONArray("exercises");
            if (children.length() == 0) throw new JSONException("Imported workouts require an exercise");
            List<Exercise> exercises = new ArrayList<>();
            for (int child = 0; child < children.length(); child++) {
                JSONObject exercise = children.getJSONObject(child);
                String exerciseKey = text(exercise.get("exerciseKey"), "exerciseKey");
                String name = text(exercise.get("nameSnapshot"), "nameSnapshot");
                String exerciseNotes = nullableText(exercise.opt("notes"), "exercise notes");
                JSONArray sourceSets = exercise.getJSONArray("sets");
                if (sourceSets.length() == 0) throw new JSONException("Imported exercise rows require a performed set");
                List<SetEntry> sets = new ArrayList<>();
                int previousNumber = 0;
                for (int number = 0; number < sourceSets.length(); number++) {
                    JSONObject sourceSet = sourceSets.getJSONObject(number);
                    int setNumber = (int) integer(sourceSet.get("setNumber"), "setNumber", 1, Integer.MAX_VALUE);
                    if (setNumber <= previousNumber) throw new JSONException("Imported set numbers must increase within each exercise");
                    previousNumber = setNumber;
                    Object weightValue = sourceSet.get("weight");
                    double weight = weightValue == JSONObject.NULL ? 0 : decimal(weightValue, "weight", 0, Double.MAX_VALUE);
                    int reps = (int) integer(sourceSet.get("reps"), "reps", 0, Integer.MAX_VALUE);
                    Unit unit;
                    SetType type;
                    try {
                        unit = Unit.valueOf(text(sourceSet.get("unit"), "unit"));
                        type = SetType.valueOf(text(sourceSet.get("setType"), "setType"));
                    } catch (IllegalArgumentException invalid) {
                        throw invalid("Unknown imported set unit or type", invalid);
                    }
                    Object rpeValue = sourceSet.opt("rpe");
                    Float rpe = rpeValue == null || rpeValue == JSONObject.NULL ? null
                            : (float) decimal(rpeValue, "rpe", 0, 10);
                    Object restValue = sourceSet.opt("restSeconds");
                    Integer rest = restValue == null || restValue == JSONObject.NULL ? null
                            : (int) integer(restValue, "restSeconds", 0, Integer.MAX_VALUE);
                    SetEntry set = new SetEntry();
                    set.setSetNumber(setNumber);
                    set.setWeight(weight);
                    set.setUnit(unit);
                    set.setReps(reps);
                    set.setSetType(type);
                    set.setCompleted(true);
                    set.setRpe(rpe);
                    set.setRestSeconds(rest);
                    set.setCreatedAtEpochMillis(started);
                    sets.add(set);
                }
                exercises.add(new Exercise(exerciseKey, name, exerciseNotes, sets));
            }
            workouts.add(new Workout(key, title, notes, templateKey, started, ended, exercises));
        }
        workouts.sort(Comparator.comparingLong((Workout workout) -> workout.ended)
                .thenComparingLong(workout -> workout.started).thenComparing(workout -> workout.key));
        return workouts;
    }

    private static String text(Object value, String field) throws JSONException {
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new JSONException(field + " must be a nonblank string");
        }
        return (String) value;
    }

    private static String nullableText(Object value, String field) throws JSONException {
        if (value == null || value == JSONObject.NULL) return null;
        if (!(value instanceof String)) throw new JSONException(field + " must be a string or null");
        return (String) value;
    }

    private static long integer(Object value, String field, long minimum, long maximum) throws JSONException {
        if (!(value instanceof Number)) throw new JSONException(field + " must be an integer");
        try {
            long result = new BigDecimal(value.toString()).longValueExact();
            if (result < minimum || result > maximum) throw new JSONException(field + " is out of range");
            return result;
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw invalid(field + " must be a finite integer", invalid);
        }
    }

    private static double decimal(Object value, String field, double minimum, double maximum) throws JSONException {
        if (!(value instanceof Number)) throw new JSONException(field + " must be a number");
        double result = ((Number) value).doubleValue();
        if (!Double.isFinite(result) || result < minimum || result > maximum) {
            throw new JSONException(field + " is out of range");
        }
        return result;
    }

    private static JSONException invalid(String message, Exception cause) {
        JSONException error = new JSONException(message);
        error.initCause(cause);
        return error;
    }

    private static final class Workout {
        final String key, title, notes, templateKey;
        final long started, ended;
        final List<Exercise> exercises;
        Workout(String key, String title, String notes, String templateKey,
                long started, long ended, List<Exercise> exercises) {
            this.key = key; this.title = title; this.notes = notes; this.templateKey = templateKey;
            this.started = started; this.ended = ended; this.exercises = exercises;
        }
    }

    private static final class Exercise {
        final String key, name, notes;
        final List<SetEntry> sets;
        Exercise(String key, String name, String notes, List<SetEntry> sets) {
            this.key = key; this.name = name; this.notes = notes; this.sets = sets;
        }
    }
}
