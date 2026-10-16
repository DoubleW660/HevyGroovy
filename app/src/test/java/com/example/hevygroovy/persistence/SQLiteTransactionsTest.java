package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.service.SetEntryService;
import com.example.hevygroovy.service.WorkoutSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SQLiteTransactionsTest {
    @Test
    void commitsAfterSuccessfulOperationAndReturnsResult() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        Runnable operation = mock(Runnable.class);
        Object result = new Object();
        assertSame(result, new SQLiteTransactions(db).run(() -> {
            operation.run();
            return result;
        }));
        InOrder order = inOrder(db, operation);
        order.verify(db).beginTransaction();
        order.verify(operation).run();
        order.verify(db).setTransactionSuccessful();
        order.verify(db).endTransaction();
    }

    @Test
    void failureEndsTransactionWithoutCommittingAndPreservesException() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        RuntimeException failure = new RuntimeException("Child save failed");
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> new SQLiteTransactions(db).run(() -> { throw failure; })));
        verify(db, never()).setTransactionSuccessful();
        verify(db).endTransaction();
    }

    @Test
    void failedBeginDoesNotEndAnUnopenedTransaction() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        doThrow(new RuntimeException("Database unavailable")).when(db).beginTransaction();
        assertThrows(RuntimeException.class, () -> new SQLiteTransactions(db).run(() -> null));
        verify(db, never()).endTransaction();
    }

    @Test
    void everyWorkoutMutationRunsInsideTransaction() {
        List<Consumer<WorkoutSessionService>> operations = List.of(
                service -> service.startEmptyWorkout(1L),
                service -> service.startWorkoutFromTemplate(1L, 7L),
                service -> service.finishWorkout(1L, 100L),
                service -> service.addExerciseToWorkout(1L, 100L, 9L),
                service -> service.removeExerciseFromWorkout(1L, 10L),
                service -> service.moveExerciseInWorkout(1L, 10L, 2),
                service -> service.updateExerciseNotes(1L, 10L, null),
                service -> service.updateExerciseRestTime(1L, 10L, 60));
        for (Consumer<WorkoutSessionService> operation : operations) {
            SQLiteDatabase db = mock(SQLiteDatabase.class);
            WorkoutSessionService delegate = mock(WorkoutSessionService.class);
            operation.accept(new TransactionalWorkoutSessionService(db, delegate));
            InOrder order = inOrder(db, delegate);
            order.verify(db).beginTransaction();
            operation.accept(order.verify(delegate));
            order.verify(db).setTransactionSuccessful();
            order.verify(db).endTransaction();
            verifyNoMoreInteractions(db, delegate);
        }
    }

    @Test
    void everySetMutationRunsInsideTransaction() {
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        List<Consumer<SetEntryService>> operations = List.of(
                service -> service.addSet(1L, 10L),
                service -> service.updateSet(1L, 50L, request),
                service -> service.deleteSet(1L, 50L));
        for (Consumer<SetEntryService> operation : operations) {
            SQLiteDatabase db = mock(SQLiteDatabase.class);
            SetEntryService delegate = mock(SetEntryService.class);
            operation.accept(new TransactionalSetEntryService(db, delegate));
            InOrder order = inOrder(db, delegate);
            order.verify(db).beginTransaction();
            operation.accept(order.verify(delegate));
            order.verify(db).setTransactionSuccessful();
            order.verify(db).endTransaction();
            verifyNoMoreInteractions(db, delegate);
        }
    }
}
