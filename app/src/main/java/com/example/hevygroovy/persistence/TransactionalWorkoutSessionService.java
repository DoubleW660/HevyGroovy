package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.service.WorkoutSessionService;
import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.Optional;

/** All delegate repositories must use this same SQLiteDatabase. Call off the UI thread. */
public final class TransactionalWorkoutSessionService implements WorkoutSessionService {
    private final WorkoutSessionService delegate;
    private final SQLiteTransactions transactions;

    public TransactionalWorkoutSessionService(SQLiteDatabase db, WorkoutSessionService delegate) {
        this.delegate = delegate;
        this.transactions = new SQLiteTransactions(db);
    }

    @Override
    public Optional<ActiveWorkoutDetail> getActiveWorkout(long userId) {
        return transactions.read(() -> delegate.getActiveWorkout(userId));
    }

    @Override
    public LoggedWorkout updateWorkoutDetails(long userId, long workoutId, String name, String notes) {
        return transactions.run(() -> delegate.updateWorkoutDetails(userId, workoutId, name, notes));
    }

    @Override
    public WorkoutExercise updateExerciseDetails(long userId, long rowId, String notes, int seconds) {
        return transactions.run(() -> delegate.updateExerciseDetails(userId, rowId, notes, seconds));
    }

    @Override
    public LoggedWorkout renameWorkout(long userId, long workoutId, String name) {
        return transactions.run(() -> delegate.renameWorkout(userId, workoutId, name));
    }

    @Override
    public LoggedWorkout updateWorkoutNotes(long userId, long workoutId, String notes) {
        return transactions.run(() -> delegate.updateWorkoutNotes(userId, workoutId, notes));
    }

    @Override
    public void discardWorkout(long userId, long workoutId) {
        transactions.run(() -> { delegate.discardWorkout(userId, workoutId); return null; });
    }

    @Override
    public void deleteFinishedWorkout(long userId, long workoutId) {
        transactions.run(() -> { delegate.deleteFinishedWorkout(userId, workoutId); return null; });
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
    public LoggedWorkout startWorkoutFromTemplate(long userId, long templateId, Unit defaultUnit) {
        return transactions.run(() -> delegate.startWorkoutFromTemplate(userId, templateId, defaultUnit));
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
