package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;
import com.example.hevygroovy.service.WorkoutHistoryQueryService;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceHistoryGatewayTest {
    private AppServices services;
    private WorkoutHistoryQueryService history;
    private HistoryGateway gateway;

    @BeforeEach void setUp() {
        services = mock(AppServices.class);
        history = mock(WorkoutHistoryQueryService.class);
        when(services.getHistory()).thenReturn(history);
        gateway = new ServiceHistoryGateway(new AppWorker(Runnable::run, Runnable::run, () -> services, () -> 41L));
    }

    @Test void pageUsesUserScopeAndOneRowLookaheadWithoutCountingOrLoadingAllHistory() {
        List<WorkoutHistory> rows = new ArrayList<>();
        for (long id = 100; id > 79; id--) rows.add(workout(id));
        when(history.getWorkoutHistory(41L, 21, 20)).thenReturn(rows);
        HistoryGateway.PageCallback callback = mock(HistoryGateway.PageCallback.class);
        gateway.loadPage(20, callback);
        ArgumentCaptor<HistoryGateway.HistoryPage> result = ArgumentCaptor.forClass(HistoryGateway.HistoryPage.class);
        verify(callback).onSuccess(result.capture());
        assertEquals(20, result.getValue().getItems().size());
        assertEquals(81L, result.getValue().getItems().get(19).getWorkoutId());
        assertTrue(result.getValue().hasMore());
        assertEquals(40, result.getValue().getNextOffset());
        verify(history).getWorkoutHistory(41L, 21, 20);
        verifyNoMoreInteractions(history);
    }

    @Test void exactlyFullLastPageHasNoMoreAndOffsetCountsRowsEvenWhenIdsOverlap() {
        List<WorkoutHistory> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) rows.add(workout(100L));
        when(history.getWorkoutHistory(41L, 21, 40)).thenReturn(rows);
        HistoryGateway.PageCallback callback = mock(HistoryGateway.PageCallback.class);
        gateway.loadPage(40, callback);
        ArgumentCaptor<HistoryGateway.HistoryPage> result = ArgumentCaptor.forClass(HistoryGateway.HistoryPage.class);
        verify(callback).onSuccess(result.capture());
        assertFalse(result.getValue().hasMore());
        assertEquals(60, result.getValue().getNextOffset());
        rows.clear();
        assertEquals(20, result.getValue().getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> result.getValue().getItems().clear());
    }

    @Test void detailDelegatesOwnershipAndPropagatesFailure() {
        WorkoutHistoryDetail detail = new WorkoutHistoryDetail(workout(90L), "Notes", List.of());
        when(history.getWorkoutHistoryDetail(41L, 90L)).thenReturn(detail);
        HistoryGateway.DetailCallback callback = mock(HistoryGateway.DetailCallback.class);
        gateway.loadDetail(90L, callback);
        verify(callback).onSuccess(detail);
        RuntimeException denied = new IllegalArgumentException("Workout belongs to another user");
        when(history.getWorkoutHistoryDetail(41L, 91L)).thenThrow(denied);
        gateway.loadDetail(91L, callback);
        verify(callback).onFailure(denied);
    }

    @Test void invalidOffsetDoesNotQueryHistory() {
        HistoryGateway.PageCallback callback = mock(HistoryGateway.PageCallback.class);
        gateway.loadPage(-1, callback);
        verify(callback).onFailure(isA(IllegalArgumentException.class));
        verifyNoInteractions(history);
    }

    @Test void storageAndReadStayOnWorkerAndCallbackIsPostedToMain() {
        ArrayDeque<Runnable> background = new ArrayDeque<>();
        ArrayDeque<Runnable> main = new ArrayDeque<>();
        java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
        when(history.getWorkoutHistory(41L, 21, 0)).thenReturn(List.of());
        HistoryGateway deferred = new ServiceHistoryGateway(new AppWorker(background::add, main::add,
                () -> { opened.incrementAndGet(); return services; }, () -> 41L));
        HistoryGateway.PageCallback callback = mock(HistoryGateway.PageCallback.class);
        deferred.loadPage(0, callback);
        assertEquals(0, opened.get());
        verifyNoInteractions(history, callback);
        background.remove().run();
        assertEquals(1, opened.get());
        verify(history).getWorkoutHistory(41L, 21, 0);
        verifyNoInteractions(callback);
        main.remove().run();
        verify(callback).onSuccess(any());
    }

    private static WorkoutHistory workout(long id) {
        return new WorkoutHistory(id, "Workout " + id, 1_000L, 61_000L,
                Duration.ofMinutes(1), 1, 1, 5, 100);
    }
}
