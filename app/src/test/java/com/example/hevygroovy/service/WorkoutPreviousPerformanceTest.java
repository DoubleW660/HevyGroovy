package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.ActiveSetEntry;
import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import com.example.hevygroovy.dto.ActiveWorkoutExercise;
import com.example.hevygroovy.dto.PreviousExercisePerformance;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkoutPreviousPerformanceTest {
    private final LoggedWorkoutRepository workouts = mock(LoggedWorkoutRepository.class);
    private final WorkoutExerciseRepository exercises = mock(WorkoutExerciseRepository.class);
    private final SetEntryRepository sets = mock(SetEntryRepository.class);
    private WorkoutSessionService service;

    @BeforeEach
    void setup() {
        service = new WorkoutSessionServiceImpl(workouts, mock(TemplateWorkoutRepository.class),
                mock(TemplateExerciseRepository.class), exercises, sets, mock(ExerciseService.class));
        LoggedWorkout active = new LoggedWorkout();
        active.setId(100); active.setUserId(1); active.setStartedAtEpochMillis(1000);
        when(workouts.findActiveWorkoutByUserId(1)).thenReturn(Optional.of(active));
    }

    @Test
    void loadsPreviousExercisesInOneBatchAndMatchesDuplicateRowsByOccurrence() {
        when(exercises.findByLoggedWorkoutId(100)).thenReturn(List.of(
                row(20, 7, 2), row(11, 8, 1), row(10, 7, 0)));
        ActiveSetEntry kilos = new ActiveSetEntry(set(1, 1, 60, 8, Unit.KG));
        ActiveSetEntry pounds = new ActiveSetEntry(set(2, 3, 135, 6, Unit.LB));
        when(exercises.findPreviousPerformancesForUserExercises(1, List.of(7L, 8L))).thenReturn(Map.of(
                7L, new PreviousExercisePerformance(99, 900, List.of(List.of(kilos), List.of(pounds)))));

        ActiveWorkoutDetail detail = service.getActiveWorkout(1).orElseThrow();

        assertEquals(60, detail.getExercises().get(0).getPreviousSets().get(0).getWeight());
        assertEquals(Unit.KG, detail.getExercises().get(0).getPreviousSets().get(0).getUnit());
        assertEquals(99L, detail.getExercises().get(0).getPreviousWorkoutId());
        assertEquals(900L, detail.getExercises().get(0).getPreviousEndedAtEpochMillis());
        assertTrue(detail.getExercises().get(1).getPreviousSets().isEmpty());
        assertNull(detail.getExercises().get(1).getPreviousWorkoutId());
        assertEquals(Unit.LB, detail.getExercises().get(2).getPreviousSets().get(0).getUnit());
        assertEquals(3, detail.getExercises().get(2).getPreviousSets().get(0).getSetNumber());
        verify(exercises).findPreviousPerformancesForUserExercises(1, List.of(7L, 8L));
        verify(sets).findByLoggedWorkoutId(100);
        verify(sets, never()).findByWorkoutExerciseId(anyLong());
    }

    @Test
    void preservesEmptyDuplicateOccurrencesRatherThanShiftingLaterSetsForward() {
        when(exercises.findByLoggedWorkoutId(100)).thenReturn(List.of(row(10, 7, 0), row(20, 7, 1), row(30, 7, 2)));
        ActiveSetEntry previous = new ActiveSetEntry(set(1, 1, 50, 5, Unit.KG));
        when(exercises.findPreviousPerformancesForUserExercises(1, List.of(7L))).thenReturn(Map.of(
                7L, new PreviousExercisePerformance(99, 900, List.of(List.of(), List.of(previous)))));
        List<ActiveWorkoutExercise> result = service.getActiveWorkout(1).orElseThrow().getExercises();
        assertTrue(result.get(0).getPreviousSets().isEmpty());
        assertEquals(50, result.get(1).getPreviousSets().get(0).getWeight());
        assertTrue(result.get(2).getPreviousSets().isEmpty());
    }

    @Test
    void calculatesCurrentEstimateOnlyFromCompletedWorkingSetsAndRetainsImmutableHistory() {
        WorkoutExercise exercise = row(10, 7, 0);
        SetEntry normal = set(1, 1, 100, 5, Unit.KG);
        SetEntry warmup = set(2, 2, 500, 5, Unit.KG);
        warmup.setSetType(SetType.WARMUP);
        SetEntry incomplete = set(3, 3, 500, 5, Unit.KG);
        incomplete.setCompleted(false);
        List<ActiveSetEntry> source = new ArrayList<>(List.of(new ActiveSetEntry(normal)));
        List<List<ActiveSetEntry>> occurrenceSource = new ArrayList<>(List.of(source));
        PreviousExercisePerformance previous = new PreviousExercisePerformance(99, 900, occurrenceSource);
        ActiveWorkoutExercise result = new ActiveWorkoutExercise(exercise,
                List.of(new ActiveSetEntry(normal), new ActiveSetEntry(warmup), new ActiveSetEntry(incomplete)),
                previous.getSetsByOccurrence().get(0), 99L, 900L);
        source.clear(); occurrenceSource.clear(); normal.setWeight(999);
        assertEquals(116.6666667, result.getEstimatedOneRepMax(), 0.000001);
        assertEquals(100, result.getPreviousSets().get(0).getWeight());
        assertThrows(UnsupportedOperationException.class, () -> result.getPreviousSets().clear());
        assertThrows(UnsupportedOperationException.class, () -> previous.getSetsByOccurrence().clear());
        assertThrows(UnsupportedOperationException.class, () -> previous.getSetsByOccurrence().get(0).clear());
    }

    @Test
    void emptyActiveWorkoutDoesNotRequestPreviousHistory() {
        when(exercises.findByLoggedWorkoutId(100)).thenReturn(List.of());
        assertTrue(service.getActiveWorkout(1).orElseThrow().getExercises().isEmpty());
        verify(exercises, never()).findPreviousPerformancesForUserExercises(anyLong(), anyList());
    }

    private WorkoutExercise row(long id, long exerciseId, int order) {
        WorkoutExercise row = new WorkoutExercise();
        row.setId(id); row.setExerciseId(exerciseId); row.setLoggedWorkoutId(100); row.setOrderIndex(order);
        return row;
    }

    private SetEntry set(long id, int number, double weight, int reps, Unit unit) {
        SetEntry set = new SetEntry();
        set.setId(id); set.setWorkoutExerciseId(10); set.setSetNumber(number); set.setWeight(weight);
        set.setReps(reps); set.setUnit(unit); set.setSetType(SetType.NORMAL); set.setCompleted(true);
        return set;
    }
}
