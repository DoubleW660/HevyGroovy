package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.ActiveSetEntry;
import com.example.hevygroovy.dto.PreviousExercisePerformance;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the actual Android SQLite window/aggregate queries against isolated storage. */
@RunWith(AndroidJUnit4.class)
public class PreviousExercisePerformancePersistenceTest {
    private SQLiteDatabase db;
    private SQLiteLoggedWorkoutRepository workouts;
    private SQLiteWorkoutExerciseRepository exercises;
    private SQLiteSetEntryRepository sets;
    private SQLitePersonalRecordRepository records;

    @Before public void openIsolatedDatabase() {
        db = SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        WorkoutDatabase schema = new WorkoutDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try { schema.onCreate(db); } finally { schema.close(); }
        workouts = new SQLiteLoggedWorkoutRepository(db);
        exercises = new SQLiteWorkoutExerciseRepository(db);
        sets = new SQLiteSetEntryRepository(db);
        records = new SQLitePersonalRecordRepository(db);
    }

    @After public void closeIsolatedDatabase() { if (db != null) db.close(); }

    @Test public void latestOwnedCompletedPerformanceUsesFinishThenIdAcrossTemplatesWithoutChangingNotes() {
        long first = workout(1, 100, 3000L, 10L);
        set(row(first, 9, 0, "First routine"), 1, 50, Unit.KG, 8, SetType.NORMAL, true);
        // Later start but earlier finish must not supersede the first workout.
        long laterStart = workout(1, 2000, 2500L, 20L);
        set(row(laterStart, 9, 0, "Earlier finish"), 1, 55, Unit.KG, 8, SetType.NORMAL, true);
        long tieWinner = workout(1, 100, 3000L, null);
        set(row(tieWinner, 9, 0, "Latest saved notes"), 1, 135, Unit.LB, 6, SetType.NORMAL, true);
        long placeholders = workout(1, 100, 4000L, 30L);
        set(row(placeholders, 9, 0, "Unperformed note"), 1, 999, Unit.KG, 99, SetType.NORMAL, false);
        long otherUser = workout(2, 100, 9000L, 40L);
        set(row(otherUser, 9, 0, "Other user"), 1, 999, Unit.KG, 99, SetType.NORMAL, true);
        long active = workout(1, 100, null, 50L);
        set(row(active, 9, 0, "Active note"), 1, 999, Unit.KG, 99, SetType.NORMAL, true);
        Map<Long, String> notesBefore = exercises.findLastNotesForUserExercises(1, List.of(9L));

        PreviousExercisePerformance result = exercises.findPreviousPerformancesForUserExercises(1, List.of(9L)).get(9L);

        assertEquals(tieWinner, result.getWorkoutId());
        assertEquals(3000L, result.getEndedAtEpochMillis());
        assertEquals(1, result.getSetsByOccurrence().size());
        ActiveSetEntry previous = result.getSetsByOccurrence().get(0).get(0);
        assertEquals(135, previous.getWeight(), 0D);
        assertEquals(Unit.LB, previous.getUnit());
        assertEquals(6, previous.getReps());
        assertEquals("Latest saved notes", notesBefore.get(9L));
        assertEquals(notesBefore, exercises.findLastNotesForUserExercises(1, List.of(9L)));
        assertTrue(exercises.findPreviousPerformancesForUserExercises(3, List.of(9L)).isEmpty());
    }

