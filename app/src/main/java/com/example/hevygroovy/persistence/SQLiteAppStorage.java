package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.app.AppServices;
import com.example.hevygroovy.service.*;

/** Own one instance for the app lifetime. Construct and use on a background thread. */
public final class SQLiteAppStorage implements AppServices {
    private final WorkoutDatabase helper;
    private final ExerciseService exercises;
    private final TemplateService templates;
    private final SQLiteWorkoutServices workouts;
    private final WorkoutHistoryQueryService history;

    public SQLiteAppStorage(Context context) {
        this(context, "hevy_groovy.db", true);
    }

    /** Named storage also supports isolated integration tests and local profiles. */
    public SQLiteAppStorage(Context context, String databaseName) {
        this(context, databaseName, false);
    }

    /** Explicit catalog opt-in keeps isolated test/profile databases free of bundled fixtures. */
    public SQLiteAppStorage(Context context, String databaseName, boolean seedCatalog) {
        helper = new WorkoutDatabase(context, databaseName);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            if (seedCatalog) { BundledExerciseCatalog.seed(context, db); }
            exercises = new ExerciseServiceImpl(new SQLiteExerciseRepository(db));
            SQLiteTemplateWorkoutRepository templateRows = new SQLiteTemplateWorkoutRepository(db);
            SQLiteTemplateExerciseRepository templateExercises = new SQLiteTemplateExerciseRepository(db);
            templates = new TransactionalTemplateService(db,
                    new TemplateServiceImpl(templateRows, templateExercises, exercises));
            workouts = new SQLiteWorkoutServices(db, templateRows, templateExercises, exercises);
            history = new TransactionalWorkoutHistoryService(db, new WorkoutHistoryQueryServiceImpl(
                    new SQLiteLoggedWorkoutRepository(db), new SQLiteWorkoutExerciseRepository(db),
                    new SQLiteSetEntryRepository(db)));
        } catch (RuntimeException | Error failure) {
            try { helper.close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public ExerciseService getExercises() { return exercises; }
    public TemplateService getTemplates() { return templates; }
    public WorkoutSessionService getWorkoutSessions() { return workouts.getWorkoutSessions(); }
    public SetEntryService getSetEntries() { return workouts.getSetEntries(); }
    public WorkoutHistoryQueryService getHistory() { return history; }

    @Override
    public void close() { helper.close(); }
}
