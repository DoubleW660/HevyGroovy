package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;
import com.example.hevygroovy.service.WorkoutHistoryQueryService;
import java.util.List;
import java.util.Map;

/** Keeps each multi-query history read in one consistent database transaction. */
public final class TransactionalWorkoutHistoryService implements WorkoutHistoryQueryService {
    private final WorkoutHistoryQueryService delegate;
    private final SQLiteTransactions transactions;

    public TransactionalWorkoutHistoryService(SQLiteDatabase db, WorkoutHistoryQueryService delegate) {
        this.delegate = delegate;
        transactions = new SQLiteTransactions(db);
    }

    @Override
    public List<WorkoutHistory> getWorkoutHistory(long userId) {
        return delegate.getWorkoutHistory(userId);
    }

    @Override
    public List<WorkoutHistory> getWorkoutHistory(long userId, int limit, int offset) {
        return delegate.getWorkoutHistory(userId, limit, offset);
    }

    @Override
    public Map<Long, Long> getLastCompletedAtByTemplate(long userId) {
        return delegate.getLastCompletedAtByTemplate(userId);
    }

    @Override
    public List<WorkoutHistory> getTemplateWorkoutHistory(long userId, long templateId, int limit) {
        return delegate.getTemplateWorkoutHistory(userId, templateId, limit);
    }

    @Override
    public WorkoutHistoryDetail getWorkoutHistoryDetail(long userId, long workoutId) {
        return transactions.read(() -> delegate.getWorkoutHistoryDetail(userId, workoutId));
    }
}
