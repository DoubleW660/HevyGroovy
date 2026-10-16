package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.PersonalRecordRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalRecordServiceImplTest {
    @Mock PersonalRecordRepository records;
    @Mock LoggedWorkoutRepository workouts;
    @Mock WorkoutExerciseRepository exercises;
    @Mock SetEntryRepository sets;
    PersonalRecordService service;

    @BeforeEach
    void setup() { service = new PersonalRecordServiceImpl(records, workouts, exercises, sets); }

    @Test
    void allTimeRecordsAreCopiedAndUseOnlyFinishedRepositoryQuery() {
        PersonalRecord record = prior(RecordType.HEAVIEST_WEIGHT, 50);
        List<PersonalRecord> source = new ArrayList<>(List.of(record));
        when(records.findBestRecords(1, List.of(7L), null, 0)).thenReturn(source);
        List<PersonalRecord> result = service.getExerciseRecords(1, 7);
        source.clear();
        assertEquals(50, result.get(0).getValue());
        assertThrows(UnsupportedOperationException.class, result::clear);
        verifyNoInteractions(workouts, exercises, sets);
    }

    @Test
    void estimatedMaxUsesDedicatedAggregateAndRejectsInvalidIds() {
        when(records.findBestEstimatedOneRepMax(1, 7)).thenReturn(116.6666667);
        assertEquals(116.6666667, service.getEstimatedOneRepMax(1, 7), 0.000001);
        assertThrows(IllegalArgumentException.class, () -> service.getEstimatedOneRepMax(0, 7));
        assertThrows(IllegalArgumentException.class, () -> service.getEstimatedOneRepMax(1, 0));
        verify(records).findBestEstimatedOneRepMax(1, 7);
        verifyNoMoreInteractions(records);
        verifyNoInteractions(workouts, exercises, sets);
    }

    @Test
    void firstCompletedPerformanceEstablishesFourRecordsAndPreservesSourceUnits() {
        prepare(null, List.of(row(10, 7, 0)), List.of(set(101, 10, 1, 100, 8, Unit.LB)));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of());
        List<PersonalRecord> result = service.getWorkoutRecords(1, 100);
        assertEquals(4, result.size());
        PersonalRecord weight = result.get(0);
        assertEquals(RecordType.HEAVIEST_WEIGHT, weight.getType());
        assertEquals(45.359237, weight.getValue(), 0.000001);
        assertEquals(100, weight.getWeight());
        assertEquals(Unit.LB, weight.getUnit());
        assertEquals(8, weight.getReps());
        assertEquals(101, weight.getSetId());
        assertEquals("Bench snapshot", weight.getExerciseName());
        assertEquals(362.873896, result.get(3).getValue(), 0.000001);
        assertEquals(0, result.get(3).getSetId());
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test
    void equivalentWeightsAndVolumesInDifferentUnitsDoNotCreateNewRecords() {
        prepare(null, List.of(row(10, 7, 0)), List.of(set(101, 10, 1, 100, 10, Unit.LB)));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of(
                prior(RecordType.HEAVIEST_WEIGHT, 45.359237), prior(RecordType.MOST_REPS, 10),
                prior(RecordType.BEST_SET_VOLUME, 453.59237), prior(RecordType.WORKOUT_VOLUME, 453.59237)));
        assertTrue(service.getWorkoutRecords(1, 100).isEmpty());
    }

    @Test
    void insignificantFloatingDifferencesAreNotNewRecords() {
        prepare(null, List.of(row(10, 7, 0)), List.of(set(101, 10, 1, 50.00000005, 10, Unit.KG)));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of(
                prior(RecordType.HEAVIEST_WEIGHT, 50), prior(RecordType.MOST_REPS, 10),
                prior(RecordType.BEST_SET_VOLUME, 500), prior(RecordType.WORKOUT_VOLUME, 500)));
        assertTrue(service.getWorkoutRecords(1, 100).isEmpty());
    }

    @Test
    void totalExerciseVolumeCombinesDuplicateRowsAndProducesOneAwardOnFirstEligibleRow() {
        prepare(null, List.of(row(20, 7, 1), row(10, 7, 0)), List.of(
                set(202, 20, 1, 50, 6, Unit.KG), set(101, 10, 1, 100, 5, Unit.KG)));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of(
                prior(RecordType.HEAVIEST_WEIGHT, 150), prior(RecordType.MOST_REPS, 20),
                prior(RecordType.BEST_SET_VOLUME, 1000), prior(RecordType.WORKOUT_VOLUME, 700)));
        List<PersonalRecord> result = service.getWorkoutRecords(1, 100);
        assertEquals(1, result.size());
        assertEquals(RecordType.WORKOUT_VOLUME, result.get(0).getType());
        assertEquals(800, result.get(0).getValue());
        assertEquals(10, result.get(0).getWorkoutExerciseId());
        assertEquals(11, result.get(0).getReps());
        verify(records).findBestRecords(1, List.of(7L), null, 100);
        verify(sets).findByLoggedWorkoutId(100);
        verify(sets, never()).findByWorkoutExerciseId(anyLong());
    }

    @Test
    void excludesWarmupsPlaceholdersAndZeroRepsButKeepsBodyweightRepRecords() {
        SetEntry warmup = set(1, 10, 1, 999, 99, Unit.KG);
        warmup.setSetType(SetType.WARMUP);
        SetEntry incomplete = set(2, 10, 2, 999, 99, Unit.KG);
        incomplete.setCompleted(false);
        prepare(null, List.of(row(10, 7, 0)), List.of(warmup, incomplete,
                set(3, 10, 3, 500, 0, Unit.KG), set(4, 10, 4, 0, 20, Unit.KG)));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of());
        List<PersonalRecord> result = service.getWorkoutRecords(1, 100);
        assertEquals(1, result.size());
        assertEquals(RecordType.MOST_REPS, result.get(0).getType());
        assertEquals(20, result.get(0).getValue());
        assertEquals(4, result.get(0).getSetId());
    }

    @Test
    void includesFailureAndDropSetsAndKeepsRecordCategoriesIndependent() {
        SetEntry failure = set(1, 10, 1, 100, 5, Unit.KG);
        failure.setSetType(SetType.FAILURE);
        SetEntry drop = set(2, 10, 2, 50, 20, Unit.KG);
        drop.setSetType(SetType.DROP);
        prepare(null, List.of(row(10, 7, 0)), List.of(drop, failure));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of());
        List<PersonalRecord> result = service.getWorkoutRecords(1, 100);
        assertEquals(1, result.get(0).getSetId());
        assertEquals(2, result.get(1).getSetId());
        assertEquals(2, result.get(2).getSetId());
        assertEquals(1500, result.get(3).getValue());
    }

    @Test
    void finishedHistoricalAwardsUseEndedAtAndIdCutoffRatherThanCurrentAllTimeRecords() {
        prepare(2000L, List.of(row(10, 7, 0)), List.of(set(101, 10, 1, 50, 10, Unit.KG)));
        when(records.findBestRecords(1, List.of(7L), 2000L, 100)).thenReturn(List.of());
        List<PersonalRecord> result = service.getWorkoutRecords(1, 100);
        assertEquals(4, result.size());
        assertEquals(2000L, result.get(0).getAchievedAtEpochMillis());
        verify(records, never()).findBestRecords(1, List.of(7L), null, 0);
    }

    @Test
    void tiedSetsChooseFirstByExerciseOrderThenSetNumberAndId() {
        prepare(null, List.of(row(20, 7, 1), row(10, 7, 0)), List.of(
                set(203, 20, 1, 50, 10, Unit.KG), set(102, 10, 2, 50, 10, Unit.KG),
                set(101, 10, 1, 50, 10, Unit.KG)));
        when(records.findBestRecords(1, List.of(7L), null, 100)).thenReturn(List.of());
        List<PersonalRecord> result = service.getWorkoutRecords(1, 100);
        assertEquals(101, result.get(0).getSetId());
        assertEquals(101, result.get(1).getSetId());
        assertEquals(101, result.get(2).getSetId());
        assertEquals(1500, result.get(3).getValue());
    }

    @Test
    void multipleExerciseBaselinesAreLoadedTogether() {
        prepare(null, List.of(row(10, 7, 0), row(20, 8, 1)), List.of(
                set(101, 10, 1, 50, 10, Unit.KG), set(201, 20, 1, 25, 5, Unit.KG)));
        when(records.findBestRecords(1, List.of(7L, 8L), null, 100)).thenReturn(List.of());
        assertEquals(8, service.getWorkoutRecords(1, 100).size());
        verify(records).findBestRecords(1, List.of(7L, 8L), null, 100);
        verifyNoMoreInteractions(records);
    }

    @Test
    void emptyOrEntirelyIneligibleWorkoutDoesNotQueryHistory() {
        when(workouts.findById(100)).thenReturn(Optional.of(workout(null)));
        when(exercises.findByLoggedWorkoutId(100)).thenReturn(List.of());
        assertTrue(service.getWorkoutRecords(1, 100).isEmpty());
        verifyNoInteractions(sets, records);
    }

    @Test
    void ownershipAndMissingWorkoutAreRejectedBeforeChildrenOrRecordsLoad() {
        LoggedWorkout otherUser = workout(null);
        otherUser.setUserId(2);
        when(workouts.findById(100)).thenReturn(Optional.empty(), Optional.of(otherUser));
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutRecords(1, 100));
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutRecords(1, 100));
        verifyNoInteractions(exercises, sets, records);
    }

    @Test
    void invalidIdsDoNotReachPersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.getExerciseRecords(0, 7));
        assertThrows(IllegalArgumentException.class, () -> service.getExerciseRecords(1, -1));
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutRecords(-1, 100));
        assertThrows(IllegalArgumentException.class, () -> service.getWorkoutRecords(1, 0));
        verifyNoInteractions(workouts, exercises, sets, records);
    }

    private void prepare(Long ended, List<WorkoutExercise> rows, List<SetEntry> entries) {
        when(workouts.findById(100)).thenReturn(Optional.of(workout(ended)));
        when(exercises.findByLoggedWorkoutId(100)).thenReturn(rows);
        when(sets.findByLoggedWorkoutId(100)).thenReturn(entries);
    }

    private LoggedWorkout workout(Long ended) {
        LoggedWorkout row = new LoggedWorkout();
        row.setId(100); row.setUserId(1); row.setStartedAtEpochMillis(1000);
        row.setEndedAtEpochMillis(ended);
        return row;
    }

    private WorkoutExercise row(long id, long exerciseId, int order) {
        WorkoutExercise row = new WorkoutExercise();
        row.setId(id); row.setExerciseId(exerciseId); row.setLoggedWorkoutId(100);
        row.setOrderIndex(order); row.setExerciseNameSnapshot("Bench snapshot");
        return row;
    }

    private SetEntry set(long id, long rowId, int number, double weight, int reps, Unit unit) {
        SetEntry set = new SetEntry();
        set.setId(id); set.setWorkoutExerciseId(rowId); set.setSetNumber(number);
        set.setWeight(weight); set.setReps(reps); set.setUnit(unit);
        set.setSetType(SetType.NORMAL); set.setCompleted(true); set.setCreatedAtEpochMillis(1200);
        return set;
    }

    private PersonalRecord prior(RecordType type, double value) {
        return new PersonalRecord(type, 7, "Older name", 99, 9, 90, value, 50, Unit.KG, 10, 900);
    }
}
