package com.example.hevygroovy.persistence;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.enums.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RoutineProgressPersistenceTest {
    private Context context;
    private String name;
    private SQLiteAppStorage storage;
    private long exercise;
    private long routine;

    @Before public void open() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "routine-progress-" + UUID.randomUUID() + ".db";
        storage = new SQLiteAppStorage(context, name);
        CreateExerciseRequest request = new CreateExerciseRequest(); request.setTitle("Single leg press");
        request.setPrimaryMuscleGroup(MuscleGroup.QUADRICEPS); request.setUnilateral(true);
        exercise = storage.getExercises().createExercise(request).getExerciseId();
        routine = template("Lower");
    }
    @After public void close() { storage.close(); context.deleteDatabase(name); }

    @Test public void completionDatesUseAllFinishedHistoryAndRoutineGraphsAreBoundedAndUserScoped() {
        long olderRoutine = template("Upper");
        completed(olderRoutine, 1000, 2000, 50, 5);
        for (int index = 0; index < 25; index++) completed(routine, 3000 + index * 1000, 4000 + index * 1000, 140, 17);
        // A foreign user's row with the same template ID must not affect either read.
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            helper.getWritableDatabase().execSQL("INSERT INTO logged_workout(user_id,template_id,started_at,ended_at) VALUES(2,?,1,999999)",
                    new Object[]{routine});
        }
        storage.getWorkoutSessions().startWorkoutFromTemplate(1, olderRoutine);
        Map<Long, Long> dates = storage.getHistory().getLastCompletedAtByTemplate(1);
        assertEquals(Long.valueOf(2000), dates.get(olderRoutine));
        assertEquals(Long.valueOf(28000), dates.get(routine));
        List<WorkoutHistory> recent = storage.getHistory().getTemplateWorkoutHistory(1, routine, 12);
        assertEquals(12, recent.size());
        assertEquals(Long.valueOf(28000), recent.get(0).getEndedAtEpochMillis());
        assertEquals(Long.valueOf(17000), recent.get(11).getEndedAtEpochMillis());
        assertEquals(4760 * 0.45359237, recent.get(0).getTotalVolume(), 0.000001);
        assertEquals(17, recent.get(0).getTotalReps());
        assertEquals(1, recent.get(0).getDurationSeconds());
        assertEquals(1, storage.getHistory().getTemplateWorkoutHistory(1, olderRoutine, 12).size());
    }

    @Test public void completionOrderUsesEndTimeAndDeletionAndMovementSettingsRefreshDerivedValues() {
        long first = completed(routine, 1000, 5000, 100, 5);
        long last = completed(routine, 500, 6000, 140, 17);
        assertEquals(last, storage.getHistory().getTemplateWorkoutHistory(1, routine, 12).get(0).getWorkoutId());
        storage.getWorkoutSessions().deleteFinishedWorkout(1, last);
        assertEquals(Long.valueOf(5000), storage.getHistory().getLastCompletedAtByTemplate(1).get(routine));
        assertEquals(first, storage.getHistory().getTemplateWorkoutHistory(1, routine, 12).get(0).getWorkoutId());
        UpdateExerciseRequest bilateral = new UpdateExerciseRequest(); bilateral.setUnilateral(false);
        storage.getExercises().updateExercise(exercise, bilateral);
        storage.close(); storage = new SQLiteAppStorage(context, name);
        assertEquals(500 * 0.45359237, storage.getHistory().getTemplateWorkoutHistory(1, routine, 12).get(0).getTotalVolume(), 0.000001);
        storage.getWorkoutSessions().deleteFinishedWorkout(1, first);
        assertFalse(storage.getHistory().getLastCompletedAtByTemplate(1).containsKey(routine));
        assertTrue(storage.getHistory().getTemplateWorkoutHistory(1, routine, 12).isEmpty());
    }

    private long template(String title) {
        CreateTemplateRequest request = new CreateTemplateRequest(); request.setTitle(title);
        return storage.getTemplates().createTemplate(1, request).getTemplateWorkoutId();
    }
    private long completed(long template, long start, long end, double weight, int reps) {
        long workout = storage.getWorkoutSessions().startWorkoutFromTemplate(1, template).getId();
        long row = storage.getWorkoutSessions().addExerciseToWorkout(1, workout, exercise).getId();
        long set = storage.getSetEntries().addSet(1, row).getId();
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setWeight(weight); request.setReps(reps); request.setUnit(Unit.LB); request.setCompleted(true);
        storage.getSetEntries().updateSet(1, set, request);
        storage.getWorkoutSessions().finishWorkout(1, workout);
        try (WorkoutDatabase helper = new WorkoutDatabase(context, name)) {
            helper.getWritableDatabase().execSQL("UPDATE logged_workout SET started_at=?,ended_at=? WHERE id=?",
                    new Object[]{start, end, workout});
        }
        return workout;
    }
}
