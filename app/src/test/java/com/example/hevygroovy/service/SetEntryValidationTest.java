package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.SetType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetEntryValidationTest {
    @Test
    void omittedOptionalFieldsArePreservedButExplicitNullClearsThem() {
        SetEntry set = ownedSet();
        when(sets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.updateSet(1L, 50L, new UpdateSetEntryRequest());
        assertEquals(Float.valueOf(7F), set.getRpe());
        assertEquals(Integer.valueOf(60), set.getRestSeconds());
        UpdateSetEntryRequest clear = new UpdateSetEntryRequest();
        clear.setRpe(null);
        clear.setRestSeconds(null);
        service.updateSet(1L, 50L, clear);
        assertNull(set.getRpe());
        assertNull(set.getRestSeconds());
    }
    @Test
    void completionCanBeSetClearedAndOmitted() {
        SetEntry set = ownedSet();
        assertFalse(set.isCompleted());
        when(sets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setCompleted(true);
        service.updateSet(1L, 50L, request);
        assertTrue(set.isCompleted());
        service.updateSet(1L, 50L, new UpdateSetEntryRequest());
        assertTrue(set.isCompleted());
        request.setCompleted(false);
        service.updateSet(1L, 50L, request);
        assertFalse(set.isCompleted());
    }

    @Test
    void invalidUpdateCannotChangeCompletionState() {
        SetEntry set = ownedSet();
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setCompleted(true);
        request.setReps(-1);
        assertThrows(RuntimeException.class, () -> service.updateSet(1L, 50L, request));
        assertFalse(set.isCompleted());
        verify(sets, never()).save(any());
    }
    @Mock private SetEntryRepository sets;
    @Mock private WorkoutExerciseRepository exercises;
    @Mock private LoggedWorkoutRepository workouts;
    private SetEntryService service;

    @BeforeEach
    void setUp() {
        service = new SetEntryServiceImpl(sets, exercises, workouts);
    }

    @Test
    void nullRequestIsRejectedBeforeRepositoryAccess() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.updateSet(1L, 50L, null));
        assertEquals("Set update request is required", error.getMessage());
        verifyNoInteractions(sets, exercises, workouts);
    }

    @Test
    void invalidLaterFieldsDoNotPartiallyMutateSet() {
        SetEntry set = ownedSet();
        for (int invalidField = 0; invalidField < 4; invalidField++) {
            UpdateSetEntryRequest request = validUpdate();
            switch (invalidField) {
                case 0: request.setReps(-1); break;
                case 1: request.setWeight(-1D); break;
                case 2: request.setRpe(11F); break;
                default: request.setRestSeconds(-1);
            }
            assertThrows(RuntimeException.class, () -> service.updateSet(1L, 50L, request));
            assertEquals(5, set.getReps());
            assertEquals(100D, set.getWeight());
            assertEquals(Unit.LB, set.getUnit());
            assertEquals(Float.valueOf(7F), set.getRpe());
            assertEquals(SetType.NORMAL, set.getSetType());
            assertEquals(Integer.valueOf(60), set.getRestSeconds());
        }
        verify(sets, never()).save(any());
    }

    @Test
    void nonfiniteWeightsAreRejectedWithoutMutation() {
        SetEntry set = ownedSet();
        for (double weight : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            UpdateSetEntryRequest request = validUpdate();
            request.setWeight(weight);
            assertThrows(RuntimeException.class, () -> service.updateSet(1L, 50L, request));
            assertEquals(100D, set.getWeight());
            assertEquals(5, set.getReps());
        }
        verify(sets, never()).save(any());
    }

    @Test
    void nonfiniteAndOutOfRangeRpeAreRejectedWithoutMutation() {
        SetEntry set = ownedSet();
        for (float rpe : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1F, 11F}) {
            UpdateSetEntryRequest request = validUpdate();
            request.setRpe(rpe);
            assertThrows(RuntimeException.class, () -> service.updateSet(1L, 50L, request));
            assertEquals(Float.valueOf(7F), set.getRpe());
            assertEquals(100D, set.getWeight());
        }
        verify(sets, never()).save(any());
    }

    @Test
    void boundaryValuesAreAcceptedAndOmittedFieldsArePreserved() {
        SetEntry set = ownedSet();
        when(sets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setReps(0);
        request.setWeight(0D);
        request.setRestSeconds(0);
        request.setRpe(0F);
        assertSame(set, service.updateSet(1L, 50L, request));
        assertEquals(0, set.getReps());
        assertEquals(0D, set.getWeight());
        assertEquals(Integer.valueOf(0), set.getRestSeconds());
        assertEquals(Float.valueOf(0F), set.getRpe());
        request = new UpdateSetEntryRequest();
        request.setRpe(10F);
        service.updateSet(1L, 50L, request);
        assertEquals(Float.valueOf(10F), set.getRpe());
        assertEquals(Unit.LB, set.getUnit());
        assertEquals(SetType.NORMAL, set.getSetType());
        assertEquals(1, set.getSetNumber());
        assertEquals(10L, set.getWorkoutExerciseId());
    }

    @Test
    void appendUsesHighestSetNumberRatherThanRowCount() {
        ownedWorkoutExercise();
        SetEntry first = new SetEntry();
        first.setSetNumber(1);
        SetEntry last = new SetEntry();
        last.setSetNumber(4);
        when(sets.findByWorkoutExerciseId(10L)).thenReturn(List.of(last, first));
        when(sets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SetEntry added = service.addSet(1L, 10L);
        assertEquals(5, added.getSetNumber());
        assertFalse(added.isCompleted());
    }

    @Test
    void deleteReindexesImmutableRepositoryResultsWithoutChangingTheirOrder() {
        ownedSet();
        SetEntry first = new SetEntry();
        first.setSetNumber(1);
        SetEntry last = new SetEntry();
        last.setSetNumber(3);
        List<SetEntry> remaining = List.of(last, first);
        when(sets.findByWorkoutExerciseId(10L)).thenReturn(remaining);
        service.deleteSet(1L, 50L);
        assertEquals(1, first.getSetNumber());
        assertEquals(2, last.getSetNumber());
        assertSame(last, remaining.get(0));
        verify(sets).delete(50L);
        verify(sets).save(last);
        verify(sets, never()).save(first);
    }

    private SetEntry ownedSet() {
        ownedWorkoutExercise();
        SetEntry set = new SetEntry();
        set.setId(50L);
        set.setWorkoutExerciseId(10L);
        set.setSetNumber(1);
        set.setReps(5);
        set.setWeight(100D);
        set.setUnit(Unit.LB);
        set.setRpe(7F);
        set.setRestSeconds(60);
        set.setSetType(SetType.NORMAL);
        when(sets.findById(50L)).thenReturn(Optional.of(set));
        return set;
    }

    private void ownedWorkoutExercise() {
        WorkoutExercise exercise = new WorkoutExercise();
        exercise.setLoggedWorkoutId(100L);
        when(exercises.findById(10L)).thenReturn(Optional.of(exercise));
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(1L);
        when(workouts.findById(100L)).thenReturn(Optional.of(workout));
    }

    private UpdateSetEntryRequest validUpdate() {
        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setReps(10);
        request.setWeight(200D);
        request.setUnit(Unit.KG);
        request.setRpe(9F);
        request.setRestSeconds(90);
        request.setSetType(SetType.DROP);
        return request;
    }
}
