package com.example.hevygroovy.app;

import com.example.hevygroovy.service.*;

/** Synchronous services shared by gateways on the application's database worker. */
public interface AppServices extends AutoCloseable {
    ExerciseService getExercises();
    TemplateService getTemplates();
    WorkoutSessionService getWorkoutSessions();
    SetEntryService getSetEntries();
    WorkoutHistoryQueryService getHistory();
    @Override void close();
}
