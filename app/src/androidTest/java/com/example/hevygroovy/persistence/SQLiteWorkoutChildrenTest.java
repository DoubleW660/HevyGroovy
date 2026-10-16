package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SQLiteWorkoutChildrenTest {
    @Test
    public void placeholdersDoNotQualifyForFinishOrHistoricalNotes() {
        long workout = workout(1L, 100L);
        WorkoutExercise row = exercise(workout, 9L, 0, "Placeholder note", false);
        SetEntry placeholder = set(row.getId(), 1);
        placeholder.setCompleted(false);
        sets.save(placeholder);
        assertFalse(sets.findById(placeholder.getId()).get().isCompleted());
        assertFalse(sets.existsByLoggedWorkoutId(workout));
        assertTrue(exercises.findLastNotesForUserExercises(1L, List.of(9L)).isEmpty());
        placeholder.setCompleted(true);
        sets.save(placeholder);
        assertTrue(sets.existsByLoggedWorkoutId(workout));
        assertEquals("Placeholder note", exercises.findLastNotesForUserExercises(1L, List.of(9L)).get(9L));
    }

    @Test
    public void versionTwoMigrationPreservesExistingSetsAsCompleted() {
        // Reproduce the v2 set table; other tables already have their pre-v3 layout.
        db.execSQL("DROP TABLE set_entry");
        db.execSQL("CREATE TABLE set_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "workout_exercise_id INTEGER NOT NULL REFERENCES workout_exercise(id), "
                + "set_number INTEGER NOT NULL, reps INTEGER NOT NULL, weight REAL NOT NULL, "
                + "unit TEXT NOT NULL, rest_seconds INTEGER, rpe REAL, set_type TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL)");
        long workout = workout(1L, 100L);
        WorkoutExercise row = exercise(workout, 9L, 0, "Legacy note", false);
        db.execSQL("INSERT INTO set_entry (workout_exercise_id, set_number, reps, weight, unit, set_type, created_at) "
                + "VALUES (?, 1, 5, 100, 'LB', 'NORMAL', 10)", new Object[]{row.getId()});
        WorkoutDatabase helper = new WorkoutDatabase(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        helper.onUpgrade(db, 2, 3);
        helper.close();
        assertTrue(sets.findByWorkoutExerciseId(row.getId()).get(0).isCompleted());
        assertTrue(sets.existsByLoggedWorkoutId(workout));
        SetEntry fresh = new SetEntry();
        fresh.setWorkoutExerciseId(row.getId());
        fresh.setSetNumber(2);
        fresh.setUnit(Unit.LB);
        fresh.setSetType(SetType.NORMAL);
        sets.save(fresh);
        assertFalse(sets.findById(fresh.getId()).get().isCompleted());
    }
    private SQLiteDatabase db;
    private SQLiteLoggedWorkoutRepository workouts;
    private SQLiteWorkoutExerciseRepository exercises;
    private SQLiteSetEntryRepository sets;

    @Before
    public void setUp() {
        db = SQLiteDatabase.create(null);
        WorkoutDatabase helper = new WorkoutDatabase(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        helper.onConfigure(db);
        helper.onCreate(db);
        helper.close();
        workouts = new SQLiteLoggedWorkoutRepository(db);
        exercises = new SQLiteWorkoutExerciseRepository(db);
        sets = new SQLiteSetEntryRepository(db);
    }

    @After
    public void tearDown() { db.close(); }

    @Test
    public void exerciseRoundTripOrderingUpdateAndDelete() {
        long workout = workout(1L, 100L);
        assertEquals(-1, exercises.findMaxOrderIndex(workout));
        WorkoutExercise later = exercise(workout, 9L, 4, "old", false);
        WorkoutExercise earlier = exercise(workout, 8L, 1, null, false);
        assertEquals(earlier.getId(), exercises.findByLoggedWorkoutId(workout).get(0).getId());
        assertEquals(4, exercises.findMaxOrderIndex(workout));
        later.setNotes("");
        later.setRestTimeSeconds(90);
        exercises.save(later);
        WorkoutExercise loaded = exercises.findById(later.getId()).get();
        assertEquals("Exercise 9", loaded.getExerciseNameSnapshot());
        assertEquals(9L, loaded.getExerciseId());
        assertEquals(workout, loaded.getLoggedWorkoutId());
        assertEquals(90, loaded.getRestTimeSeconds());
        assertEquals("", loaded.getNotes());
        exercises.delete(later.getId());
        assertFalse(exercises.findById(later.getId()).isPresent());
        assertThrows(IllegalStateException.class, () -> exercises.save(later));
    }

    @Test
    public void setRoundTripOrderingExistenceAndForeignKeys() {
        long workout = workout(1L, 100L);
        WorkoutExercise row = exercise(workout, 9L, 0, null, false);
        assertFalse(sets.existsByLoggedWorkoutId(workout));
        SetEntry later = set(row.getId(), 2);
        SetEntry earlier = set(row.getId(), 1);
        later.setRestSeconds(60);
        later.setRpe(8.5F);
        later.setUnit(Unit.KG);
        later.setSetType(SetType.DROP);
        sets.save(later);
        SetEntry loaded = sets.findById(later.getId()).get();
        assertEquals(5, loaded.getReps());
        assertEquals(100D, loaded.getWeight(), 0D);
        assertEquals(Unit.KG, loaded.getUnit());
        assertEquals(SetType.DROP, loaded.getSetType());
        assertEquals(Float.valueOf(8.5F), loaded.getRpe());
        assertEquals(Integer.valueOf(60), loaded.getRestSeconds());
        assertEquals(10L, loaded.getCreatedAtEpochMillis());
        assertNull(sets.findById(earlier.getId()).get().getRpe());
        assertNull(sets.findById(earlier.getId()).get().getRestSeconds());
        assertEquals(earlier.getId(), sets.findByWorkoutExerciseId(row.getId()).get(0).getId());
        assertEquals(2, sets.findByLoggedWorkoutId(workout).size());
        assertTrue(sets.existsByLoggedWorkoutId(workout));
        assertFalse(sets.existsByLoggedWorkoutId(workout(2L, 100L)));
        assertThrows(SQLiteConstraintException.class, () -> exercises.delete(row.getId()));
        sets.delete(later.getId());
        sets.delete(earlier.getId());
        assertFalse(sets.existsByLoggedWorkoutId(workout));
        assertThrows(IllegalStateException.class, () -> sets.save(later));
        exercises.delete(row.getId());
        assertThrows(SQLiteConstraintException.class, () -> set(row.getId(), 1));
    }

    @Test
    public void historyScopesUserRequiresSetsAndCompletedWorkoutsAcrossTemplates() {
        long old = workout(1L, 100L);
        exercise(old, 9L, 0, "old", true);
        long latest = workout(1L, 200L);
        exercise(latest, 9L, 0, "latest", true);
        exercise(workout(2L, 500L), 9L, 0, "other user", true);
        exercise(workout(1L, 600L), 9L, 0, "no sets", false);
        exercise(workout(1L, null), 9L, 0, "active", true);
        assertEquals("latest", exercises.findLastNotesForUserExercises(1L, List.of(9L)).get(9L));
        assertTrue(exercises.findLastNotesForUserExercises(1L, List.of()).isEmpty());
        assertTrue(exercises.findLastNotesForUserExercises(1L, List.of(999L)).isEmpty());
    }

    @Test
    public void historyUsesDeterministicTiesAndPreservesClearedNotes() {
        exercise(workout(1L, 100L), 9L, 0, "old", true);
        long latest = workout(1L, 100L);
        exercise(latest, 9L, 0, "first occurrence", true);
        WorkoutExercise cleared = exercise(latest, 9L, 1, null, true);
        Map<Long, String> notes = exercises.findLastNotesForUserExercises(1L, List.of(9L, 9L));
        assertTrue(notes.containsKey(9L));
        assertNull(notes.get(9L));
        cleared.setNotes("");
        exercises.save(cleared);
        assertEquals("", exercises.findLastNotesForUserExercises(1L, List.of(9L)).get(9L));
    }

    private long workout(long user, Long ended) {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(user);
        workout.setStartedAtEpochMillis(1L);
        workout.setEndedAtEpochMillis(ended);
        return workouts.save(workout).getId();
    }

    private WorkoutExercise exercise(long workout, long exerciseId, int order, String notes, boolean performed) {
        WorkoutExercise row = new WorkoutExercise();
        row.setLoggedWorkoutId(workout);
        row.setExerciseId(exerciseId);
        row.setExerciseNameSnapshot("Exercise " + exerciseId);
        row.setOrderIndex(order);
        row.setNotes(notes);
        exercises.save(row);
        if (performed) { set(row.getId(), 1); }
        return row;
    }

    private SetEntry set(long exercise, int number) {
        SetEntry set = new SetEntry();
        set.setWorkoutExerciseId(exercise);
        set.setSetNumber(number);
        set.setReps(5);
        set.setCompleted(true);
        set.setWeight(100D);
        set.setUnit(Unit.LB);
        set.setSetType(SetType.NORMAL);
        set.setCreatedAtEpochMillis(10L);
        return sets.save(set);
    }
}
