package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.service.*;

/** Wires workout mutations to a shared database and transaction boundary. */
public final class SQLiteWorkoutServices {
    private final WorkoutSessionService workoutSessions;
    private final SetEntryService setEntries;

    /** Template repositories and exercise-service persistence must share db as well. */
    public SQLiteWorkoutServices(SQLiteDatabase db, TemplateWorkoutRepository templates,
                                 TemplateExerciseRepository templateExercises,
                                 ExerciseService exercises) {
        SQLiteLoggedWorkoutRepository workouts = new SQLiteLoggedWorkoutRepository(db);
        SQLiteWorkoutExerciseRepository workoutExercises = new SQLiteWorkoutExerciseRepository(db);
        SQLiteSetEntryRepository sets = new SQLiteSetEntryRepository(db);
        workoutSessions = new TransactionalWorkoutSessionService(db,
                new WorkoutSessionServiceImpl(workouts, templates, templateExercises,
                        workoutExercises, sets, exercises));
        setEntries = new TransactionalSetEntryService(db,
                new SetEntryServiceImpl(sets, workoutExercises, workouts));
    }

    public WorkoutSessionService getWorkoutSessions() { return workoutSessions; }

    public SetEntryService getSetEntries() { return setEntries; }
}
