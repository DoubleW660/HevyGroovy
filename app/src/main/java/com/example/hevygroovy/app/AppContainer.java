package com.example.hevygroovy.app;

import android.content.Context;
import com.example.hevygroovy.persistence.SQLiteAppStorage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-owned dependencies. All database access stays on one background worker. */
public final class AppContainer implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AppWorker appWorker;
    private final WorkoutGateway workouts;
    private final LibraryGateway library;
    private final HistoryGateway history;
    private final ProfileGateway profile;

    public AppContainer(Context context) { this(context, "hevy_groovy.db"); }

    public AppContainer(Context context, String databaseName) {
        Context app = context.getApplicationContext();
        appWorker = new AppWorker(worker, app.getMainExecutor(),
                () -> new SQLiteAppStorage(app, databaseName), () -> new LocalUserIdentity(app).getUserId());
        UserPreferences preferences = new UserPreferences(app);
        workouts = new ServiceWorkoutGateway(appWorker, preferences::readDefaultUnit);
        library = new ServiceLibraryGateway(appWorker);
        history = new ServiceHistoryGateway(appWorker);
        profile = new ServiceProfileGateway(appWorker, preferences);
    }

    public WorkoutGateway getWorkouts() { return workouts; }
    public LibraryGateway getLibrary() { return library; }
    public HistoryGateway getHistory() { return history; }
    public ProfileGateway getProfile() { return profile; }

    /** For explicit shutdown outside main; normal app storage lives until process exit. */
    @Override
    public void close() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("Close storage off the main thread");
        }
        java.util.concurrent.Future<?> closing = worker.submit(() -> { appWorker.closeStorage(); });
        worker.shutdown();
        try {
            closing.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing storage", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException("Could not close storage", error.getCause());
        }
    }
}
