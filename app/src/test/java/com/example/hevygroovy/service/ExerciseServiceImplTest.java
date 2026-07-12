package com.example.hevygroovy.service;
import com.example.hevygroovy.entity.enums.MuscleGroup;

import com.example.hevygroovy.dto.CreateExerciseRequest;
import com.example.hevygroovy.dto.UpdateExerciseRequest;
import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.model.ExerciseModel;
import com.example.hevygroovy.repo.ExerciseRepository;
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
class ExerciseServiceImplTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    private ExerciseServiceImpl exerciseService;

    @BeforeEach
    void setUp() {
        exerciseService = new ExerciseServiceImpl(exerciseRepository);
    }

    @Test
    void createExercise_validRequest_createsExercise() {
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle("  Bench   Press  ");
        request.setDescription("  Chest exercise  ");

        when(exerciseRepository.save(any(Exercise.class)))
                .thenAnswer(invocation -> {
                    Exercise exercise = invocation.getArgument(0);
                    exercise.setId(1L);
                    return exercise;
                });

        ExerciseModel result = exerciseService.createExercise(request);

        assertEquals(1L, result.getExerciseId());
        assertEquals("Bench Press", result.getTitle());
        assertEquals("Chest exercise", result.getDescription());
        assertFalse(result.isArchived());

        verify(exerciseRepository).save(any(Exercise.class));
    }

    @Test
    void createExercise_nullRequest_throwsException() {
        assertThrows(
                RuntimeException.class,
                () -> exerciseService.createExercise(null)
        );

        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void createExercise_blankTitle_throwsException() {
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle("   ");

        assertThrows(
                RuntimeException.class,
                () -> exerciseService.createExercise(request)
        );

        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void updateExercise_validRequest_updatesExercise() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Bench");
        exercise.setDescription("Old description");

        UpdateExerciseRequest request = new UpdateExerciseRequest();
        request.setTitle("  Incline   Bench ");
        request.setDescription("  New description  ");

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        when(exerciseRepository.save(exercise))
                .thenReturn(exercise);

        ExerciseModel result =
                exerciseService.updateExercise(exerciseId, request);

        assertEquals("Incline Bench", result.getTitle());
        assertEquals("New description", result.getDescription());

        verify(exerciseRepository).save(exercise);
    }

    @Test
    void getExercise_exerciseExists_returnsExercise() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Bench Press");

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        ExerciseModel result = exerciseService.getExercise(exerciseId);

        assertEquals(exerciseId, result.getExerciseId());
        assertEquals("Bench Press", result.getTitle());
    }

    @Test
    void getExercise_exerciseNotFound_throwsException() {
        long exerciseId = 1L;

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.empty());

        assertThrows(
                RuntimeException.class,
                () -> exerciseService.getExercise(exerciseId)
        );
    }

    @Test
    void archiveExercise_activeExercise_archivesExercise() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setArchived(false);

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        exerciseService.archiveExercise(exerciseId);

        assertTrue(exercise.isArchived());

        verify(exerciseRepository).save(exercise);
    }

    @Test
    void archiveExercise_alreadyArchived_throwsException() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setArchived(true);

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        assertThrows(
                RuntimeException.class,
                () -> exerciseService.archiveExercise(exerciseId)
        );

        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void restoreExercise_archivedExercise_restoresExercise() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setArchived(true);

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        exerciseService.restoreExercise(exerciseId);

        assertFalse(exercise.isArchived());

        verify(exerciseRepository).save(exercise);
    }

    @Test
    void requireActiveExercise_archivedExercise_throwsException() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setArchived(true);

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        assertThrows(
                RuntimeException.class,
                () -> exerciseService.requireActiveExercise(exerciseId)
        );
    }

    @Test
    void listExercises_noSearchText_returnsAllExercises() {
        Exercise bench = new Exercise();
        bench.setId(1L);
        bench.setTitle("Bench Press");

        Exercise squat = new Exercise();
        squat.setId(2L);
        squat.setTitle("Squat");

        when(exerciseRepository.findAll(false))
                .thenReturn(List.of(bench, squat));

        List<ExerciseModel> results =
                exerciseService.listExercises(null, false);

        assertEquals(2, results.size());

        verify(exerciseRepository).findAll(false);
        verify(exerciseRepository, never())
                .searchByTitle(anyString(), anyBoolean());
    }

    @Test
    void listExercises_searchText_searchesByTitle() {
        Exercise bench = new Exercise();
        bench.setId(1L);
        bench.setTitle("Bench Press");

        when(exerciseRepository.searchByTitle("Bench Press", false))
                .thenReturn(List.of(bench));

        List<ExerciseModel> results =
                exerciseService.listExercises("  Bench   Press  ", false);

        assertEquals(1, results.size());
        assertEquals("Bench Press", results.get(0).getTitle());

        verify(exerciseRepository)
                .searchByTitle("Bench Press", false);
    }

    @Test
    void requireExercise_invalidId_throwsException() {
        assertThrows(
                RuntimeException.class,
                () -> exerciseService.requireExercise(0)
        );

        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void createExercise_withMuscleGroups_savesMuscleGroups() {
        CreateExerciseRequest request = new CreateExerciseRequest();
        request.setTitle("Bench Press");
        request.setDescription("Chest exercise");
        request.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        request.setSecondaryMuscleGroups(
                List.of(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FRONT_DELTS
                )
        );

        when(exerciseRepository.save(any(Exercise.class)))
                .thenAnswer(invocation -> {
                    Exercise exercise = invocation.getArgument(0);
                    exercise.setId(1L);
                    return exercise;
                });

        ExerciseModel result = exerciseService.createExercise(request);

        assertEquals(MuscleGroup.CHEST, result.getPrimaryMuscleGroup());

        assertEquals(
                List.of(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FRONT_DELTS
                ),
                result.getSecondaryMuscleGroups()
        );

        verify(exerciseRepository).save(any(Exercise.class));
    }

    @Test
    void updateExercise_withMuscleGroups_updatesMuscleGroups() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Bench Press");
        exercise.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        exercise.setSecondaryMuscleGroups(
                List.of(MuscleGroup.TRICEPS)
        );

        UpdateExerciseRequest request = new UpdateExerciseRequest();
        request.setPrimaryMuscleGroup(MuscleGroup.FRONT_DELTS);
        request.setSecondaryMuscleGroups(
                List.of(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FRONT_DELTS
                )
        );

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        when(exerciseRepository.save(exercise))
                .thenReturn(exercise);

        ExerciseModel result =
                exerciseService.updateExercise(exerciseId, request);

        assertEquals(
                MuscleGroup.FRONT_DELTS,
                result.getPrimaryMuscleGroup()
        );

        assertEquals(
                List.of(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FRONT_DELTS
                ),
                result.getSecondaryMuscleGroups()
        );

        verify(exerciseRepository).save(exercise);
    }

    @Test
    void updateExercise_nullMuscleGroups_keepsExistingMuscleGroups() {
        long exerciseId = 1L;

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Bench Press");
        exercise.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        exercise.setSecondaryMuscleGroups(
                List.of(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FRONT_DELTS
                )
        );

        UpdateExerciseRequest request = new UpdateExerciseRequest();
        request.setTitle("Barbell Bench Press");

        when(exerciseRepository.findById(exerciseId))
                .thenReturn(Optional.of(exercise));

        when(exerciseRepository.save(exercise))
                .thenReturn(exercise);

        ExerciseModel result =
                exerciseService.updateExercise(exerciseId, request);

        assertEquals(
                MuscleGroup.CHEST,
                result.getPrimaryMuscleGroup()
        );

        assertEquals(
                List.of(
                        MuscleGroup.TRICEPS,
                        MuscleGroup.FRONT_DELTS
                ),
                result.getSecondaryMuscleGroups()
        );

        verify(exerciseRepository).save(exercise);
    }
}