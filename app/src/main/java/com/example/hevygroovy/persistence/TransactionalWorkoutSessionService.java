package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.service.WorkoutSessionService;

/** All delegate repositories must use this same SQLiteDatabase. Call off the UI thread. */
public final class TransactionalWorkoutSessionService implements WorkoutSessionService {
    private final WorkoutSessionService delegate;
    private final SQLiteTransactions transactions;

    public TransactionalWorkoutSessionService(SQLiteDatabase db, WorkoutSessionService delegate) {
        this.delegate = delegate;
        this.transactions = new SQLiteTransactions(db);
    }

    @Override
    public LoggedWorkout startEmptyWorkout(long userId) {
        return transactions.run(() -> delegate.startEmptyWorkout(userId));
    }

    @Override
    public LoggedWorkout startWorkoutFromTemplate(long userId, long templateId) {
        return transactions.run(() -> delegate.startWorkoutFromTemplate(userId, templateId));
    }

    @Override
    public LoggedWorkout finishWorkout(long userId, long workoutId) {
        return transactions.run(() -> delegate.finishWorkout(userId, workoutId));
    }

    @Override
    public WorkoutExercise addExerciseToWorkout(long userId, long workoutId, long exerciseId) {
        return transactions.run(() -> delegate.addExerciseToWorkout(userId, workoutId, exerciseId));
    }

    @Override
    public void removeExerciseFromWorkout(long userId, long workoutExerciseId) {
        transactions.run(() -> {
            delegate.removeExerciseFromWorkout(userId, workoutExerciseId);
            return null;
        });
    }

    @Override
    public void moveExerciseInWorkout(long userId, long workoutExerciseId, int orderIndex) {
        transactions.run(() -> {
            delegate.moveExerciseInWorkout(userId, workoutExerciseId, orderIndex);
            return null;
        });
    }

    @Override
    public WorkoutExercise updateExerciseNotes(long userId, long workoutExerciseId, String notes) {
        return transactions.run(() -> delegate.updateExerciseNotes(userId, workoutExerciseId, notes));
    }

    @Override
    public WorkoutExercise updateExerciseRestTime(long userId, long workoutExerciseId, int seconds) {
        return transactions.run(() -> delegate.updateExerciseRestTime(userId, workoutExerciseId, seconds));
    }
}
