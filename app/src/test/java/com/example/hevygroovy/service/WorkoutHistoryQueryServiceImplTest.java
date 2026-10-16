package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.*;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.SetType;
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
    void pageUsesOneSummaryQueryWithoutLoadingChildren() {
        WorkoutHistory summary = summary(100L, 0, 0, 0);
        when(workouts.findCompletedSummaries(1L, 20, 40)).thenReturn(List.of(summary));
        List<WorkoutHistory> page = service.getWorkoutHistory(1L, 20, 40);
        assertEquals(1, page.size());
        assertEquals(100L, page.get(0).getWorkoutId());
        verify(workouts).findCompletedSummaries(1L, 20, 40);
        verify(workouts, never()).findByUserId(anyLong());
        verifyNoInteractions(exercises, sets);
    }

    @Test
    void zeroLimitAndInvalidPaginationAvoidRepositoryAccess() {
        assertTrue(service.getWorkoutHistory(1L, 0, 100).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutHistory(1L, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutHistory(1L, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutHistory(0L, 10, 0));
        verifyNoInteractions(workouts, exercises, sets);
    }

    @Test
    void emptyPageDoesNotLoadChildRows() {
        when(workouts.findCompletedSummaries(1L, 10, 100)).thenReturn(List.of());
        assertTrue(service.getWorkoutHistory(1L, 10, 100).isEmpty());
        verifyNoInteractions(exercises, sets);
    }
    @Test
    void placeholdersAreExcludedFromSummaryDetailAndExerciseStatistics() {
        LoggedWorkout workout = workout(100L, 1L, 1000L, 2000L);
        when(workouts.findCompletedSummaries(1L, Integer.MAX_VALUE, 0))
                .thenReturn(List.of(summary(100L, 1, 5, 250)));
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
        pounds.setWeight(999);
        assertEquals(100D, firstDetail.getSets().get(1).getWeight());
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
    void unpagedHistoryUsesRepositoryOrderingWithoutChildQueries() {
        when(workouts.findCompletedSummaries(1L, Integer.MAX_VALUE, 0))
                .thenReturn(List.of(summary(300L, 0, 0, 0), summary(200L, 0, 0, 0), summary(100L, 0, 0, 0)));
        List<WorkoutHistory> history = service.getWorkoutHistory(1L);
        assertEquals(3, history.size());
        assertEquals(300L, history.get(0).getWorkoutId());
        assertEquals(200L, history.get(1).getWorkoutId());
        assertEquals(100L, history.get(2).getWorkoutId());
        verifyNoInteractions(exercises, sets);
    }

    @Test
    void summaryAndDetailUseTheSameVolumeUnitAndTotals() {
        LoggedWorkout workout = workout(100L, 1L, 1000L, 2000L);
        when(workouts.findCompletedSummaries(1L, Integer.MAX_VALUE, 0))
                .thenReturn(List.of(summary(100L, 1, 5, 226.796185)));
        when(workouts.findById(100L)).thenReturn(Optional.of(workout));
        when(exercises.findByLoggedWorkoutId(100L)).thenReturn(List.of(exercise(10L, 0)));
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(set(10L, 1, 100, 5, Unit.LB)));
        WorkoutHistory summary = service.getWorkoutHistory(1L).get(0);
        WorkoutHistoryDetail detail = service.getWorkoutHistoryDetail(1L, 100L);
        assertEquals(summary.getTotalVolume(), detail.getTotalVolume(), 0.000001);
        assertEquals(summary.getTotalReps(), detail.getTotalReps());
        assertEquals(summary.getVolumeUnit(), detail.getVolumeUnit());
    }

    @Test
    void historyIncludesImmutableAwardsAndCountsThemOnlyOnTheirSourceRows() {
        PersonalRecordService personalRecords = mock(PersonalRecordService.class);
        WorkoutHistoryQueryService withRecords = new WorkoutHistoryQueryServiceImpl(
                workouts, exercises, sets, personalRecords);
        when(workouts.findById(100L)).thenReturn(Optional.of(workout(100L, 1L, 1000L, 2000L)));
        when(exercises.findByLoggedWorkoutId(100L)).thenReturn(List.of(exercise(10L, 0), exercise(20L, 1)));
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(set(10L, 1, 50, 10, Unit.KG)));
        PersonalRecord award = new PersonalRecord(RecordType.WORKOUT_VOLUME, 9, "Saved exercise name",
                100, 10, 0, 500, 0, Unit.KG, 10, 2000);
        List<PersonalRecord> source = new java.util.ArrayList<>(List.of(award));
        when(personalRecords.getWorkoutRecords(1L, 100L)).thenReturn(source);

        WorkoutHistoryDetail detail = withRecords.getWorkoutHistoryDetail(1L, 100L);

        source.clear();
        assertEquals(1, detail.getPersonalRecords().size());
        assertEquals(1, detail.getPersonalRecordCount());
        assertEquals(0, summary(100L, 1, 10, 500).getPersonalRecordCount());
        assertTrue(detail.getExercises().get(0).isPersonalRecord());
        assertEquals(1, detail.getExercises().get(0).getPersonalRecordCount());
        assertFalse(detail.getExercises().get(1).isPersonalRecord());
        assertEquals(0, detail.getExercises().get(1).getPersonalRecordCount());
        assertThrows(UnsupportedOperationException.class, () -> detail.getPersonalRecords().clear());
        verify(personalRecords).getWorkoutRecords(1L, 100L);
    }

    @Test
    void historyEstimateUsesBestCompletedWorkingSetWithinTenRepsInKg() {
        when(workouts.findById(100L)).thenReturn(Optional.of(workout(100L, 1L, 1000L, 2000L)));
        when(exercises.findByLoggedWorkoutId(100L)).thenReturn(List.of(exercise(10L, 0)));
        SetEntry pounds = set(10L, 1, 225, 5, Unit.LB);
        pounds.setSetType(SetType.NORMAL);
        SetEntry warmup = set(10L, 2, 500, 5, Unit.KG);
        warmup.setSetType(SetType.WARMUP);
        SetEntry highReps = set(10L, 3, 500, 11, Unit.KG);
        highReps.setSetType(SetType.NORMAL);
        SetEntry incomplete = set(10L, 4, 500, 5, Unit.KG);
        incomplete.setSetType(SetType.NORMAL); incomplete.setCompleted(false);
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(pounds, warmup, highReps, incomplete));

        WorkoutExerciseHistory result = service.getWorkoutHistoryDetail(1L, 100L).getExercises().get(0);

        assertEquals(225 * 0.45359237 * (1 + 5 / 30.0), result.getEstimatedOneRepMax(), 0.000001);
        assertEquals(3, result.getTotalSets());
        assertEquals(500, result.getHeaviestWeight());
    }

    private WorkoutHistory summary(long id, int count, int reps, double volume) {
        return new WorkoutHistory(id, "Snapshot", 1000L, 2000L,
                java.time.Duration.ofSeconds(1), 1, count, reps, volume);
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
