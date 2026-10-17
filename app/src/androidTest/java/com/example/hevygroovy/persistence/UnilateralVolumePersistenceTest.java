package com.example.hevygroovy.persistence;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.enums.*;
import java.util.List;
import java.util.UUID;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UnilateralVolumePersistenceTest {
    private Context context;
    private String database;
    private SQLiteAppStorage storage;

    @Before public void open() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = "unilateral-" + UUID.randomUUID() + ".db";
        storage = new SQLiteAppStorage(context, database);
    }

    @After public void close() {
        if (storage != null) storage.close();
        context.deleteDatabase(database);
    }

    @Test public void changingMovementTypeRecalculatesHistoryAndRecordsAndSurvivesReopening() {
        long exercise = exercise("Single leg press", false);
        long workout = storage.getWorkoutSessions().startEmptyWorkout(1).getId();
        long row = row(1, workout, exercise);
        set(1, row, 140, Unit.LB, 17, SetType.NORMAL, true);
        storage.getWorkoutSessions().finishWorkout(1, workout);
        assertVolume(workout, 2380 * 0.45359237);

        mode(exercise, true);
        assertVolume(workout, 4760 * 0.45359237);
        WorkoutExerciseHistory detail = storage.getHistory().getWorkoutHistoryDetail(1, workout).getExercises().get(0);
        assertTrue(detail.isUnilateral());
        assertEquals(17, detail.getTotalReps());
        assertEquals(140, detail.getSets().get(0).getWeight(), 0D);
        assertEquals(Unit.LB, detail.getSets().get(0).getUnit());
        assertEquals(140 * 0.45359237, detail.getHeaviestWeight(), 0.000001);
        List<PersonalRecord> records = storage.getPersonalRecords().getExerciseRecords(1, exercise);
        assertRecord(records, RecordType.HEAVIEST_WEIGHT, 140 * 0.45359237);
        assertRecord(records, RecordType.MOST_REPS, 17);
        assertRecord(records, RecordType.BEST_SET_VOLUME, 4760 * 0.45359237);
        assertRecord(records, RecordType.WORKOUT_VOLUME, 4760 * 0.45359237);

        UpdateExerciseRequest rename = new UpdateExerciseRequest();
        rename.setTitle("My per-side press");
        storage.getExercises().updateExercise(exercise, rename);
        storage.getExercises().archiveExercise(exercise);
        storage.close();
        storage = new SQLiteAppStorage(context, database);
        assertTrue(storage.getExercises().getExercise(exercise).isUnilateral());
        assertTrue(storage.getExercises().getExercise(exercise).isArchived());
        assertVolume(workout, 4760 * 0.45359237);
        mode(exercise, false);
        assertVolume(workout, 2380 * 0.45359237);
        assertRecord(storage.getPersonalRecords().getExerciseRecords(1, exercise),
                RecordType.WORKOUT_VOLUME, 2380 * 0.45359237);
        assertFalse(storage.getHistory().getWorkoutHistoryDetail(1, workout).getExercises().get(0).isUnilateral());
    }

    @Test public void mixedUnitsRepeatedRowsAndEligibilityUseMultiplierOnceWithoutDoublingRepsOrEstimates() {
        long unilateral = exercise("Per-side press", true);
        long bilateral = exercise("Bilateral press", false);
        long otherUser = storage.getWorkoutSessions().startEmptyWorkout(2).getId();
        set(2, row(2, otherUser, unilateral), 999, Unit.KG, 99, SetType.NORMAL, true);
        storage.getWorkoutSessions().finishWorkout(2, otherUser);

        long workout = storage.getWorkoutSessions().startEmptyWorkout(1).getId();
        long first = row(1, workout, unilateral);
        set(1, first, 140, Unit.LB, 17, SetType.FAILURE, true);
        set(1, first, 5, Unit.KG, 2, SetType.WARMUP, true);
        set(1, first, 999, Unit.KG, 99, SetType.NORMAL, false);
        set(1, row(1, workout, unilateral), 20, Unit.KG, 5, SetType.DROP, true);
        set(1, row(1, workout, bilateral), 10, Unit.KG, 3, SetType.NORMAL, true);

        List<PersonalRecord> provisional = storage.getPersonalRecords().getWorkoutRecords(1, workout).stream()
                .filter(record -> record.getExerciseId() == unilateral).collect(java.util.stream.Collectors.toList());
        double workingVolume = 4760 * 0.45359237 + 200;
        assertRecord(provisional, RecordType.WORKOUT_VOLUME, workingVolume);
        storage.getWorkoutSessions().finishWorkout(1, workout);
        assertVolume(workout, workingVolume + 20 + 30); // Completed warmups contribute to history, not PRs.
        WorkoutHistoryDetail history = storage.getHistory().getWorkoutHistoryDetail(1, workout);
        assertEquals(4, history.getTotalSets());
        assertEquals(27, history.getTotalReps());
        assertTrue(history.getExercises().get(0).isUnilateral());
        assertTrue(history.getExercises().get(1).isUnilateral());
        assertFalse(history.getExercises().get(2).isUnilateral());
        assertEquals(20 * (1 + 5 / 30.0), storage.getPersonalRecords().getEstimatedOneRepMax(1, unilateral), 0.000001);
        assertEquals(20 * (1 + 5 / 30.0), history.getExercises().get(1).getEstimatedOneRepMax(), 0.000001);
        List<PersonalRecord> saved = storage.getPersonalRecords().getExerciseRecords(1, unilateral);
        assertRecord(saved, RecordType.WORKOUT_VOLUME, workingVolume);
        assertRecord(saved, RecordType.BEST_SET_VOLUME, 4760 * 0.45359237);
        assertRecord(saved, RecordType.MOST_REPS, 17);
        assertRecord(saved, RecordType.HEAVIEST_WEIGHT, 140 * 0.45359237);
        mode(unilateral, false);
        assertEquals(20 * (1 + 5 / 30.0), storage.getPersonalRecords().getEstimatedOneRepMax(1, unilateral), 0.000001);
        assertVolume(workout, workingVolume / 2 + 10 + 30);
    }

    private long exercise(String title, boolean unilateral) {
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle(title); request.setPrimaryMuscleGroup(MuscleGroup.QUADRICEPS);
        request.setUnilateral(unilateral);
        return storage.getExercises().createExercise(request).getExerciseId();
    }
    private void mode(long exercise, boolean unilateral) {
        UpdateExerciseRequest request = new UpdateExerciseRequest(); request.setUnilateral(unilateral);
        storage.getExercises().updateExercise(exercise, request);
    }
    private long row(long user, long workout, long exercise) {
        return storage.getWorkoutSessions().addExerciseToWorkout(user, workout, exercise).getId();
    }
    private void set(long user, long row, double weight, Unit unit, int reps, SetType type, boolean complete) {
        long id = storage.getSetEntries().addSet(user, row).getId();
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setWeight(weight); request.setUnit(unit); request.setReps(reps);
        request.setSetType(type); request.setCompleted(complete);
        storage.getSetEntries().updateSet(user, id, request);
    }
    private void assertVolume(long workout, double expected) {
        assertEquals(expected, storage.getHistory().getWorkoutHistoryDetail(1, workout).getTotalVolume(), 0.000001);
        assertEquals(expected, storage.getHistory().getWorkoutHistory(1).stream()
                .filter(item -> item.getWorkoutId() == workout).findFirst().get().getTotalVolume(), 0.000001);
    }
    private void assertRecord(List<PersonalRecord> records, RecordType type, double expected) {
        assertEquals(expected, records.stream().filter(record -> record.getType() == type)
                .findFirst().get().getValue(), 0.000001);
    }
}
