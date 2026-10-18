package com.example.hevygroovy.importer;

import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Reads the original Hevy export, including quoted notes, mixed measurement types and units. */
public final class HevyCsvParser {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final DateTimeFormatter HEVY_DATE = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("d MMM uuuu, HH:mm").toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);

    public static final class Workout {
        public final String sourceKey, title, notes;
        public final long started, ended;
        public final List<Exercise> exercises = new ArrayList<>();
        Workout(String title, String notes, long started, long ended) {
            this.sourceKey = sourceKey(started, title); this.title = title; this.notes = notes;
            this.started = started; this.ended = ended;
        }
    }
    public static final class Exercise {
        public final String title, notes;
        public final List<SetEntry> sets = new ArrayList<>();
        Exercise(String title, String notes) { this.title = title; this.notes = notes; }
    }

    public List<Workout> parse(InputStream input, ZoneId zone) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw invalid("The CSV is larger than 20 MB.");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> records = csv(text);
        if (records.isEmpty()) throw invalid("The CSV is empty.");
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < records.get(0).size(); i++) {
            String name = records.get(0).get(i).trim().toLowerCase(Locale.ROOT);
            if (columns.put(name, i) != null) throw invalid("Duplicate CSV column: " + name);
        }
        for (String required : List.of("title", "start_time", "end_time", "exercise_title", "set_index", "set_type", "reps")) {
            if (!columns.containsKey(required)) throw invalid("Missing Hevy column: " + required);
        }
        List<String> weightColumns = List.of("weight_kg", "weight_lbs").stream().filter(columns::containsKey).toList();
        if (weightColumns.size() != 1) throw invalid("The CSV must contain exactly one of weight_kg or weight_lbs.");
        String weightColumn = weightColumns.get(0);
        Unit unit = weightColumn.equals("weight_lbs") ? Unit.LB : Unit.KG;
        List<String> distanceColumns = List.of("distance_meters", "distance_km", "distance_miles").stream().filter(columns::containsKey).toList();
        if (distanceColumns.size() > 1) throw invalid("The CSV contains conflicting distance units.");
        String distanceColumn = distanceColumns.isEmpty() ? null : distanceColumns.get(0);
        Map<String, Workout> workouts = new LinkedHashMap<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> row = records.get(i);
            if (row.stream().allMatch(String::isBlank)) continue;
            try {
                if (row.size() != records.get(0).size()) throw invalid("The number of values does not match the header.");
                String title = required(cell(row, columns, "title"), "Workout title");
                long started = timestamp(cell(row, columns, "start_time"), zone);
                long ended = timestamp(cell(row, columns, "end_time"), zone);
                if (ended < started) throw invalid("Workout ends before it starts.");
                String notes = optional(cell(row, columns, "description"));
                String key = sourceKey(started, title);
                Workout workout = workouts.computeIfAbsent(key, ignored -> new Workout(title, notes, started, ended));
                if (workout.ended != ended || !Objects.equals(workout.notes, notes)) throw invalid("Conflicting dates or notes for the same workout.");
                String exerciseTitle = required(cell(row, columns, "exercise_title"), "Exercise title");
                int index = integer(cell(row, columns, "set_index"), "Set index", false);
                String exerciseNotes = optional(cell(row, columns, "exercise_notes"));
                String superset = optional(cell(row, columns, "superset_id"));
                if (superset != null) exerciseNotes = (exerciseNotes == null ? "" : exerciseNotes + "\n\n") + "Superset: " + superset;
                Exercise exercise = workout.exercises.isEmpty() ? null : workout.exercises.get(workout.exercises.size() - 1);
                if (exercise == null || !exercise.title.equals(exerciseTitle) || index == 0) {
                    if (index != 0) throw invalid("Each exercise occurrence must start at set_index 0.");
                    exercise = new Exercise(exerciseTitle, exerciseNotes); workout.exercises.add(exercise);
                }
                if (index != exercise.sets.size()) throw invalid("Set indices must be consecutive within an exercise.");
                if (!Objects.equals(exercise.notes, exerciseNotes)) throw invalid("Conflicting notes within an exercise.");
                SetEntry set = new SetEntry();
                set.setSetNumber(index + 1); set.setUnit(unit); set.setCompleted(true); set.setCreatedAtEpochMillis(started);
                Double weight = decimal(cell(row, columns, weightColumn), "Weight");
                set.setWeight(weight == null ? 0 : weight);
                set.setReps(integer(cell(row, columns, "reps"), "Reps", true));
                String type = cell(row, columns, "set_type").trim().toLowerCase(Locale.ROOT);
                switch (type) {
                    case "normal": set.setSetType(SetType.NORMAL); break;
                    case "warmup": set.setSetType(SetType.WARMUP); break;
                    case "failure": set.setSetType(SetType.FAILURE); break;
                    case "dropset": case "drop": set.setSetType(SetType.DROP); break;
                    default: throw invalid("Unknown set type: " + type);
                }
                String duration = cell(row, columns, "duration_seconds");
                set.setDurationSeconds(duration.isBlank() ? null : integer(duration, "Duration", false));
                Double distance = distanceColumn == null ? null : decimal(cell(row, columns, distanceColumn), "Distance");
                double multiplier = "distance_miles".equals(distanceColumn) ? 1609.344 : "distance_km".equals(distanceColumn) ? 1000 : 1;
                if (distance != null && !Double.isFinite(distance * multiplier)) throw invalid("Distance is too large.");
                set.setDistanceMeters(distance == null ? null : distance * multiplier);
                Double rpe = decimal(cell(row, columns, "rpe"), "RPE");
                if (rpe != null && rpe > 10) throw invalid("RPE must be between 0 and 10.");
                set.setRpe(rpe == null ? null : rpe.floatValue());
                String rest = cell(row, columns, "rest_seconds");
                set.setRestSeconds(rest.isBlank() ? null : integer(rest, "Rest", false));
                exercise.sets.add(set);
            } catch (IllegalArgumentException error) {
                throw invalid("CSV record " + (i + 1) + ": " + error.getMessage());
            }
        }
        if (workouts.isEmpty()) throw invalid("The CSV contains no workouts.");
        return List.copyOf(workouts.values());
    }

    /** Compatible with receipts created by the bundled personal-history import. */
    public static String sourceKey(long started, String title) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((started + "\n" + title).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("hevy-history-v1/");
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static long timestamp(String text, ZoneId zone) {
        try {
            long millis = OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
            if (millis < 0) throw invalid("Workout dates must be after 1970.");
            return millis;
        }
        catch (DateTimeException ignored) { }
        try {
            LocalDateTime local;
            try { local = LocalDateTime.parse(text, HEVY_DATE); }
            catch (DateTimeException ignored) { local = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
            if (zone.getRules().getValidOffsets(local).size() != 1) throw invalid("Ambiguous or invalid local time in " + zone + ": " + text);
            long millis = local.atZone(zone).toInstant().toEpochMilli();
            if (millis < 0) throw invalid("Workout dates must be after 1970.");
            return millis;
        } catch (DateTimeException error) { throw invalid("Invalid date '" + text + "'. Expected 5 Jan 2026, 17:00 or an ISO timestamp."); }
    }
    private static String cell(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name); return index == null ? "" : row.get(index);
    }
    private static String required(String text, String label) { if (text.isBlank()) throw invalid(label + " is required."); return text; }
    private static String optional(String text) { return text.isBlank() ? null : text; }
    private static int integer(String text, String label, boolean blankIsZero) {
        if (blankIsZero && text.isBlank()) return 0;
        try { int value = new java.math.BigDecimal(text.trim()).intValueExact(); if (value < 0) throw new ArithmeticException(); return value; }
        catch (ArithmeticException | NumberFormatException error) { throw invalid(label + " must be a nonnegative whole number."); }
    }
    private static Double decimal(String text, String label) {
        if (text.isBlank()) return null;
        try { double value = Double.parseDouble(text.trim()); if (!Double.isFinite(value) || value < 0) throw new NumberFormatException(); return value; }
        catch (NumberFormatException error) { throw invalid(label + " must be finite and nonnegative."); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    /** RFC 4180 records: commas and line breaks inside quoted fields are preserved. */
    private static List<List<String>> csv(String text) {
        List<List<String>> records = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder(); boolean quoted = false, closed = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); closed = false;
                if (c != ',') {
                    records.add(row); row = new ArrayList<>();
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                }
            } else if (closed) throw invalid("Unexpected text after a quoted CSV field.");
            else if (c == '"') { if (field.length() != 0) throw invalid("Unexpected quote in a CSV field."); quoted = true; }
            else field.append(c);
        }
        if (quoted) throw invalid("Unclosed quoted CSV field.");
        if (field.length() > 0 || !row.isEmpty() || closed) { row.add(field.toString()); records.add(row); }
        return records;
    }
}
