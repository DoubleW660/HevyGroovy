package com.example.hevygroovy.importer;

import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HevyCsvParserTest {
    private static final String HEADER = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\r\n";
    private static final String START = "\"14 Oct 2026, 17:00\",\"14 Oct 2026, 18:00\"";
    private List<HevyCsvParser.Workout> parse(String csv) throws Exception {
        return new HevyCsvParser().parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), ZoneId.of("America/New_York"));
    }
    private String row(String exercise, int index, String type, String weight, String reps, String distance, String duration) {
        return "Upper," + START + ",," + exercise + ",,," + index + "," + type + "," + weight + "," + reps + "," + distance + "," + duration + ",\r\n";
    }
    @Test void parsesBomMultilineNotesTypesAndRepeatedExerciseOccurrences() throws Exception {
        var workouts = parse("\uFEFF" + HEADER +
                "Upper," + START + ",\"Line 1\r\nLine 2, \"\"quoted\"\"\",Bench,1,\"Slow, pause\",0,warmup,45,10,,,6\r\n" +
                "Upper," + START + ",\"Line 1\r\nLine 2, \"\"quoted\"\"\",Bench,1,\"Slow, pause\",1,dropset,90,5,,,8\r\n" +
                "Upper," + START + ",\"Line 1\r\nLine 2, \"\"quoted\"\"\",Bench,1,\"Slow, pause\",0,failure,100,6,,,9\r\n");
        assertEquals(1, workouts.size()); var workout = workouts.get(0);
        assertEquals("Line 1\r\nLine 2, \"quoted\"", workout.notes);
        assertEquals(Instant.parse("2026-10-14T21:00:00Z").toEpochMilli(), workout.started);
        assertEquals(2, workout.exercises.size());
        assertEquals("Slow, pause\n\nSuperset: 1", workout.exercises.get(0).notes);
        var sets = workout.exercises.get(0).sets;
        assertEquals(SetType.WARMUP, sets.get(0).getSetType());
        assertEquals(SetType.DROP, sets.get(1).getSetType());
        assertEquals(SetType.FAILURE, workout.exercises.get(1).sets.get(0).getSetType());
        assertTrue(sets.get(0).isCompleted()); assertEquals(Unit.LB, sets.get(0).getUnit());
    }
    @Test void preservesTimedDistanceAndBodyweightSetsAndConvertsDistanceUnits() throws Exception {
        var workout = parse(HEADER + row("Run", 0, "normal", "", "", "2.5", "1200") +
                row("Plank", 0, "normal", "", "", "", "90") + row("Pushups", 0, "normal", "", "12", "", "")).get(0);
        var run = workout.exercises.get(0).sets.get(0);
        assertEquals(4023.36, run.getDistanceMeters(), .00001); assertEquals(1200, run.getDurationSeconds());
        assertEquals(0, run.getReps()); assertEquals(0, run.getWeight());
        assertNull(workout.exercises.get(1).sets.get(0).getDistanceMeters());
        assertEquals(12, workout.exercises.get(2).sets.get(0).getReps());
        var metric = parse((HEADER + row("Run", 0, "normal", "20", "", "2.5", "300"))
                .replace("weight_lbs", "weight_kg").replace("distance_miles", "distance_km")).get(0).exercises.get(0).sets.get(0);
        assertEquals(Unit.KG, metric.getUnit()); assertEquals(2500, metric.getDistanceMeters());
    }
    @Test void stableReceiptMatchesLegacyHashAndHasNoOldDateCutoff() throws Exception {
        var workout = parse((HEADER + row("Bench", 0, "normal", "100", "5", "", ""))
                .replace("14 Oct 2026", "1 Jan 2020")).get(0);
        assertEquals(HevyCsvParser.sourceKey(workout.started, "Upper"), workout.sourceKey);
        assertEquals(80, workout.sourceKey.length());
        assertNotEquals(workout.sourceKey, HevyCsvParser.sourceKey(workout.started, "Lower"));
    }
    @Test void rejectsInvalidValuesQuotesIndicesDatesAndAmbiguousTime() {
        for (String bad : List.of(
                HEADER + row("Bench", 0, "unknown", "10", "5", "", ""),
                HEADER + row("Bench", 1, "normal", "10", "5", "", ""),
                HEADER + row("Bench", 0, "normal", "NaN", "5", "", ""),
                HEADER + row("Bench", 0, "normal", "10", "-1", "", ""),
                HEADER + row("Run", 0, "normal", "", "", "-5", "10"),
                HEADER + row("Run", 0, "normal", "", "", "1", "1.5"),
                HEADER + row("Run", 0, "normal", "", "", "1", "10").replace("14 Oct 2026, 17:00", "1 Nov 2026, 01:30"),
                HEADER + "\"unclosed",
                HEADER.replace("weight_lbs", "unexpected") + row("Bench", 0, "normal", "10", "5", "", ""))) {
            assertThrows(IllegalArgumentException.class, () -> parse(bad), bad);
        }
    }
}
