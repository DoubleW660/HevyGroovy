package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.service.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

/** Uses real services, repositories, transactions, and an isolated SQLite database. */
@RunWith(AndroidJUnit4.class)
public class SQLiteWorkoutFlowTest {
    @Test
    public void metadataPersistsAndDiscardRemovesChildrenAndFreesActiveSlot() {
        long exerciseId = createExercise("Squat");
        LoggedWorkout workout = sessions.startEmptyWorkout(1L);
        sessions.renameWorkout(1L, workout.getId(), "  Leg   day  ");
        sessions.updateWorkoutNotes(1L, workout.getId(), "Take it easy");
        WorkoutExercise row = sessions.addExerciseToWorkout(1L, workout.getId(), exerciseId);
        SetEntry set = setService.addSet(1L, row.getId());
        ActiveWorkoutDetail state = sessions.getActiveWorkout(1L).get();
        assertEquals("Leg day", state.getNameSnapshot());
        assertEquals("Take it easy", state.getNotes());
        sessions.discardWorkout(1L, workout.getId());
        assertFalse(workouts.findById(workout.getId()).isPresent());
        assertFalse(exercises.findById(row.getId()).isPresent());
        assertFalse(sets.findById(set.getId()).isPresent());
        assertTrue(sessions.getActiveWorkout(1L).isEmpty());
        assertTrue(history.getWorkoutHistory(1L).isEmpty());
        assertTrue(sessions.startEmptyWorkout(1L).getId() > 0);
    }

    @Test
    public void failedDiscardRestoresParentExercisesAndSets() {
        long exerciseId = createExercise("Squat");
        LoggedWorkout workout = sessions.startEmptyWorkout(1L);
        WorkoutExercise row = sessions.addExerciseToWorkout(1L, workout.getId(), exerciseId);
        SetEntry set = setService.addSet(1L, row.getId());
        db.execSQL("CREATE TRIGGER fail_discard BEFORE DELETE ON logged_workout "
                + "BEGIN SELECT RAISE(ABORT, 'Simulated discard failure'); END");
        assertThrows(SQLiteConstraintException.class, () -> sessions.discardWorkout(1L, workout.getId()));
        assertTrue(workouts.findById(workout.getId()).isPresent());
        assertTrue(exercises.findById(row.getId()).isPresent());
        assertTrue(sets.findById(set.getId()).isPresent());
        assertFalse(db.inTransaction());
    }
    @Test
    public void reconstructedServicesRecoverOrderedSessionIncludingIncompleteSets() {
        long exerciseId = createExercise("Squat");
        LoggedWorkout workout = sessions.startEmptyWorkout(1L);
        WorkoutExercise first = sessions.addExerciseToWorkout(1L, workout.getId(), exerciseId);
        WorkoutExercise second = sessions.addExerciseToWorkout(1L, workout.getId(), exerciseId);
        sessions.moveExerciseInWorkout(1L, second.getId(), 0);
        sessions.updateExerciseNotes(1L, second.getId(), "Brace");
        sessions.updateExerciseRestTime(1L, second.getId(), 120);
        SetEntry completed = setService.addSet(1L, second.getId());
        SetEntry placeholder = setService.addSet(1L, second.getId());
        UpdateSetEntryRequest update = new UpdateSetEntryRequest();
        update.setCompleted(true);
        update.setWeight(100D);
        update.setReps(5);
        setService.updateSet(1L, completed.getId(), update);

        SQLiteWorkoutServices rebuilt = new SQLiteWorkoutServices(db,
                new SQLiteTemplateWorkoutRepository(db), new SQLiteTemplateExerciseRepository(db),
                new ExerciseServiceImpl(new SQLiteExerciseRepository(db)));
        ActiveWorkoutDetail restored = rebuilt.getWorkoutSessions().getActiveWorkout(1L).get();
        assertEquals(workout.getId(), restored.getWorkoutId());
        assertEquals(workout.getStartedAtEpochMillis(), restored.getStartedAtEpochMillis());
        assertNull(restored.getTemplateId());
        assertEquals(2, restored.getExercises().size());
        ActiveWorkoutExercise top = restored.getExercises().get(0);
        assertEquals(second.getId(), top.getWorkoutExerciseId());
        assertEquals("Squat", top.getExerciseNameSnapshot());
        assertEquals("Brace", top.getNotes());
        assertEquals(120, top.getRestTimeSeconds());
        assertEquals(2, top.getSets().size());
        assertEquals(completed.getId(), top.getSets().get(0).getId());
        assertTrue(top.getSets().get(0).isCompleted());
        assertEquals(placeholder.getId(), top.getSets().get(1).getId());
        assertFalse(top.getSets().get(1).isCompleted());
        assertEquals(first.getId(), restored.getExercises().get(1).getWorkoutExerciseId());
        assertTrue(rebuilt.getWorkoutSessions().getActiveWorkout(2L).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> top.getSets().clear());
        sessions.finishWorkout(1L, workout.getId());
        assertTrue(rebuilt.getWorkoutSessions().getActiveWorkout(1L).isEmpty());
    }
    private SQLiteDatabase db;
    private WorkoutDatabase helper;
    private ExerciseService exerciseService;
    private TemplateService templateService;
    private WorkoutSessionService sessions;
    private SetEntryService setService;
    private WorkoutHistoryQueryService history;
    private SQLiteWorkoutExerciseRepository exercises;
    private SQLiteSetEntryRepository sets;
    private SQLiteLoggedWorkoutRepository workouts;

