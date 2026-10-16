package com.example.hevygroovy.persistence;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real file-backed queries; personal workouts and the bundled exercise catalog are untouched. */
@RunWith(AndroidJUnit4.class)
public class PersonalRecordPersistenceTest {
    private Context context;
    private String database;
    private SQLiteAppStorage storage;
    private long exerciseId;

    @Before public void openIsolatedStorage() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = "personal-records-" + UUID.randomUUID() + ".db";
        storage = new SQLiteAppStorage(context, database);
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle("Record bench press");
        request.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        exerciseId = storage.getExercises().createExercise(request).getExerciseId();
    }

    @After public void closeIsolatedStorage() {
        if (storage != null) storage.close();
        context.deleteDatabase(database);
    }

    @Test public void equivalentPoundsAndKilogramsTieAndOriginalUnitIsPreserved() {
        long baseline = workout(1);
        long baselineRow = row(1, baseline, exerciseId);
        set(1, baselineRow, 100, Unit.KG, 5, SetType.NORMAL, true);
        finish(1, baseline);

        long active = workout(1);
        long activeRow = row(1, active, exerciseId);
        long candidate = set(1, activeRow, 100 / 0.45359237, Unit.LB, 5, SetType.NORMAL, true);
        assertTrue(storage.getPersonalRecords().getWorkoutRecords(1, active).isEmpty());

        UpdateSetEntryRequest heavier = new UpdateSetEntryRequest();
        heavier.setWeight(224D);
        heavier.setReps(4);
        storage.getSetEntries().updateSet(1, candidate, heavier);
        List<PersonalRecord> pending = storage.getPersonalRecords().getWorkoutRecords(1, active);
        assertEquals(1, pending.size());
        PersonalRecord weight = record(pending, RecordType.HEAVIEST_WEIGHT);
        assertEquals(224 * 0.45359237, weight.getValue(), 0.000001);
        assertEquals(224, weight.getWeight(), 0D);
        assertEquals(Unit.LB, weight.getUnit());
        assertEquals(candidate, weight.getSetId());
        // Active improvements are visible in the session, but do not replace saved bests yet.
        assertEquals(baseline, record(storage.getPersonalRecords().getExerciseRecords(1, exerciseId),
                RecordType.HEAVIEST_WEIGHT).getWorkoutId());
    }

    @Test public void warmupsUnfinishedSetsAndZeroRepsNeverCreateRecords() {
        long active = workout(1);
        long activeRow = row(1, active, exerciseId);
        set(1, activeRow, 999, Unit.KG, 99, SetType.WARMUP, true);
        set(1, activeRow, 999, Unit.KG, 99, SetType.NORMAL, false);
        set(1, activeRow, 999, Unit.KG, 0, SetType.NORMAL, true);
        long performed = set(1, activeRow, 50, Unit.KG, 5, SetType.NORMAL, true);
        List<PersonalRecord> pending = storage.getPersonalRecords().getWorkoutRecords(1, active);
        assertEquals(4, pending.size());
        assertEquals(performed, record(pending, RecordType.HEAVIEST_WEIGHT).getSetId());
        assertEquals(50D, record(pending, RecordType.HEAVIEST_WEIGHT).getValue(), 0D);
        assertEquals(5D, record(pending, RecordType.MOST_REPS).getValue(), 0D);
        assertEquals(250D, record(pending, RecordType.BEST_SET_VOLUME).getValue(), 0D);
        assertEquals(250D, record(pending, RecordType.WORKOUT_VOLUME).getValue(), 0D);
    }

    @Test public void duplicateExerciseRowsCombineOnlyTheirOwnWorkoutVolume() {
        long baseline = workout(1);
        set(1, row(1, baseline, exerciseId), 80, Unit.KG, 5, SetType.NORMAL, true);
        finish(1, baseline);
        long active = workout(1);
        set(1, row(1, active, exerciseId), 50, Unit.KG, 5, SetType.NORMAL, true);
        set(1, row(1, active, exerciseId), 40, Unit.KG, 5, SetType.DROP, true);
        CreateExerciseRequest other = new CreateExerciseRequest();
        other.setTitle("Separate squat");
        other.setPrimaryMuscleGroup(MuscleGroup.QUADRICEPS);
        long otherExercise = storage.getExercises().createExercise(other).getExerciseId();
        set(1, row(1, active, otherExercise), 200, Unit.KG, 20, SetType.NORMAL, true);

        List<PersonalRecord> pending = storage.getPersonalRecords().getWorkoutRecords(1, active);
        long matching = pending.stream().filter(item -> item.getExerciseId() == exerciseId).count();
        assertEquals(1, matching);
        PersonalRecord volume = pending.stream().filter(item -> item.getExerciseId() == exerciseId)
                .findFirst().orElseThrow();
        assertEquals(RecordType.WORKOUT_VOLUME, volume.getType());
        assertEquals(450D, volume.getValue(), 0D);
        assertEquals(10, volume.getReps());
        assertEquals(0L, volume.getSetId());
        finish(1, active);
        assertEquals(450D, record(storage.getPersonalRecords().getExerciseRecords(1, exerciseId),
                RecordType.WORKOUT_VOLUME).getValue(), 0D);
    }

    @Test public void historicalAwardsUseEarlierWorkoutsRatherThanTodaysBest() {
        long earliest = workout(1);
        set(1, row(1, earliest, exerciseId), 40, Unit.KG, 5, SetType.NORMAL, true);
        finish(1, earliest);
        long latest = workout(1);
        set(1, row(1, latest, exerciseId), 60, Unit.KG, 7, SetType.NORMAL, true);
        finish(1, latest);
        // Insert the middle-dated workout last to prove chronology comes from saved time, not ID.
        long middle = workout(1);
        set(1, row(1, middle, exerciseId), 50, Unit.KG, 6, SetType.NORMAL, true);
        finish(1, middle);
        try (WorkoutDatabase helper = new WorkoutDatabase(context, database)) {
            helper.getWritableDatabase().execSQL("UPDATE logged_workout SET started_at=?, ended_at=? WHERE id=?",
                    new Object[]{1000L, 1100L, earliest});
            helper.getWritableDatabase().execSQL("UPDATE logged_workout SET started_at=?, ended_at=? WHERE id=?",
                    new Object[]{3000L, 3100L, latest});
            helper.getWritableDatabase().execSQL("UPDATE logged_workout SET started_at=?, ended_at=? WHERE id=?",
                    new Object[]{2000L, 2100L, middle});
        }
        assertEquals(4, storage.getPersonalRecords().getWorkoutRecords(1, earliest).size());
        assertEquals(4, storage.getPersonalRecords().getWorkoutRecords(1, middle).size());
        assertEquals(4, storage.getPersonalRecords().getWorkoutRecords(1, latest).size());
        assertEquals(latest, record(storage.getPersonalRecords().getExerciseRecords(1, exerciseId),
                RecordType.HEAVIEST_WEIGHT).getWorkoutId());
    }

    @Test public void recordsSurviveReopenWhileUncompletedAndDiscardedImprovementsDoNotPromote() {
        long baseline = workout(1);
        set(1, row(1, baseline, exerciseId), 90, Unit.LB, 5, SetType.NORMAL, true);
        finish(1, baseline);
        storage.close();
        storage = new SQLiteAppStorage(context, database);
        assertEquals(90D, record(storage.getPersonalRecords().getExerciseRecords(1, exerciseId),
                RecordType.HEAVIEST_WEIGHT).getWeight(), 0D);

        long active = workout(1);
        long candidate = set(1, row(1, active, exerciseId), 100, Unit.LB, 6, SetType.FAILURE, true);
        assertEquals(4, storage.getPersonalRecords().getWorkoutRecords(1, active).size());
        UpdateSetEntryRequest unchecked = new UpdateSetEntryRequest();
        unchecked.setCompleted(false);
        storage.getSetEntries().updateSet(1, candidate, unchecked);
        assertTrue(storage.getPersonalRecords().getWorkoutRecords(1, active).isEmpty());
        UpdateSetEntryRequest checked = new UpdateSetEntryRequest();
        checked.setCompleted(true);
        storage.getSetEntries().updateSet(1, candidate, checked);
        assertEquals(4, storage.getPersonalRecords().getWorkoutRecords(1, active).size());
        storage.getWorkoutSessions().discardWorkout(1, active);
        storage.close();
        storage = new SQLiteAppStorage(context, database);
        List<PersonalRecord> saved = storage.getPersonalRecords().getExerciseRecords(1, exerciseId);
        assertEquals(4, saved.size());
        assertEquals(baseline, record(saved, RecordType.HEAVIEST_WEIGHT).getWorkoutId());
        assertEquals(90D, record(saved, RecordType.HEAVIEST_WEIGHT).getWeight(), 0D);
    }

    @Test public void anotherUsersBestsNeverBecomeTheCurrentUsersBaseline() {
        long privateWorkout = workout(2);
        set(2, row(2, privateWorkout, exerciseId), 999, Unit.KG, 99, SetType.NORMAL, true);
        finish(2, privateWorkout);
        assertTrue(storage.getPersonalRecords().getExerciseRecords(1, exerciseId).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> storage.getPersonalRecords().getWorkoutRecords(1, privateWorkout));
        long ownWorkout = workout(1);
        set(1, row(1, ownWorkout, exerciseId), 40, Unit.KG, 5, SetType.NORMAL, true);
        assertEquals(4, storage.getPersonalRecords().getWorkoutRecords(1, ownWorkout).size());
        finish(1, ownWorkout);
        assertEquals(40D, record(storage.getPersonalRecords().getExerciseRecords(1, exerciseId),
                RecordType.HEAVIEST_WEIGHT).getValue(), 0D);
        assertEquals(999D, record(storage.getPersonalRecords().getExerciseRecords(2, exerciseId),
                RecordType.HEAVIEST_WEIGHT).getValue(), 0D);
    }

    private long workout(long user) { return storage.getWorkoutSessions().startEmptyWorkout(user).getId(); }
    private long row(long user, long workout, long exercise) {
        return storage.getWorkoutSessions().addExerciseToWorkout(user, workout, exercise).getId();
    }
    private void finish(long user, long workout) { storage.getWorkoutSessions().finishWorkout(user, workout); }
    private long set(long user, long row, double weight, Unit unit, int reps, SetType type, boolean completed) {
        long id = storage.getSetEntries().addSet(user, row).getId();
        UpdateSetEntryRequest update = new UpdateSetEntryRequest();
        update.setWeight(weight);
        update.setUnit(unit);
        update.setReps(reps);
        update.setSetType(type);
        update.setCompleted(completed);
        storage.getSetEntries().updateSet(user, id, update);
        return id;
    }
    private PersonalRecord record(List<PersonalRecord> records, RecordType type) {
        return records.stream().filter(item -> item.getType() == type).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type));
    }
}
