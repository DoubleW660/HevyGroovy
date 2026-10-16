package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutHistoryQueryServiceImplTest {
    @Test
    void placeholdersAreExcludedFromSummaryDetailAndExerciseStatistics() {
        LoggedWorkout workout = workout(100L, 1L, 1000L, 2000L);
        when(workouts.findByUserId(1L)).thenReturn(List.of(workout));
        when(workouts.findById(100L)).thenReturn(Optional.of(workout));
        when(exercises.findByLoggedWorkoutId(100L)).thenReturn(List.of(exercise(10L, 0)));
        SetEntry completed = set(10L, 1, 50, 5, Unit.KG);
        SetEntry placeholder = set(10L, 2, 999, 999, Unit.KG);
        placeholder.setCompleted(false);
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(completed, placeholder));
        WorkoutHistory summary = service.getWorkoutHistory(1L).get(0);
        WorkoutHistoryDetail detail = service.getWorkoutHistoryDetail(1L, 100L);
        assertEquals(1, summary.getTotalSets());
        assertEquals(5, summary.getTotalReps());
        assertEquals(250D, summary.getTotalVolume());
        assertEquals(1, detail.getTotalSets());
        assertEquals(250D, detail.getTotalVolume());
        assertEquals(1, detail.getExercises().get(0).getSets().size());
        assertEquals(50D, detail.getExercises().get(0).getHeaviestWeight());
    }
    @Mock private LoggedWorkoutRepository workouts;
    @Mock private WorkoutExerciseRepository exercises;
    @Mock private SetEntryRepository sets;
    private WorkoutHistoryQueryService service;

    @BeforeEach
    void setUp() { service = new WorkoutHistoryQueryServiceImpl(workouts, exercises, sets); }

    @Test
    void detailUsesSnapshotsOrdersRowsAndConvertsMixedUnits() {
        LoggedWorkout workout = workout(100L, 1L, 1000L, 61000L);
        when(workouts.findById(100L)).thenReturn(Optional.of(workout));
        WorkoutExercise later = exercise(20L, 1);
        WorkoutExercise first = exercise(10L, 0);
        when(exercises.findByLoggedWorkoutId(100L)).thenReturn(List.of(later, first));
        SetEntry pounds = set(10L, 2, 100, 5, Unit.LB);
        SetEntry kilos = set(10L, 1, 50, 10, Unit.KG);
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(pounds, kilos));

        WorkoutHistoryDetail detail = service.getWorkoutHistoryDetail(1L, 100L);

        assertEquals("Snapshot", detail.getNameSnapshot());
        assertEquals("Workout note", detail.getNotes());
        assertEquals(60L, detail.getDurationSeconds());
        assertEquals(2, detail.getTotalExercises());
        assertEquals(2, detail.getTotalSets());
        assertEquals(15, detail.getTotalReps());
        assertEquals(726.796185, detail.getTotalVolume(), 0.000001);
        assertEquals(Unit.KG, detail.getVolumeUnit());
        WorkoutExerciseHistory firstDetail = detail.getExercises().get(0);
        assertEquals(10L, firstDetail.getWorkoutExerciseId());
        assertEquals("Saved exercise name", firstDetail.getExerciseName());
        assertEquals("Exercise note", firstDetail.getNotes());
        assertEquals(1, firstDetail.getSets().get(0).getSetNumber());
        assertEquals(50, firstDetail.getHeaviestWeight(), 0.000001);
        assertEquals(500, firstDetail.getBestSetVolume(), 0.000001);
        assertEquals(Unit.LB, firstDetail.getSets().get(1).getUnit());
        assertEquals(0, detail.getExercises().get(1).getTotalSets());
        assertThrows(UnsupportedOperationException.class, () -> detail.getExercises().clear());
    }

    @Test
    void detailsRejectMissingOtherUsersAndActiveWorkoutsBeforeLoadingChildren() {
        when(workouts.findById(100L)).thenReturn(Optional.empty(),
                Optional.of(workout(100L, 2L, 1000L, 2000L)),
                Optional.of(workout(100L, 1L, 1000L, null)));
        assertEquals("Workout Not Found", assertThrows(RuntimeException.class,
                () -> service.getWorkoutHistoryDetail(1L, 100L)).getMessage());
        assertEquals("Workout does not belong to user", assertThrows(RuntimeException.class,
                () -> service.getWorkoutHistoryDetail(1L, 100L)).getMessage());
        assertEquals("Workout has not been completed", assertThrows(RuntimeException.class,
                () -> service.getWorkoutHistoryDetail(1L, 100L)).getMessage());
        verifyNoInteractions(exercises, sets);
    }

    @Test
    void invalidIdsAreRejectedBeforeRepositoryAccess() {
        for (long invalid : new long[]{0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> service.getWorkoutHistory(invalid));
            assertThrows(IllegalArgumentException.class, () -> service.getWorkoutHistoryDetail(invalid, 100L));
            assertThrows(IllegalArgumentException.class, () -> service.getWorkoutHistoryDetail(1L, invalid));
        }
        verifyNoInteractions(workouts, exercises, sets);
    }

    @Test
    void historyExcludesActiveAndOtherUsersAndSortsNewestFirstWithIdTies() {
        LoggedWorkout old = workout(100L, 1L, 1000L, 2000L);
        LoggedWorkout newer = workout(200L, 1L, 3000L, 4000L);
        LoggedWorkout tied = workout(300L, 1L, 3000L, 4000L);
        when(workouts.findByUserId(1L)).thenReturn(List.of(old,
                workout(400L, 1L, 5000L, null), workout(500L, 2L, 6000L, 7000L), newer, tied));
        List<WorkoutHistory> history = service.getWorkoutHistory(1L);
        assertEquals(3, history.size());
        assertEquals(300L, history.get(0).getWorkoutId());
        assertEquals(200L, history.get(1).getWorkoutId());
        assertEquals(100L, history.get(2).getWorkoutId());
        verify(exercises, never()).findByLoggedWorkoutId(400L);
        verify(sets, never()).findByLoggedWorkoutId(500L);
    }

    @Test
    void summaryAndDetailUseTheSameVolumeUnitAndTotals() {
        LoggedWorkout workout = workout(100L, 1L, 1000L, 2000L);
        when(workouts.findByUserId(1L)).thenReturn(List.of(workout));
        when(workouts.findById(100L)).thenReturn(Optional.of(workout));
        when(exercises.findByLoggedWorkoutId(100L)).thenReturn(List.of(exercise(10L, 0)));
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(set(10L, 1, 100, 5, Unit.LB)));
        WorkoutHistory summary = service.getWorkoutHistory(1L).get(0);
        WorkoutHistoryDetail detail = service.getWorkoutHistoryDetail(1L, 100L);
        assertEquals(summary.getTotalVolume(), detail.getTotalVolume(), 0.000001);
        assertEquals(summary.getTotalReps(), detail.getTotalReps());
        assertEquals(summary.getVolumeUnit(), detail.getVolumeUnit());
    }

    private LoggedWorkout workout(long id, long userId, long start, Long end) {
        LoggedWorkout row = new LoggedWorkout();
        row.setId(id);
        row.setUserId(userId);
        row.setNameSnapshot("Snapshot");
        row.setNotes("Workout note");
        row.setStartedAtEpochMillis(start);
        row.setEndedAtEpochMillis(end);
        return row;
    }

    private WorkoutExercise exercise(long id, int order) {
        WorkoutExercise row = new WorkoutExercise();
        row.setId(id);
        row.setExerciseId(9L);
        row.setOrderIndex(order);
        row.setExerciseNameSnapshot("Saved exercise name");
        row.setNotes("Exercise note");
        return row;
    }

    private SetEntry set(long exercise, int number, double weight, int reps, Unit unit) {
        SetEntry row = new SetEntry();
        row.setWorkoutExerciseId(exercise);
        row.setSetNumber(number);
        row.setWeight(weight);
        row.setReps(reps);
        row.setUnit(unit);
        row.setCompleted(true);
        return row;
    }
}
