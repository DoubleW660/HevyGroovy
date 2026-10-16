package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.repo.*;
import com.example.hevygroovy.model.ExerciseModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutSessionEditingTest {
    @Test
    void combinedWorkoutEditValidatesBeforeMutatingAndSavesOnce() {
        LoggedWorkout workout = ownedWorkout();
        workout.setNameSnapshot("Original");
        workout.setNotes("Original note");
        assertThrows(RuntimeException.class, () -> service.updateWorkoutDetails(1L, 100L, " ", "Changed"));
        assertEquals("Original", workout.getNameSnapshot());
        assertEquals("Original note", workout.getNotes());
        when(workouts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.updateWorkoutDetails(1L, 100L, "  Upper   body ", "Updated");
        assertEquals("Upper body", workout.getNameSnapshot());
        assertEquals("Updated", workout.getNotes());
        verify(workouts).save(workout);
    }

    @Test
    void combinedExerciseEditValidatesBeforeMutatingAndSavesOnce() {
        ownedWorkout();
        WorkoutExercise row = ownedRow();
        row.setNotes("Original");
        row.setRestTimeSeconds(60);
        assertThrows(RuntimeException.class, () -> service.updateExerciseDetails(1L, 10L, "Changed", -1));
        assertEquals("Original", row.getNotes());
        assertEquals(60, row.getRestTimeSeconds());
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.updateExerciseDetails(1L, 10L, "Brace", 90);
        assertEquals("Brace", row.getNotes());
        assertEquals(90, row.getRestTimeSeconds());
        verify(rows).save(row);
    }
    @Test
    void renameNormalizesNameAndRejectsBlankWithoutMutation() {
        LoggedWorkout workout = ownedWorkout();
        workout.setNameSnapshot("Original");
        for (String name : new String[]{null, "", "  "}) {
            assertThrows(RuntimeException.class, () -> service.renameWorkout(1L, 100L, name));
            assertEquals("Original", workout.getNameSnapshot());
        }
        verify(workouts, never()).save(any());
        when(workouts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertSame(workout, service.renameWorkout(1L, 100L, "  Upper   body  "));
        assertEquals("Upper body", workout.getNameSnapshot());
    }

    @Test
    void workoutNotesCanBeUpdatedAndCleared() {
        LoggedWorkout workout = ownedWorkout();
        when(workouts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertSame(workout, service.updateWorkoutNotes(1L, 100L, "Good session"));
        assertEquals("Good session", workout.getNotes());
        service.updateWorkoutNotes(1L, 100L, "");
        assertEquals("", workout.getNotes());
        service.updateWorkoutNotes(1L, 100L, null);
        assertNull(workout.getNotes());
    }

    @Test
    void discardDeletesSetsThenExercisesThenWorkout() {
        ownedWorkout();
        WorkoutExercise row = row(10L, 0);
        SetEntry set = new SetEntry();
        set.setId(50L);
        when(sets.findByLoggedWorkoutId(100L)).thenReturn(List.of(set));
        when(rows.findByLoggedWorkoutId(100L)).thenReturn(List.of(row));
        service.discardWorkout(1L, 100L);
        org.mockito.InOrder order = inOrder(sets, rows, workouts);
        order.verify(sets).delete(50L);
        order.verify(rows).delete(10L);
        order.verify(workouts).delete(100L);
        verify(workouts, never()).save(any());
    }
    @Mock private LoggedWorkoutRepository workouts;
    @Mock private WorkoutExerciseRepository rows;
    @Mock private SetEntryRepository sets;
    @Mock private TemplateWorkoutRepository templates;
    @Mock private TemplateExerciseRepository templateRows;
    @Mock private TemplateService templateService;
    @Mock private ExerciseService exercises;
    private WorkoutSessionService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutSessionServiceImpl(workouts, templates, templateRows,
                rows, sets, templateService, exercises);
    }

    @Test
    void appendSnapshotsNameAndHistoryAfterLastIndex() {
        ownedWorkout();
        ExerciseModel exercise = new ExerciseModel();
        exercise.setTitle("Squat");
        when(exercises.requireActiveExercise(9L)).thenReturn(exercise);
        when(rows.findMaxOrderIndex(100L)).thenReturn(4);
        when(rows.findLastNotesForUserExercises(1L, List.of(9L)))
                .thenReturn(Map.of(9L, "Brace"));
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutExercise added = service.addExerciseToWorkout(1L, 100L, 9L);

        assertEquals(100L, added.getLoggedWorkoutId());
        assertEquals(9L, added.getExerciseId());
        assertEquals("Squat", added.getExerciseNameSnapshot());
        assertEquals("Brace", added.getNotes());
        assertEquals(5, added.getOrderIndex());
        assertEquals(0, added.getRestTimeSeconds());
        verify(rows, never()).findByLoggedWorkoutId(anyLong());
        verifyNoInteractions(sets);
    }

    @Test
    void appendToEmptyWorkoutStartsAtZeroWithoutNotes() {
        ownedWorkout();
        when(exercises.requireActiveExercise(9L)).thenReturn(new ExerciseModel());
        when(rows.findMaxOrderIndex(100L)).thenReturn(-1);
        when(rows.findLastNotesForUserExercises(1L, List.of(9L))).thenReturn(Map.of());
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WorkoutExercise added = service.addExerciseToWorkout(1L, 100L, 9L);
        assertEquals(0, added.getOrderIndex());
        assertNull(added.getNotes());
    }

    @Test
    void removalDeletesSetsBeforeParentAndClosesOrderGaps() {
        ownedWorkout();
        WorkoutExercise removed = ownedRow();
        WorkoutExercise first = row(2L, 0);
        WorkoutExercise last = row(3L, 2);
        SetEntry set = new SetEntry();
        set.setId(50L);
        when(sets.findByWorkoutExerciseId(10L)).thenReturn(List.of(set));
        when(rows.findByLoggedWorkoutId(100L)).thenReturn(List.of(last, first));

        service.removeExerciseFromWorkout(1L, removed.getId());

        org.mockito.InOrder deletion = inOrder(sets, rows);
        deletion.verify(sets).delete(50L);
        deletion.verify(rows).delete(10L);
        assertEquals(1, last.getOrderIndex());
        assertEquals(0, first.getOrderIndex());
        verify(rows).save(last);
        verify(rows, never()).save(first);
    }

    @Test
    void moveInBothDirectionsPreservesOtherRowsRelativeOrder() {
        ownedWorkout();
        WorkoutExercise moving = ownedRow();
        moving.setOrderIndex(2);
        WorkoutExercise first = row(2L, 0);
        WorkoutExercise second = row(3L, 1);
        when(rows.findByLoggedWorkoutId(100L)).thenReturn(List.of(second, moving, first));

        service.moveExerciseInWorkout(1L, 10L, 0);
        assertEquals(0, moving.getOrderIndex());
        assertEquals(1, first.getOrderIndex());
        assertEquals(2, second.getOrderIndex());

        service.moveExerciseInWorkout(1L, 10L, 2);
        assertEquals(2, moving.getOrderIndex());
        assertEquals(0, first.getOrderIndex());
        assertEquals(1, second.getOrderIndex());
    }

    @Test
    void moveToSamePositionDoesNotWriteAndInvalidPositionsAreRejected() {
        ownedWorkout();
        WorkoutExercise moving = ownedRow();
        moving.setOrderIndex(0);
        when(rows.findByLoggedWorkoutId(100L)).thenReturn(List.of(moving));
        service.moveExerciseInWorkout(1L, 10L, 0);
        assertThrows(RuntimeException.class, () -> service.moveExerciseInWorkout(1L, 10L, -1));
        assertThrows(RuntimeException.class, () -> service.moveExerciseInWorkout(1L, 10L, 1));
        verify(rows, never()).save(any());
        assertEquals(0, moving.getOrderIndex());
    }

    @Test
    void notesCanBeUpdatedAndClearedWithoutChangingRestTime() {
        ownedWorkout();
        WorkoutExercise row = ownedRow();
        row.setRestTimeSeconds(90);
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertSame(row, service.updateExerciseNotes(1L, 10L, "Brace"));
        assertEquals("Brace", row.getNotes());
        service.updateExerciseNotes(1L, 10L, "");
        assertEquals("", row.getNotes());
        service.updateExerciseNotes(1L, 10L, null);
        assertNull(row.getNotes());
        assertEquals(90, row.getRestTimeSeconds());
    }

    @Test
    void restTimeRejectsNegativeValuesBeforeMutationAndAllowsZero() {
        ownedWorkout();
        WorkoutExercise row = ownedRow();
        row.setRestTimeSeconds(90);
        assertThrows(RuntimeException.class, () -> service.updateExerciseRestTime(1L, 10L, -1));
        assertEquals(90, row.getRestTimeSeconds());
        verify(rows, never()).save(any());
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertSame(row, service.updateExerciseRestTime(1L, 10L, 0));
        assertEquals(0, row.getRestTimeSeconds());
    }

    @Test
    void allEditsRejectAnotherUsersWorkoutWithoutWrites() {
        LoggedWorkout workout = ownedWorkout();
        workout.setUserId(2L);
        ownedRow();
        assertEditsRejected();
    }

    @Test
    void allEditsRejectCompletedWorkoutWithoutWrites() {
        LoggedWorkout workout = ownedWorkout();
        workout.setEndedAtEpochMillis(1000L);
        ownedRow();
        assertEditsRejected();
    }

    @Test
    void missingChildAndInvalidIdsDoNotWrite() {
        assertThrows(RuntimeException.class, () -> service.updateExerciseNotes(1L, 10L, "x"));
        for (long invalid : new long[]{0L, -1L}) {
            assertThrows(RuntimeException.class, () -> service.removeExerciseFromWorkout(1L, invalid));
            assertThrows(RuntimeException.class, () -> service.moveExerciseInWorkout(invalid, 10L, 0));
            assertThrows(RuntimeException.class, () -> service.updateExerciseRestTime(invalid, 10L, 0));
            assertThrows(RuntimeException.class, () -> service.addExerciseToWorkout(1L, invalid, 9L));
        }
        verifyNoInteractions(workouts, sets, exercises);
        verify(rows, never()).save(any());
        verify(rows, never()).delete(anyLong());
    }

    private void assertEditsRejected() {
        assertThrows(RuntimeException.class, () -> service.updateWorkoutDetails(1L, 100L, "Legs", "Note"));
        assertThrows(RuntimeException.class, () -> service.updateExerciseDetails(1L, 10L, "Note", 90));
        assertThrows(RuntimeException.class, () -> service.renameWorkout(1L, 100L, "Legs"));
        assertThrows(RuntimeException.class, () -> service.updateWorkoutNotes(1L, 100L, "note"));
        assertThrows(RuntimeException.class, () -> service.discardWorkout(1L, 100L));
        assertThrows(RuntimeException.class, () -> service.addExerciseToWorkout(1L, 100L, 9L));
        assertThrows(RuntimeException.class, () -> service.removeExerciseFromWorkout(1L, 10L));
        assertThrows(RuntimeException.class, () -> service.moveExerciseInWorkout(1L, 10L, 0));
        assertThrows(RuntimeException.class, () -> service.updateExerciseNotes(1L, 10L, "x"));
        assertThrows(RuntimeException.class, () -> service.updateExerciseRestTime(1L, 10L, 60));
        verify(rows, never()).save(any());
        verify(rows, never()).delete(anyLong());
        verify(workouts, never()).save(any());
        verify(workouts, never()).delete(anyLong());
        verifyNoInteractions(sets, exercises);
    }

    private LoggedWorkout ownedWorkout() {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(100L);
        workout.setUserId(1L);
        when(workouts.findById(100L)).thenReturn(Optional.of(workout));
        return workout;
    }

    private WorkoutExercise ownedRow() {
        WorkoutExercise row = row(10L, 1);
        when(rows.findById(10L)).thenReturn(Optional.of(row));
        return row;
    }

    private WorkoutExercise row(long id, int index) {
        WorkoutExercise row = new WorkoutExercise();
        row.setId(id);
        row.setLoggedWorkoutId(100L);
        row.setOrderIndex(index);
        return row;
    }
}
