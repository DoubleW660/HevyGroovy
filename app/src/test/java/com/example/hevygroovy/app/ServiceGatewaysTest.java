package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.model.*;
import com.example.hevygroovy.service.*;
import java.util.ArrayDeque;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceGatewaysTest {
    @Test void librarySnapshotUsesUserScopedCompletionDatesAndTwelveSelectedRoutineSessions() {
        WorkoutHistoryQueryService history = services.getHistory();
        when(history.getLastCompletedAtByTemplate(1)).thenReturn(java.util.Map.of(7L, 3000L));
        when(templates.getTemplateDetail(1, 7)).thenReturn(new TemplateDetailModel());
        LibraryGateway.LibraryCallback callback = mock(LibraryGateway.LibraryCallback.class);
        new ServiceLibraryGateway(worker).loadTemplate(7, callback);
        org.mockito.ArgumentCaptor<LibraryGateway.LibrarySnapshot> result = org.mockito.ArgumentCaptor.forClass(LibraryGateway.LibrarySnapshot.class);
        verify(callback).onSuccess(result.capture());
        assertEquals(3000L, result.getValue().getLastCompletedAtByTemplate().get(7L));
        verify(history).getLastCompletedAtByTemplate(1);
        verify(history).getTemplateWorkoutHistory(1, 7, 12);
        verify(history, never()).getWorkoutHistory(anyLong());
    }
    private AppServices services;
    private ExerciseService exercises;
    private TemplateService templates;
    private WorkoutSessionService workouts;
    private AppWorker worker;

    @BeforeEach void setUp() {
        services = mock(AppServices.class);
        exercises = mock(ExerciseService.class);
        templates = mock(TemplateService.class);
        workouts = mock(WorkoutSessionService.class);
        when(services.getExercises()).thenReturn(exercises);
        when(services.getTemplates()).thenReturn(templates);
        when(services.getWorkoutSessions()).thenReturn(workouts);
        when(services.getHistory()).thenReturn(mock(WorkoutHistoryQueryService.class));
        worker = new AppWorker(Runnable::run, Runnable::run, () -> services, () -> 1L);
    }

    @Test void failedWriteIsNotReportedAsCommittedAndDoesNotRefresh() {
        RuntimeException failure = new IllegalStateException("Write failed");
        doThrow(failure).when(workouts).addExerciseToWorkout(1L, 10L, 9L);
        WorkoutGateway.Callback callback = mock(WorkoutGateway.Callback.class);
        new ServiceWorkoutGateway(worker).addExercise(10L, 9L, callback);
        verify(callback).onFailure(failure);
        verify(callback, never()).onSuccess(any());
        verify(workouts, never()).getActiveWorkout(anyLong());
    }

    @Test void committedWorkoutWriteIsNotRepeatedWhenSnapshotFailsAndLoadRecovers() {
        RuntimeException failure = new IllegalStateException("Read failed");
        when(workouts.getActiveWorkout(1L)).thenThrow(failure).thenReturn(Optional.empty());
        WorkoutGateway gateway = new ServiceWorkoutGateway(worker);
        WorkoutGateway.Callback callback = mock(WorkoutGateway.Callback.class);
        gateway.addExercise(10L, 9L, callback);
        org.mockito.ArgumentCaptor<Exception> error = org.mockito.ArgumentCaptor.forClass(Exception.class);
        verify(callback).onFailure(error.capture());
        assertInstanceOf(CommittedRefreshException.class, error.getValue());
        assertSame(failure, error.getValue().getCause());
        gateway.load(callback);
        verify(callback).onSuccess(any());
        verify(workouts, times(1)).addExerciseToWorkout(1L, 10L, 9L);
    }

    @Test void committedTemplateCreateReturnsItsIdEvenWhenDetailRefreshFails() {
        TemplateSummaryModel created = new TemplateSummaryModel();
        created.setTemplateWorkoutId(42L);
        CreateTemplateRequest request = new CreateTemplateRequest();
        when(templates.createTemplate(1L, request)).thenReturn(created);
        when(templates.getTemplateDetail(1L, 42L)).thenThrow(new IllegalStateException("Read failed"))
                .thenReturn(new TemplateDetailModel());
        LibraryGateway gateway = new ServiceLibraryGateway(worker);
        LibraryGateway.LibraryCallback callback = mock(LibraryGateway.LibraryCallback.class);
        gateway.createTemplate(request, callback);
        org.mockito.ArgumentCaptor<Exception> error = org.mockito.ArgumentCaptor.forClass(Exception.class);
        verify(callback).onFailure(error.capture());
        assertEquals(42L, ((CommittedRefreshException) error.getValue()).getSelectedTemplateId());
        gateway.loadLibrary(42L, callback);
        verify(callback).onSuccess(any());
        verify(templates, times(1)).createTemplate(1L, request);
    }

    @Test void openingTemplatesReusesListsAndCatalogWritesOnlyInvalidateCatalog() {
        when(templates.getTemplateDetail(1L, 7L)).thenReturn(new TemplateDetailModel());
        LibraryGateway gateway = new ServiceLibraryGateway(worker);
        LibraryGateway.LibraryCallback callback = mock(LibraryGateway.LibraryCallback.class);
        gateway.loadLibrary(null, callback);
        gateway.loadTemplate(7L, callback);
        gateway.loadTemplate(7L, callback);
        verify(exercises, times(1)).listExercises(null, true);
        verify(templates, times(1)).listTemplates(1L, true);
        verify(templates, times(2)).getTemplateDetail(1L, 7L);
        gateway.createCatalogExercise(new CreateExerciseRequest(), callback);
        verify(exercises, times(2)).listExercises(null, true);
        verify(templates, times(1)).listTemplates(1L, true);
    }

    @Test void readsFailNormallyWithoutImplyingACommittedWrite() {
        RuntimeException failure = new IllegalStateException("Read failed");
        when(exercises.listExercises(null, true)).thenThrow(failure);
        LibraryGateway.LibraryCallback callback = mock(LibraryGateway.LibraryCallback.class);
        new ServiceLibraryGateway(worker).loadLibrary(null, callback);
        verify(callback).onFailure(failure);
        verify(callback, never()).onSuccess(any());
    }

    @Test void storageAndServiceWorkAreDeferredAndCallbacksArePostedToMain() {
        ArrayDeque<Runnable> background = new ArrayDeque<>();
        ArrayDeque<Runnable> main = new ArrayDeque<>();
        java.util.concurrent.atomic.AtomicInteger opens = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<AppServices> opener = () -> { opens.incrementAndGet(); return services; };
        AppWorker queued = new AppWorker(background::add, main::add, opener, () -> 1L);
        LibraryGateway.LibraryCallback callback = mock(LibraryGateway.LibraryCallback.class);
        new ServiceLibraryGateway(queued).loadLibrary(null, callback);
        assertEquals(0, opens.get());
        verifyNoInteractions(exercises, callback);
        background.remove().run();
        assertEquals(1, opens.get());
        verify(exercises).listExercises(null, true);
        verifyNoInteractions(callback);
        main.remove().run();
        verify(callback).onSuccess(any());
    }
}
