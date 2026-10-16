package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.service.SetEntryService;

/** Uses the same database as the delegate repositories and workout-session wrapper. */
public final class TransactionalSetEntryService implements SetEntryService {
    private final SetEntryService delegate;
    private final SQLiteTransactions transactions;

    public TransactionalSetEntryService(SQLiteDatabase db, SetEntryService delegate) {
        this.delegate = delegate;
        transactions = new SQLiteTransactions(db);
    }

    @Override
    public SetEntry addSet(long userId, long workoutExerciseId) {
        return transactions.run(() -> delegate.addSet(userId, workoutExerciseId));
    }

    @Override
    public SetEntry addSet(long userId, long workoutExerciseId, Unit unit) {
        return transactions.run(() -> delegate.addSet(userId, workoutExerciseId, unit));
    }

    @Override
    public SetEntry updateSet(long userId, long setEntryId, UpdateSetEntryRequest request) {
        return transactions.run(() -> delegate.updateSet(userId, setEntryId, request));
    }

    @Override
    public void deleteSet(long userId, long setEntryId) {
        transactions.run(() -> {
            delegate.deleteSet(userId, setEntryId);
            return null;
        });
    }
}
