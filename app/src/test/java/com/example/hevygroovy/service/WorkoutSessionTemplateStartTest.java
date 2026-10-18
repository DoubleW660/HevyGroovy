package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.dto.ActiveSetEntry;
import com.example.hevygroovy.repo.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutSessionTemplateStartTest {
    @Mock private LoggedWorkoutRepository workouts;
    @Mock private TemplateWorkoutRepository templates;
    @Mock private TemplateExerciseRepository templateExercises;
    @Mock private WorkoutExerciseRepository workoutExercises;
    @Mock private SetEntryRepository sets;
    @Mock private ExerciseService exercises;

    private WorkoutSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkoutSessionServiceImpl(workouts, templates, templateExercises,
                workoutExercises, sets, exercises);
    }

    @Test
    void startSnapshotsWorkoutAndExercisesInTemplateOrder() {
        stubTemplate();
        TemplateExercise later = templateExercise(20L, 5, 120);
        TemplateExercise earlier = templateExercise(10L, 2, 60);
        // Repository results may be immutable and must not be sorted in place.
        List<TemplateExercise> rows = List.of(later, earlier);
        when(templateExercises.findByTemplateWorkoutId(7L)).thenReturn(rows);
        when(workoutExercises.findLastNotesForUserExercises(1L, List.of(10L, 20L)))
                .thenReturn(Map.of(10L, "Keep elbows tucked", 20L, "Pause at top"));
        Exercise bench = stubExercise(10L, "Bench press");
        stubExercise(20L, "Row");
        stubSave();

        long before = System.currentTimeMillis();
        LoggedWorkout result = service.startWorkoutFromTemplate(1L, 7L);
        long after = System.currentTimeMillis();

        assertEquals(100L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(Long.valueOf(7L), result.getTemplateId());
        assertEquals("Upper body", result.getNameSnapshot());
        assertNull(result.getEndedAtEpochMillis());
        assertTrue(result.getStartedAtEpochMillis() >= before);
        assertTrue(result.getStartedAtEpochMillis() <= after);
        verify(workouts).save(result);
        ArgumentCaptor<WorkoutExercise> captured = ArgumentCaptor.forClass(WorkoutExercise.class);
        verify(workoutExercises, times(2)).save(captured.capture());
        List<WorkoutExercise> saved = captured.getAllValues();
        assertExercise(saved.get(0), 10L, "Bench press", 2, 60, "Keep elbows tucked");
        assertExercise(saved.get(1), 20L, "Row", 5, 120, "Pause at top");
        assertSame(later, rows.get(0));
        bench.setTitle("Renamed bench");
        assertEquals("Bench press", saved.get(0).getExerciseNameSnapshot());
        verify(workoutExercises).findLastNotesForUserExercises(1L, List.of(10L, 20L));
        ArgumentCaptor<SetEntry> seeded = ArgumentCaptor.forClass(SetEntry.class);
        verify(sets, times(2)).save(seeded.capture());
        assertTrue(seeded.getAllValues().stream().allMatch(set -> !set.isCompleted()
                && set.getSetNumber() == 1 && set.getUnit() == Unit.LB
                && set.getSetType() == SetType.NORMAL && set.getWeight() == 0 && set.getReps() == 0));
    }

    @Test
    void restoresCompletedPerformanceByOccurrenceAndUsesPreferredUnitOnlyForBlankFallback() {
        stubTemplate();
        when(templateExercises.findByTemplateWorkoutId(7L)).thenReturn(List.of(
                templateExercise(10L, 0, 60), templateExercise(20L, 1, 90),
                templateExercise(10L, 2, 60)));
        stubExercise(10L, "Bench");
        stubExercise(20L, "New exercise");
        stubSave();
        SetEntry warmup = previousSet(1, SetType.WARMUP, 30, 10, Unit.KG);
        warmup.setRpe(6F); warmup.setRestSeconds(60);
        SetEntry failure = previousSet(3, SetType.FAILURE, 135, 0, Unit.LB);
        SetEntry drop = previousSet(2, SetType.DROP, 40, 8, Unit.KG);
        when(workoutExercises.findLastCompletedSetsForTemplateExercises(1L, 7L, List.of(10L, 20L, 10L)))
                .thenReturn(Map.of(10L, List.of(List.of(new ActiveSetEntry(warmup), new ActiveSetEntry(failure)),
                        List.of(new ActiveSetEntry(drop)))));

        long before = System.currentTimeMillis();
        service.startWorkoutFromTemplate(1L, 7L, Unit.KG);

        ArgumentCaptor<SetEntry> seeded = ArgumentCaptor.forClass(SetEntry.class);
        verify(sets, times(4)).save(seeded.capture());
        List<SetEntry> result = seeded.getAllValues();
        assertSeed(result.get(0), 1, SetType.WARMUP, 30, 10, Unit.KG);
        assertSeed(result.get(1), 2, SetType.FAILURE, 135, 0, Unit.LB);
        assertSeed(result.get(2), 1, SetType.NORMAL, 0, 0, Unit.KG);
        assertSeed(result.get(3), 1, SetType.DROP, 40, 8, Unit.KG);
        assertEquals(result.get(0).getWorkoutExerciseId(), result.get(1).getWorkoutExerciseId());
        assertNotEquals(result.get(0).getWorkoutExerciseId(), result.get(3).getWorkoutExerciseId());
        for (SetEntry set : result) {
            assertFalse(set.isCompleted()); assertEquals(0, set.getId());
            assertTrue(set.getCreatedAtEpochMillis() >= before);
            assertNull(set.getRpe()); assertNull(set.getRestSeconds());
        }
        verify(workoutExercises, never()).findPreviousPerformancesForUserExercises(anyLong(), anyList());
        assertTrue(warmup.isCompleted());
    }

    @Test
    void missingDuplicateOccurrenceGetsOneBlankSetWithoutReusingEarlierOccurrence() {
        stubTemplate();
        when(templateExercises.findByTemplateWorkoutId(7L)).thenReturn(List.of(
                templateExercise(10L, 0, 60), templateExercise(10L, 1, 60)));
        stubExercise(10L, "Bench"); stubSave();
        when(workoutExercises.findLastCompletedSetsForTemplateExercises(1L, 7L, List.of(10L, 10L)))
                .thenReturn(Map.of(10L, List.of(List.of(), List.of(new ActiveSetEntry(
                        previousSet(1, SetType.NORMAL, 50, 5, Unit.KG))))));
        service.startWorkoutFromTemplate(1L, 7L, Unit.LB);
        ArgumentCaptor<SetEntry> seeded = ArgumentCaptor.forClass(SetEntry.class);
        verify(sets, times(2)).save(seeded.capture());
        assertSeed(seeded.getAllValues().get(0), 1, SetType.NORMAL, 0, 0, Unit.LB);
        assertSeed(seeded.getAllValues().get(1), 1, SetType.NORMAL, 50, 5, Unit.KG);
    }

    @Test
    void missingDefaultUnitIsRejectedBeforeAccessingRepositories() {
        assertThrows(IllegalArgumentException.class, () -> service.startWorkoutFromTemplate(1L, 7L, null));
        verifyNoInteractions(workouts, templates, templateExercises, workoutExercises, sets, exercises);
    }

    @Test
    void startPreservesMissingNullAndBlankNotes() {
        stubTemplate();
        when(templateExercises.findByTemplateWorkoutId(7L)).thenReturn(List.of(
                templateExercise(10L, 0, 60), templateExercise(20L, 1, 60),
                templateExercise(30L, 2, 60)));
        Map<Long, String> notes = new HashMap<>();
        notes.put(20L, null);
        notes.put(30L, "");
        when(workoutExercises.findLastNotesForUserExercises(1L, List.of(10L, 20L, 30L)))
                .thenReturn(notes);
        stubExercise(10L, "Bench");
        stubExercise(20L, "Row");
        stubExercise(30L, "Press");
        stubSave();

        service.startWorkoutFromTemplate(1L, 7L);

        ArgumentCaptor<WorkoutExercise> captured = ArgumentCaptor.forClass(WorkoutExercise.class);
        verify(workoutExercises, times(3)).save(captured.capture());
        assertNull(captured.getAllValues().get(0).getNotes());
        assertNull(captured.getAllValues().get(1).getNotes());
        assertEquals("", captured.getAllValues().get(2).getNotes());
    }

    @Test
    void emptyTemplateStartsButCannotFinishUntilASetExists() {
        stubTemplate();
        when(templateExercises.findByTemplateWorkoutId(7L)).thenReturn(List.of());
        when(workoutExercises.findLastNotesForUserExercises(1L, List.of())).thenReturn(Map.of());
        stubSave();

        LoggedWorkout result = service.startWorkoutFromTemplate(1L, 7L);
        when(workouts.findById(100L)).thenReturn(Optional.of(result));
        when(sets.existsByLoggedWorkoutId(100L)).thenReturn(false, true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.finishWorkout(1L, 100L));
        assertEquals("Cannot finish an empty workout", exception.getMessage());
        assertNull(result.getEndedAtEpochMillis());
        verify(workouts, times(1)).save(any());
        verify(workoutExercises, never()).save(any());
        verifyNoInteractions(exercises);

        assertSame(result, service.finishWorkout(1L, 100L));
        assertNotNull(result.getEndedAtEpochMillis());
        verify(workouts, times(2)).save(result);
    }

    @Test
    void invalidUserIdsAreRejectedBeforeRepositoryAccess() {
        for (long userId : new long[]{0L, -1L}) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> service.startWorkoutFromTemplate(userId, 7L));
            assertEquals("Invalid User Id", exception.getMessage());
        }
        verifyNoInteractions(workouts, templates, templateExercises, workoutExercises, sets, exercises);
    }

    @Test
    void invalidTemplateIdsAreRejectedBeforeRepositoryAccess() {
        for (long templateId : new long[]{0L, -1L}) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> service.startWorkoutFromTemplate(1L, templateId));
            assertEquals("Invalid Template Id", exception.getMessage());
        }
        verifyNoInteractions(workouts, templates, templateExercises, workoutExercises, sets, exercises);
    }

    @Test
    void activeWorkoutBlocksStartBeforeLoadingTemplate() {
        when(workouts.findActiveWorkoutByUserId(1L)).thenReturn(Optional.of(new LoggedWorkout()));
        assertStartRejected("User already has an active workout");
        verifyNoInteractions(templates);
    }

    @Test
    void missingTemplateIsRejectedWithoutWrites() {
        when(templates.findById(7L)).thenReturn(Optional.empty());
        assertStartRejected("Workout Template Not Found");
    }

    @Test
    void anotherUsersTemplateIsRejectedWithoutWrites() {
        TemplateWorkout template = stubTemplate();
        template.setUserId(2L);
        assertStartRejected("You are not the owner of this template.");
    }

    @Test
    void archivedTemplateIsRejectedWithoutWrites() {
        TemplateWorkout template = stubTemplate();
        template.setArchived(true);
        assertStartRejected("Archived templates cannot be used to start a workout.");
    }

    private void assertStartRejected(String message) {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.startWorkoutFromTemplate(1L, 7L));
        assertEquals(message, exception.getMessage());
        verify(workouts, never()).save(any());
        verifyNoInteractions(templateExercises, workoutExercises, sets, exercises);
    }

    private TemplateWorkout stubTemplate() {
        TemplateWorkout template = new TemplateWorkout();
        template.setId(7L);
        template.setUserId(1L);
        template.setTitle("Upper body");
        when(templates.findById(7L)).thenReturn(Optional.of(template));
        return template;
    }

    private void stubSave() {
        when(workouts.save(any())).thenAnswer(invocation -> {
            LoggedWorkout workout = invocation.getArgument(0);
            workout.setId(100L);
            return workout;
        });
        java.util.concurrent.atomic.AtomicLong nextId = new java.util.concurrent.atomic.AtomicLong(200);
        lenient().when(workoutExercises.save(any())).thenAnswer(invocation -> {
            WorkoutExercise row = invocation.getArgument(0);
            row.setId(nextId.getAndIncrement());
            return row;
        });
    }

    private SetEntry previousSet(int number, SetType type, double weight, int reps, Unit unit) {
        SetEntry set = new SetEntry(); set.setId(999); set.setWorkoutExerciseId(888);
        set.setSetNumber(number); set.setSetType(type); set.setWeight(weight); set.setReps(reps);
        set.setUnit(unit); set.setCompleted(true); set.setCreatedAtEpochMillis(1);
        return set;
    }

    private void assertSeed(SetEntry set, int number, SetType type, double weight, int reps, Unit unit) {
        assertEquals(number, set.getSetNumber()); assertEquals(type, set.getSetType());
        assertEquals(weight, set.getWeight()); assertEquals(reps, set.getReps()); assertEquals(unit, set.getUnit());
        assertTrue(set.getWorkoutExerciseId() > 0);
    }

    private TemplateExercise templateExercise(long exerciseId, int order, int rest) {
        TemplateExercise row = new TemplateExercise();
        row.setTemplateWorkoutId(7L);
        row.setExerciseId(exerciseId);
        row.setOrderIndex(order);
        row.setRestTimeSeconds(rest);
        return row;
    }

    private Exercise stubExercise(long id, String title) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setTitle(title);
        when(exercises.requireExercise(id)).thenReturn(exercise);
        return exercise;
    }

    private void assertExercise(WorkoutExercise row, long exerciseId, String name,
                                int order, int rest, String notes) {
        assertEquals(100L, row.getLoggedWorkoutId());
        assertEquals(exerciseId, row.getExerciseId());
        assertEquals(name, row.getExerciseNameSnapshot());
        assertEquals(order, row.getOrderIndex());
        assertEquals(rest, row.getRestTimeSeconds());
        assertEquals(notes, row.getNotes());
    }
}
