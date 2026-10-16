package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.service.*;

/** Own one instance for the app lifetime. Construct and use on a background thread. */
public final class SQLiteAppStorage implements AutoCloseable {
    private final WorkoutDatabase helper;
    private final ExerciseService exercises;
    private final TemplateService templates;
    private final SQLiteWorkoutServices workouts;
    private final WorkoutHistoryQueryService history;

    public SQLiteAppStorage(Context context) {
        helper = new WorkoutDatabase(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        exercises = new ExerciseServiceImpl(new SQLiteExerciseRepository(db));
        SQLiteTemplateWorkoutRepository templateRows = new SQLiteTemplateWorkoutRepository(db);
        SQLiteTemplateExerciseRepository templateExercises = new SQLiteTemplateExerciseRepository(db);
        templates = new TransactionalTemplateService(db,
                new TemplateServiceImpl(templateRows, templateExercises, exercises));
        workouts = new SQLiteWorkoutServices(db, templateRows, templateExercises, exercises);
        history = new TransactionalWorkoutHistoryService(db, new WorkoutHistoryQueryServiceImpl(
                new SQLiteLoggedWorkoutRepository(db), new SQLiteWorkoutExerciseRepository(db),
                new SQLiteSetEntryRepository(db)));
    }

    public ExerciseService getExercises() { return exercises; }
    public TemplateService getTemplates() { return templates; }
    public WorkoutSessionService getWorkoutSessions() { return workouts.getWorkoutSessions(); }
    public SetEntryService getSetEntries() { return workouts.getSetEntries(); }
    public WorkoutHistoryQueryService getHistory() { return history; }

    @Override
    public void close() { helper.close(); }
}