    @Before
    public void setUp() {
        db = SQLiteDatabase.create(null);
        helper = new WorkoutDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext());
        helper.onConfigure(db);
        helper.onCreate(db);
        exerciseService = new ExerciseServiceImpl(new SQLiteExerciseRepository(db));
        SQLiteTemplateWorkoutRepository templates = new SQLiteTemplateWorkoutRepository(db);
        SQLiteTemplateExerciseRepository templateExercises = new SQLiteTemplateExerciseRepository(db);
        templateService = new TransactionalTemplateService(db,
                new TemplateServiceImpl(templates, templateExercises, exerciseService));
        SQLiteWorkoutServices services = new SQLiteWorkoutServices(db, templates, templateExercises, exerciseService);
        sessions = services.getWorkoutSessions();
        setService = services.getSetEntries();
        exercises = new SQLiteWorkoutExerciseRepository(db);
        sets = new SQLiteSetEntryRepository(db);
        workouts = new SQLiteLoggedWorkoutRepository(db);
        history = new TransactionalWorkoutHistoryService(db,
                new WorkoutHistoryQueryServiceImpl(workouts, exercises, sets));
    }

    @After
    public void tearDown() { db.close(); helper.close(); }

    @Test
    public void templateToCompletedWorkoutToHistoryAndNextSessionNotes() {
        long exerciseId = createExercise("Bench press");
        long templateId = createTemplate(exerciseId);
        LoggedWorkout workout = sessions.startWorkoutFromTemplate(1L, templateId);
        WorkoutExercise row = exercises.findByLoggedWorkoutId(workout.getId()).get(0);
        SetEntry set = setService.addSet(1L, row.getId());
        assertFalse(set.isCompleted());
        assertThrows(RuntimeException.class, () -> sessions.finishWorkout(1L, workout.getId()));
        assertFalse(db.inTransaction());
        assertNull(workouts.findById(workout.getId()).get().getEndedAtEpochMillis());

        sessions.updateExerciseNotes(1L, row.getId(), "Pause at chest");
        UpdateSetEntryRequest update = new UpdateSetEntryRequest();
        update.setWeight(60D);
        update.setReps(8);
        update.setUnit(Unit.KG);
        update.setCompleted(true);
        setService.updateSet(1L, set.getId(), update);
        sessions.finishWorkout(1L, workout.getId());

        WorkoutHistoryDetail detail = history.getWorkoutHistoryDetail(1L, workout.getId());
        assertEquals("Upper body", detail.getNameSnapshot());
        assertEquals("Bench press", detail.getExercises().get(0).getExerciseName());
        assertEquals("Pause at chest", detail.getExercises().get(0).getNotes());
        assertEquals(1, detail.getTotalSets());
        assertEquals(8, detail.getTotalReps());
        assertEquals(480D, detail.getTotalVolume(), 0D);
        assertEquals(1, history.getWorkoutHistory(1L, 10, 0).size());
        assertTrue(history.getWorkoutHistory(2L, 10, 0).isEmpty());
        assertThrows(RuntimeException.class, () -> setService.updateSet(1L, set.getId(), update));
        assertThrows(RuntimeException.class, () -> sessions.removeExerciseFromWorkout(1L, row.getId()));

        // Historical notes also seed an exercise added to an unrelated, empty workout.
        LoggedWorkout next = sessions.startEmptyWorkout(1L);
        WorkoutExercise added = sessions.addExerciseToWorkout(1L, next.getId(), exerciseId);
        assertEquals("Pause at chest", added.getNotes());
    }

    @Test
    public void templateSeedingFailureRollsBackParentAndPreviouslySavedChildren() {
        long first = createExercise("Bench");
        long second = createExercise("Row");
        long template = createTemplate(first, second);
        db.execSQL("CREATE TRIGGER fail_seed BEFORE INSERT ON workout_exercise "
                + "WHEN NEW.exercise_id = " + second + " BEGIN SELECT RAISE(ABORT, 'Simulated child failure'); END");

        assertThrows(SQLiteConstraintException.class,
                () -> sessions.startWorkoutFromTemplate(1L, template));

        assertFalse(db.inTransaction());
        assertTrue(workouts.findByUserId(1L).isEmpty());
        try (android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM workout_exercise", null)) {
            cursor.moveToFirst();
            assertEquals(0, cursor.getInt(0));
        }
        db.execSQL("DROP TRIGGER fail_seed");
        LoggedWorkout retry = sessions.startWorkoutFromTemplate(1L, template);
        assertEquals(2, exercises.findByLoggedWorkoutId(retry.getId()).size());
    }

    @Test
    public void failedExerciseDeletionRestoresAlreadyDeletedSets() {
        long exerciseId = createExercise("Squat");
        LoggedWorkout workout = sessions.startEmptyWorkout(1L);
        WorkoutExercise row = sessions.addExerciseToWorkout(1L, workout.getId(), exerciseId);
        SetEntry set = setService.addSet(1L, row.getId());
        db.execSQL("CREATE TRIGGER fail_remove BEFORE DELETE ON workout_exercise "
                + "BEGIN SELECT RAISE(ABORT, 'Simulated deletion failure'); END");

        assertThrows(SQLiteConstraintException.class,
                () -> sessions.removeExerciseFromWorkout(1L, row.getId()));

        assertTrue(exercises.findById(row.getId()).isPresent());
        assertTrue(sets.findById(set.getId()).isPresent());
        assertFalse(db.inTransaction());
        db.execSQL("DROP TRIGGER fail_remove");
        sessions.removeExerciseFromWorkout(1L, row.getId());
        assertFalse(sets.findById(set.getId()).isPresent());
        assertTrue(exercises.findByLoggedWorkoutId(workout.getId()).isEmpty());
    }

    private long createExercise(String name) {
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle(name);
        request.setPrimaryMuscleGroup(MuscleGroup.OTHER);
        return exerciseService.createExercise(request).getExerciseId();
    }

    private long createTemplate(long... exerciseIds) {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setTitle("Upper body");
        long id = templateService.createTemplate(1L, request).getTemplateWorkoutId();
        for (long exerciseId : exerciseIds) {
            AddTemplateExerciseRequest add = new AddTemplateExerciseRequest();
            add.setTemplateId(id);
            add.setExerciseId(exerciseId);
            templateService.addExerciseToTemplate(1L, add);
        }
        return id;
    }
}