    @Test public void repeatedRowsKeepEmptyOccurrencesAndCompletedWarmupAndWorkingSetsInOriginalOrder() {
        long finished = workout(1, 100, 2000L, null);
        long empty = row(finished, 9, 0, null);
        set(empty, 1, 100, Unit.KG, 8, SetType.NORMAL, false);
        long warmup = row(finished, 9, 1, "Warmup note");
        set(warmup, 1, 20, Unit.KG, 10, SetType.WARMUP, true);
        long working = row(finished, 9, 2, "Working note");
        long third = set(working, 3, 135, Unit.LB, 6, SetType.DROP, true);
        long first = set(working, 1, 60, Unit.KG, 8, SetType.NORMAL, true);
        set(working, 2, 99, Unit.KG, 99, SetType.NORMAL, false);
        long separateExercise = row(finished, 10, 3, null);
        set(separateExercise, 1, 80, Unit.KG, 5, SetType.FAILURE, true);

        Map<Long, PreviousExercisePerformance> result = exercises
                .findPreviousPerformancesForUserExercises(1, List.of(9L, 10L, 9L));

        List<List<ActiveSetEntry>> occurrences = result.get(9L).getSetsByOccurrence();
        assertEquals(2, result.size());
        assertEquals(3, occurrences.size());
        assertTrue(occurrences.get(0).isEmpty());
        assertEquals(SetType.WARMUP, occurrences.get(1).get(0).getSetType());
        assertEquals(2, occurrences.get(2).size());
        assertEquals(first, occurrences.get(2).get(0).getId());
        assertEquals(1, occurrences.get(2).get(0).getSetNumber());
        assertEquals(third, occurrences.get(2).get(1).getId());
        assertEquals(3, occurrences.get(2).get(1).getSetNumber());
        assertEquals(Unit.LB, occurrences.get(2).get(1).getUnit());
        assertEquals(SetType.DROP, occurrences.get(2).get(1).getSetType());
        assertTrue(occurrences.get(2).get(1).isCompleted());
        assertEquals(SetType.FAILURE, result.get(10L).getSetsByOccurrence().get(0).get(0).getSetType());
        assertThrows(UnsupportedOperationException.class, () -> occurrences.clear());
        assertThrows(UnsupportedOperationException.class, () -> occurrences.get(2).clear());
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test public void previousPerformanceHandlesMultipleBatchesAndMissingExercises() {
        long finished = workout(1, 100, 2000L, null);
        set(row(finished, 9, 0, null), 1, 50, Unit.KG, 8, SetType.NORMAL, true);
        set(row(finished, 510, 1, null), 1, 80, Unit.KG, 5, SetType.NORMAL, true);
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 510; id++) ids.add(id);
        ids.add(9L);
        Map<Long, PreviousExercisePerformance> result = exercises.findPreviousPerformancesForUserExercises(1, ids);
        assertEquals(2, result.size());
        assertEquals(finished, result.get(9L).getWorkoutId());
        assertEquals(finished, result.get(510L).getWorkoutId());
        assertTrue(exercises.findPreviousPerformancesForUserExercises(1, List.of()).isEmpty());
        assertTrue(exercises.findPreviousPerformancesForUserExercises(1, List.of(999L)).isEmpty());
    }

    @Test public void estimatedMaxNormalizesPoundsAndExcludesWarmupsHighRepsUnfinishedAndOtherUsers() {
        long finished = workout(1, 100, 2000L, null);
        long exercise = row(finished, 9, 0, null);
        set(exercise, 1, 100, Unit.KG, 1, SetType.NORMAL, true);
        set(exercise, 2, 225, Unit.LB, 5, SetType.FAILURE, true);
        set(exercise, 3, 900, Unit.KG, 5, SetType.WARMUP, true);
        set(exercise, 4, 900, Unit.KG, 11, SetType.NORMAL, true);
        set(exercise, 5, 900, Unit.KG, 5, SetType.NORMAL, false);
        set(exercise, 6, 900, Unit.KG, 0, SetType.NORMAL, true);
        set(exercise, 7, 0, Unit.KG, 5, SetType.NORMAL, true);
        long other = workout(2, 100, 3000L, null);
        set(row(other, 9, 0, null), 1, 900, Unit.KG, 5, SetType.NORMAL, true);
        long active = workout(1, 100, null, null);
        set(row(active, 9, 0, null), 1, 900, Unit.KG, 5, SetType.NORMAL, true);

        assertEquals(225 * 0.45359237 * (1 + 5 / 30.0),
                records.findBestEstimatedOneRepMax(1, 9), 0.000001);
        assertEquals(0, records.findBestEstimatedOneRepMax(1, 999), 0D);
        assertEquals(0, records.findBestEstimatedOneRepMax(3, 9), 0D);
    }

    @Test public void estimatedSingleIsActualWeightAndTenRepsAreIncludedWithoutInflatingSingles() {
        long finished = workout(1, 100, 2000L, null);
        set(row(finished, 9, 0, null), 1, 100, Unit.KG, 1, SetType.NORMAL, true);
        set(row(finished, 10, 1, null), 1, 60, Unit.KG, 10, SetType.DROP, true);
        assertEquals(100, records.findBestEstimatedOneRepMax(1, 9), 0.000001);
        assertEquals(80, records.findBestEstimatedOneRepMax(1, 10), 0.000001);
    }

    private long workout(long userId, long started, Long ended, Long templateId) {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(userId); workout.setStartedAtEpochMillis(started);
        workout.setEndedAtEpochMillis(ended); workout.setTemplateId(templateId);
        return workouts.save(workout).getId();
    }

    private long row(long workoutId, long exerciseId, int order, String notes) {
        WorkoutExercise row = new WorkoutExercise();
        row.setLoggedWorkoutId(workoutId); row.setExerciseId(exerciseId);
        row.setOrderIndex(order); row.setNotes(notes); row.setExerciseNameSnapshot("Previous exercise");
        return exercises.save(row).getId();
    }

    private long set(long rowId, int number, double weight, Unit unit, int reps, SetType type, boolean completed) {
        SetEntry set = new SetEntry();
        set.setWorkoutExerciseId(rowId); set.setSetNumber(number); set.setWeight(weight); set.setUnit(unit);
        set.setReps(reps); set.setSetType(type); set.setCompleted(completed); set.setCreatedAtEpochMillis(100);
        return sets.save(set).getId();
    }
}
