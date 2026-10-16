package com.example.hevygroovy.app;

import android.content.Context;
import com.example.hevygroovy.persistence.SQLiteAppStorage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-owned dependencies. All database access stays on one background worker. */
public final class AppContainer implements WorkoutGateway {
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Executor main;
    // Only read or written by the worker. Storage stays open for the process lifetime.
    private SQLiteAppStorage storage;
    private long userId;

    public AppContainer(Context context) {
        this.context = context.getApplicationContext();
        main = this.context.getMainExecutor();
    }

    private void submit(Runnable mutation, Callback callback) {
        worker.execute(() -> {
            try {
                if (storage == null) {
                    userId = new LocalUserIdentity(context).getUserId();
                    storage = new SQLiteAppStorage(context);
                }
                mutation.run();
                Snapshot snapshot = new Snapshot(storage.getWorkoutSessions().getActiveWorkout(userId).orElse(null),
                        storage.getHistory().getWorkoutHistory(userId, 20, 0));
                main.execute(() -> callback.onSuccess(snapshot));
            } catch (Exception error) {
                main.execute(() -> callback.onFailure(error));
            }
        });
    }

    @Override
    public void load(Callback callback) { submit(() -> { }, callback); }

    @Override
    public void startEmpty(Callback callback) {
        submit(() -> storage.getWorkoutSessions().startEmptyWorkout(userId), callback);
    }

    @Override
    public void discard(long workoutId, Callback callback) {
        submit(() -> storage.getWorkoutSessions().discardWorkout(userId, workoutId), callback);
    }
}
