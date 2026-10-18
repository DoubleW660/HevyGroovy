package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.dto.ExercisePerformance;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.model.ExerciseModel;
import com.example.hevygroovy.service.ExerciseService;
import com.example.hevygroovy.service.PersonalRecordService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceRecordGatewayTest {
    @Test void usesTheLocalUserAndCopiesTheSavedRecordResponse() {
        AppServices services = mock(AppServices.class);
        PersonalRecordService records = mock(PersonalRecordService.class);
        ExerciseService exercises = mock(ExerciseService.class);
        when(services.getPersonalRecords()).thenReturn(records);
        when(services.getExercises()).thenReturn(exercises);
        ExerciseModel exercise = new ExerciseModel();
        exercise.setExerciseId(9); exercise.setTitle("Bench"); exercise.setDescription("How to bench");
        exercise.setArchived(true);
        when(exercises.getExercise(9)).thenReturn(exercise);
        List<PersonalRecord> source = new ArrayList<>();
        source.add(new PersonalRecord(RecordType.MOST_REPS, 9, "Bench", 3, 4, 5, 12, 100, Unit.LB, 12, 1000));
        when(records.getExerciseRecords(42, 9)).thenReturn(source);
        when(records.getEstimatedOneRepMax(42, 9)).thenReturn(70.0);
        SetEntry set = new SetEntry();
        set.setId(5); set.setCompleted(true); set.setWeight(100); set.setUnit(Unit.LB);
        set.setReps(12); set.setSetType(SetType.NORMAL);
        List<ExercisePerformance> history = new ArrayList<>(List.of(new ExercisePerformance(3, 9,
                "Bench", "Push", 900, 1000, List.of(set), "Notes", false)));
        when(records.getExerciseHistory(42, 9)).thenReturn(history);
        AtomicReference<RecordGateway.Snapshot> result = new AtomicReference<>();
        new ServiceRecordGateway(new AppWorker(Runnable::run, Runnable::run, () -> services, () -> 42))
                .loadExercise(9, new RecordGateway.Callback() {
                    public void onSuccess(RecordGateway.Snapshot value) { result.set(value); }
                    public void onFailure(Exception error) { fail(error); }
                });
        source.clear();
        history.clear();
        verify(records).getExerciseRecords(42, 9);
        verify(records).getEstimatedOneRepMax(42, 9);
        verify(records).getExerciseHistory(42, 9);
        verify(exercises).getExercise(9);
        assertEquals(70.0, result.get().getEstimatedOneRepMaxKg());
        assertEquals(1, result.get().getRecords().size());
        assertEquals("How to bench", result.get().getExercise().getDescription());
        assertTrue(result.get().getExercise().isArchived());
        assertEquals(3, result.get().getHistory().get(0).getWorkoutId());
        assertEquals(12, result.get().getRepRecords().get(0).getReps());
        assertThrows(UnsupportedOperationException.class, () -> result.get().getRecords().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.get().getHistory().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.get().getRepRecords().clear());
    }
}
