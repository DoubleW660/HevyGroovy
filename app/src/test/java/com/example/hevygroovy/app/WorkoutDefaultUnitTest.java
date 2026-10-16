package com.example.hevygroovy.app;

import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.service.ExerciseService;
import com.example.hevygroovy.service.SetEntryService;
import com.example.hevygroovy.service.WorkoutHistoryQueryService;
import com.example.hevygroovy.service.WorkoutSessionService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class WorkoutDefaultUnitTest {
    @Test void newSetReadsLatestPreferenceAndPassesUnitIntoAtomicServiceOperation() {
        AppServices services = services();
        AppWorker worker = new AppWorker(Runnable::run, Runnable::run, () -> services, () -> 1L);
        AtomicReference<Unit> preference = new AtomicReference<>(Unit.KG);
        WorkoutGateway gateway = new ServiceWorkoutGateway(worker, preference::get);
        WorkoutGateway.Callback callback = mock(WorkoutGateway.Callback.class);
        gateway.addSet(10L, callback);
        preference.set(Unit.LB);
        gateway.addSet(10L, callback);
        verify(services.getSetEntries()).addSet(1L, 10L, Unit.KG);
        verify(services.getSetEntries()).addSet(1L, 10L, Unit.LB);
        verify(services.getSetEntries(), never()).updateSet(anyLong(), anyLong(), any());
        verify(callback, times(2)).onSuccess(any());
    }

    @Test void unconfiguredGatewayKeepsOriginalPoundDefault() {
        AppServices services = services();
        AppWorker worker = new AppWorker(Runnable::run, Runnable::run, () -> services, () -> 1L);
        new ServiceWorkoutGateway(worker).addSet(10L, mock(WorkoutGateway.Callback.class));
        verify(services.getSetEntries()).addSet(1L, 10L, Unit.LB);
    }

    private AppServices services() {
        AppServices services = mock(AppServices.class);
        when(services.getSetEntries()).thenReturn(mock(SetEntryService.class));
        WorkoutSessionService workouts = mock(WorkoutSessionService.class);
        when(workouts.getActiveWorkout(1L)).thenReturn(Optional.empty());
        when(services.getWorkoutSessions()).thenReturn(workouts);
        WorkoutHistoryQueryService history = mock(WorkoutHistoryQueryService.class);
        when(history.getWorkoutHistory(1L, 20, 0)).thenReturn(List.of());
        when(services.getHistory()).thenReturn(history);
        ExerciseService exercises = mock(ExerciseService.class);
        when(exercises.listExercises(null, false)).thenReturn(List.of());
        when(services.getExercises()).thenReturn(exercises);
        return services;
    }
}
