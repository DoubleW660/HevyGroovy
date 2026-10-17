package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.service.SetEntryService;
import com.example.hevygroovy.service.WorkoutSessionService;
import com.example.hevygroovy.service.TemplateService;
import com.example.hevygroovy.dto.CreateTemplateRequest;
import com.example.hevygroovy.dto.UpdateTemplateRequest;
import com.example.hevygroovy.dto.AddTemplateExerciseRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SQLiteTransactionsTest {
    @Test
    void singleQueryTemplateListDoesNotOpenTransaction() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        TemplateService delegate = mock(TemplateService.class);
        new TransactionalTemplateService(db, delegate).listTemplates(1L, false);
        verify(delegate).listTemplates(1L, false);
        verifyNoInteractions(db);
    }

    @Test
    void singleQueryHistorySummariesDoNotOpenTransaction() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        com.example.hevygroovy.service.WorkoutHistoryQueryService delegate =
                mock(com.example.hevygroovy.service.WorkoutHistoryQueryService.class);
        TransactionalWorkoutHistoryService wrapper = new TransactionalWorkoutHistoryService(db, delegate);
        wrapper.getWorkoutHistory(1L);
        wrapper.getWorkoutHistory(1L, 20, 0);
        verify(delegate).getWorkoutHistory(1L);
        verify(delegate).getWorkoutHistory(1L, 20, 0);
        verifyNoInteractions(db);
    }

    @Test
    void readFailureEndsSnapshotWithoutCommitting() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        assertThrows(IllegalArgumentException.class, () -> new SQLiteTransactions(db).read(() -> {
            throw new IllegalArgumentException("Read failed");
        }));
        verify(db).endTransaction();
        verify(db, never()).setTransactionSuccessful();
        verify(db, never()).beginTransaction();
    }
    @Test
    void everyTemplateOperationRunsInsideTransaction() {
        CreateTemplateRequest create = new CreateTemplateRequest();
        UpdateTemplateRequest update = new UpdateTemplateRequest();
        AddTemplateExerciseRequest add = new AddTemplateExerciseRequest();
        List<Consumer<TemplateService>> operations = List.of(
                service -> service.createTemplate(1L, create),
                service -> service.updateTemplate(1L, 7L, update),
                service -> service.addExerciseToTemplate(1L, add),
                service -> service.removeExerciseFromTemplate(1L, 10L, 7L),
                service -> service.moveExerciseInTemplate(1L, 10L, 2),
                service -> service.updateExerciseRestTime(1L, 10L, 90),
                service -> service.getTemplateDetail(1L, 7L),

                service -> service.archiveTemplate(1L, 7L),
                service -> service.restoreTemplate(1L, 7L));
        for (Consumer<TemplateService> operation : operations) {
            SQLiteDatabase db = mock(SQLiteDatabase.class);
            TemplateService delegate = mock(TemplateService.class);
            operation.accept(new TransactionalTemplateService(db, delegate));
            InOrder order = inOrder(db, delegate);
            order.verify(db).beginTransactionNonExclusive();
            operation.accept(order.verify(delegate));
            order.verify(db).setTransactionSuccessful();
            order.verify(db).endTransaction();
            verifyNoMoreInteractions(db, delegate);
        }
    }
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
        order.verify(db).beginTransactionNonExclusive();
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
        doThrow(new RuntimeException("Database unavailable")).when(db).beginTransactionNonExclusive();
        assertThrows(RuntimeException.class, () -> new SQLiteTransactions(db).run(() -> null));
        verify(db, never()).endTransaction();
    }

    @Test
    void everyWorkoutMutationRunsInsideTransaction() {
        List<Consumer<WorkoutSessionService>> operations = List.of(
                service -> service.getActiveWorkout(1L),
                service -> service.renameWorkout(1L, 100L, "Legs"),
                service -> service.updateWorkoutDetails(1L, 100L, "Legs", "Note"),
                service -> service.updateExerciseDetails(1L, 10L, "Brace", 90),
                service -> service.updateWorkoutNotes(1L, 100L, null),
                service -> service.discardWorkout(1L, 100L),
                service -> service.deleteFinishedWorkout(1L, 100L),
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
            order.verify(db).beginTransactionNonExclusive();
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
                service -> service.addSet(1L, 10L, com.example.hevygroovy.entity.enums.Unit.KG),
                service -> service.updateSet(1L, 50L, request),
                service -> service.deleteSet(1L, 50L));
        for (Consumer<SetEntryService> operation : operations) {
            SQLiteDatabase db = mock(SQLiteDatabase.class);
            SetEntryService delegate = mock(SetEntryService.class);
            operation.accept(new TransactionalSetEntryService(db, delegate));
            InOrder order = inOrder(db, delegate);
            order.verify(db).beginTransactionNonExclusive();
            operation.accept(order.verify(delegate));
            order.verify(db).setTransactionSuccessful();
            order.verify(db).endTransaction();
            verifyNoMoreInteractions(db, delegate);
        }
    }
}
