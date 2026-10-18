package com.example.hevygroovy.persistence;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.ActiveSetEntry;
import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import com.example.hevygroovy.dto.ActiveWorkoutExercise;
import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.service.ExerciseService;
import com.example.hevygroovy.service.ExerciseServiceImpl;
import com.example.hevygroovy.service.WorkoutSessionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RoutineSessionRestoreTest {
    private SQLiteDatabase db;
    private WorkoutDatabase helper;
    private SQLiteLoggedWorkoutRepository workouts;
    private SQLiteWorkoutExerciseRepository exercises;
    private SQLiteSetEntryRepository sets;
    private SQLiteTemplateWorkoutRepository templates;
    private SQLiteTemplateExerciseRepository templateExercises;
    private ExerciseService library;
    private WorkoutSessionService sessions;

    @Before public void open() {
        db = SQLiteDatabase.create(null);
        helper = new WorkoutDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext());
        helper.onConfigure(db); helper.onCreate(db);
        workouts = new SQLiteLoggedWorkoutRepository(db);
        exercises = new SQLiteWorkoutExerciseRepository(db);
        sets = new SQLiteSetEntryRepository(db);
        templates = new SQLiteTemplateWorkoutRepository(db);
        templateExercises = new SQLiteTemplateExerciseRepository(db);
        library = new ExerciseServiceImpl(new SQLiteExerciseRepository(db));
        sessions = new SQLiteWorkoutServices(db, templates, templateExercises, library).getWorkoutSessions();
    }

    @After public void close() { db.close(); helper.close(); }

    @Test public void lookupUsesLatestCompletedSameRoutineOccurrenceAndPreservesEmptySlotsAcrossBatches() {
        long older = workout(1, 7, 100L);
        WorkoutExercise firstOld = row(older, 9, 0);
        set(firstOld.getId(), 1, 20, 10, Unit.KG, SetType.WARMUP, true);
        WorkoutExercise secondOld = row(older, 9, 2);
        set(secondOld.getId(), 1, 70, 5, Unit.LB, SetType.NORMAL, true);
        long latest = workout(1, 7, 200L);
        WorkoutExercise firstLatest = row(latest, 9, 0);
        set(firstLatest.getId(), 2, 40, 0, Unit.KG, SetType.FAILURE, true);
        set(firstLatest.getId(), 3, 30, 8, Unit.KG, SetType.DROP, true);
        set(firstLatest.getId(), 4, 999, 99, Unit.LB, SetType.NORMAL, false);
        row(latest, 9, 2); // Its second occurrence was skipped; retain the older second occurrence.
        set(row(workout(1, 8, 900L), 9, 0).getId(), 1, 900, 1, Unit.LB, SetType.NORMAL, true);
        set(row(workout(2, 7, 900L), 9, 0).getId(), 1, 900, 1, Unit.LB, SetType.NORMAL, true);
        set(row(workout(1, 7, null), 9, 0).getId(), 1, 900, 1, Unit.LB, SetType.NORMAL, true);
        set(row(latest, 510, 3).getId(), 1, 50, 5, Unit.KG, SetType.NORMAL, true);
        // A repeated exercise with only a later completed occurrence must retain its empty first slot.
        row(latest, 10, 4);
        set(row(latest, 10, 5).getId(), 1, 80, 5, Unit.LB, SetType.NORMAL, true);
        ArrayList<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 510; id++) ids.add(id);

        Map<Long, List<List<ActiveSetEntry>>> result =
                exercises.findLastCompletedSetsForTemplateExercises(1, 7, ids);

        assertEquals(3, result.size());
        assertEquals(2, result.get(9L).get(0).size());
        assertEquals(SetType.FAILURE, result.get(9L).get(0).get(0).getSetType());
        assertEquals(0, result.get(9L).get(0).get(0).getReps());
        assertEquals(Unit.KG, result.get(9L).get(0).get(0).getUnit());
        assertEquals(SetType.DROP, result.get(9L).get(0).get(1).getSetType());
        assertEquals(70, result.get(9L).get(1).get(0).getWeight(), 0);
        assertTrue(result.get(10L).get(0).isEmpty());
        assertEquals(80, result.get(10L).get(1).get(0).getWeight(), 0);
        assertEquals(50, result.get(510L).get(0).get(0).getWeight(), 0);
        assertThrows(UnsupportedOperationException.class, () -> result.get(9L).get(0).clear());
        assertTrue(exercises.findLastCompletedSetsForTemplateExercises(1, 7, List.of()).isEmpty());
    }

    @Test public void routineRestoresUncheckedSetsAndRestChangesSurviveRebuiltServicesAndNextStart() {
        long exercise = libraryExercise("Single leg press", true);
        long newExercise = libraryExercise("New row", false);
        long routine = template();
        TemplateExercise first = templateRow(routine, exercise, 0, 45);
        TemplateExercise second = templateRow(routine, exercise, 1, 60);
        TemplateExercise differentExercise = templateRow(routine, newExercise, 2, 0);
        long otherRoutine = template();
        TemplateExercise unrelated = templateRow(otherRoutine, exercise, 0, 180);
        long history = workout(1, routine, 100L);
        WorkoutExercise previous = row(history, exercise, 0);
        set(previous.getId(), 1, 20, 10, Unit.KG, SetType.WARMUP, true);
        set(previous.getId(), 3, 90, 5, Unit.LB, SetType.NORMAL, true);
        set(previous.getId(), 4, 999, 99, Unit.LB, SetType.NORMAL, false);
        set(row(history, exercise, 1).getId(), 1, 40, 8, Unit.KG, SetType.DROP, true);
        long active = sessions.startWorkoutFromTemplate(1, routine, Unit.KG).getId();
        ActiveWorkoutDetail detail = sessions.getActiveWorkout(1).orElseThrow();
        assertEquals(3, detail.getExercises().size());
        ActiveWorkoutExercise restored = detail.getExercises().get(0);
        assertTrue(restored.isUnilateral());
        assertEquals(2, restored.getSets().size()); // Reps per side do not double set count.
        assertEquals(SetType.WARMUP, restored.getSets().get(0).getSetType());
        assertEquals(Unit.LB, restored.getSets().get(1).getUnit());
        assertEquals(90, restored.getSets().get(1).getWeight(), 0);
        assertEquals(SetType.DROP, detail.getExercises().get(1).getSets().get(0).getSetType());
        ActiveSetEntry blank = detail.getExercises().get(2).getSets().get(0);
        assertEquals(Unit.KG, blank.getUnit()); assertEquals(0, blank.getWeight(), 0);
        for (ActiveWorkoutExercise current : detail.getExercises())
            for (ActiveSetEntry set : current.getSets()) assertFalse(set.isCompleted());
        assertThrows(RuntimeException.class, () -> sessions.finishWorkout(1, active));
        sessions.updateExerciseDetails(1, detail.getExercises().get(1).getWorkoutExerciseId(), "Next time", 120);
        assertEquals(Integer.valueOf(120), templateExercises.findById(first.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(120), templateExercises.findById(second.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(0), templateExercises.findById(differentExercise.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(180), templateExercises.findById(unrelated.getId()).orElseThrow().getRestTimeSeconds());
        sessions = new SQLiteWorkoutServices(db, templates, templateExercises, library).getWorkoutSessions();
        assertEquals(45, sessions.getActiveWorkout(1).orElseThrow().getExercises().get(0).getRestTimeSeconds());
        assertEquals(120, sessions.getActiveWorkout(1).orElseThrow().getExercises().get(1).getRestTimeSeconds());
        sessions.discardWorkout(1, active);
        sessions.startWorkoutFromTemplate(1, routine, Unit.KG);
        assertEquals(120, sessions.getActiveWorkout(1).orElseThrow().getExercises().get(0).getRestTimeSeconds());
        assertEquals(120, sessions.getActiveWorkout(1).orElseThrow().getExercises().get(1).getRestTimeSeconds());
    }

    @Test public void movingAndRemovingDuplicateActiveExercisesStillRemembersRestForTheRoutineExercise() {
        long bench = libraryExercise("Bench", false);
        long squat = libraryExercise("Squat", false);
        long routine = template();
        TemplateExercise benchFirst = templateRow(routine, bench, 0, 45);
        TemplateExercise benchSecond = templateRow(routine, bench, 1, 60);
        TemplateExercise squatTemplate = templateRow(routine, squat, 2, 90);
        TemplateExercise otherRoutine = templateRow(template(), bench, 0, 180);
        long active = sessions.startWorkoutFromTemplate(1, routine).getId();
        List<ActiveWorkoutExercise> activeRows = sessions.getActiveWorkout(1).orElseThrow().getExercises();
        long first = activeRows.get(0).getWorkoutExerciseId();
        long second = activeRows.get(1).getWorkoutExerciseId();

        sessions.moveExerciseInWorkout(1, second, 0);
        sessions.updateExerciseRestTime(1, second, 75);
        assertEquals(45, exercises.findById(first).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(75), templateExercises.findById(benchFirst.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(75), templateExercises.findById(benchSecond.getId()).orElseThrow().getRestTimeSeconds());
        sessions.removeExerciseFromWorkout(1, first);
        sessions.updateExerciseDetails(1, second, "After removal", 120);

        assertEquals(Integer.valueOf(120), templateExercises.findById(benchFirst.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(120), templateExercises.findById(benchSecond.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(90), templateExercises.findById(squatTemplate.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(180), templateExercises.findById(otherRoutine.getId()).orElseThrow().getRestTimeSeconds());
        assertEquals(120, exercises.findById(second).orElseThrow().getRestTimeSeconds());
        sessions.discardWorkout(1, active);
        sessions.startWorkoutFromTemplate(1, routine);
        List<ActiveWorkoutExercise> next = sessions.getActiveWorkout(1).orElseThrow().getExercises();
        assertEquals(120, next.get(0).getRestTimeSeconds());
        assertEquals(120, next.get(1).getRestTimeSeconds());
        assertEquals(90, next.get(2).getRestTimeSeconds());
    }

    @Test public void setSeedingFailureRollsBackParentExercisesAndEarlierSetsAndRetrySucceeds() {
        long exercise = libraryExercise("Bench", false);
        long routine = template(); templateRow(routine, exercise, 0, 60);
        long history = workout(1, routine, 100L);
        long previous = row(history, exercise, 0).getId();
        set(previous, 1, 20, 8, Unit.LB, SetType.NORMAL, true);
        set(previous, 2, 99, 5, Unit.LB, SetType.FAILURE, true);
        db.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON set_entry WHEN NEW.completed=0 AND NEW.weight=99 "
                + "BEGIN SELECT RAISE(ABORT,'Set restore failed'); END");
        assertThrows(SQLiteConstraintException.class, () -> sessions.startWorkoutFromTemplate(1, routine));
        assertFalse(db.inTransaction()); assertTrue(sessions.getActiveWorkout(1).isEmpty());
        assertEquals(1, count("logged_workout")); assertEquals(1, count("workout_exercise"));
        assertEquals(2, count("set_entry"));
        db.execSQL("DROP TRIGGER fail_restore");
        sessions.startWorkoutFromTemplate(1, routine);
        assertEquals(2, sessions.getActiveWorkout(1).orElseThrow().getExercises().get(0).getSets().size());
    }

    @Test public void failedActiveRestSaveAlsoRollsBackTemplatePreference() {
        long exercise = libraryExercise("Bench", false);
        long routine = template(); TemplateExercise templateRow = templateRow(routine, exercise, 0, 60);
        sessions.startWorkoutFromTemplate(1, routine);
        long row = sessions.getActiveWorkout(1).orElseThrow().getExercises().get(0).getWorkoutExerciseId();
        db.execSQL("CREATE TRIGGER fail_rest BEFORE UPDATE ON workout_exercise WHEN NEW.rest_time_seconds=120 "
                + "BEGIN SELECT RAISE(ABORT,'Active rest failed'); END");
        assertThrows(SQLiteConstraintException.class, () -> sessions.updateExerciseRestTime(1, row, 120));
        assertEquals(60, exercises.findById(row).orElseThrow().getRestTimeSeconds());
        assertEquals(Integer.valueOf(60), templateExercises.findById(templateRow.getId()).orElseThrow().getRestTimeSeconds());
        assertFalse(db.inTransaction());
    }

    private long libraryExercise(String title, boolean unilateral) {
        CreateExerciseRequest request = new CreateExerciseRequest(); request.setTitle(title);
        request.setPrimaryMuscleGroup(MuscleGroup.OTHER); request.setUnilateral(unilateral);
        return library.createExercise(request).getExerciseId();
    }
    private long template() {
        TemplateWorkout row = new TemplateWorkout(); row.setUserId(1); row.setTitle("Routine");
        row.setCreatedAtEpochMillis(1); return templates.save(row).getId();
    }
    private TemplateExercise templateRow(long routine, long exercise, int order, int rest) {
        TemplateExercise row = new TemplateExercise(); row.setTemplateWorkoutId(routine);
        row.setExerciseId(exercise); row.setOrderIndex(order); row.setRestTimeSeconds(rest);
        return templateExercises.save(row);
    }
    private long workout(long user, long routine, Long ended) {
        LoggedWorkout row = new LoggedWorkout(); row.setUserId(user); row.setTemplateId(routine);
        row.setStartedAtEpochMillis(1); row.setEndedAtEpochMillis(ended); return workouts.save(row).getId();
    }
    private WorkoutExercise row(long workout, long exercise, int order) {
        WorkoutExercise row = new WorkoutExercise(); row.setLoggedWorkoutId(workout); row.setExerciseId(exercise);
        row.setOrderIndex(order); row.setExerciseNameSnapshot("Exercise"); return exercises.save(row);
    }
    private SetEntry set(long exercise, int number, double weight, int reps, Unit unit, SetType type, boolean completed) {
        SetEntry set = new SetEntry(); set.setWorkoutExerciseId(exercise); set.setSetNumber(number);
        set.setWeight(weight); set.setReps(reps); set.setUnit(unit); set.setSetType(type);
        set.setCompleted(completed); set.setCreatedAtEpochMillis(1); return sets.save(set);
    }
    private int count(String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst(); return cursor.getInt(0);
        }
    }
}
