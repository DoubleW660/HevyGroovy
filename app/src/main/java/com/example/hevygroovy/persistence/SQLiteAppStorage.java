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
    private final PersonalRecordService personalRecords;
    private final WorkoutImportService workoutImporter;

    public SQLiteAppStorage(Context context) {
        this(context, "hevy_groovy.db", true, 1L, true);
    }

    /** Named storage also supports isolated integration tests and local profiles. */
    public SQLiteAppStorage(Context context, String databaseName) {
        this(context, databaseName, false);
    }

    /** Explicit catalog opt-in keeps isolated test/profile databases free of bundled fixtures. */
    public SQLiteAppStorage(Context context, String databaseName, boolean seedCatalog) {
        this(context, databaseName, seedCatalog, 1L);
    }

    public SQLiteAppStorage(Context context, String databaseName, boolean seedCatalog, long userId) {
        this(context, databaseName, seedCatalog, userId, false);
    }

    /** Personal workout history is an explicit opt-in outside the normal app startup. */
    public SQLiteAppStorage(Context context, String databaseName, boolean seedCatalog,
                             long userId, boolean seedHistory) {
        helper = new WorkoutDatabase(context, databaseName);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            workoutImporter = new HevyHistoryImporter(db);
            if (seedCatalog || seedHistory) {
                db.beginTransaction();
                try {
                    if (seedCatalog) {
                        BundledExerciseCatalog.seed(context, db);
                        BundledTemplateCatalog.seed(context, db, userId);
                    }
                    if (seedHistory) BundledWorkoutHistory.seed(context, db, userId);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            }
            exercises = new ExerciseServiceImpl(new SQLiteExerciseRepository(db));
            SQLiteTemplateWorkoutRepository templateRows = new SQLiteTemplateWorkoutRepository(db);
            SQLiteTemplateExerciseRepository templateExercises = new SQLiteTemplateExerciseRepository(db);
            templates = new TransactionalTemplateService(db,
                    new TemplateServiceImpl(templateRows, templateExercises, exercises));
            workouts = new SQLiteWorkoutServices(db, templateRows, templateExercises, exercises);
            personalRecords = new PersonalRecordServiceImpl(new SQLitePersonalRecordRepository(db),
                    new SQLiteLoggedWorkoutRepository(db), new SQLiteWorkoutExerciseRepository(db),
                    new SQLiteSetEntryRepository(db));
            history = new TransactionalWorkoutHistoryService(db, new WorkoutHistoryQueryServiceImpl(
                    new SQLiteLoggedWorkoutRepository(db), new SQLiteWorkoutExerciseRepository(db),
                    new SQLiteSetEntryRepository(db), personalRecords));
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
    public PersonalRecordService getPersonalRecords() { return personalRecords; }
    public WorkoutImportService getWorkoutImporter() { return workoutImporter; }

    @Override
    public void close() { helper.close(); }
}
