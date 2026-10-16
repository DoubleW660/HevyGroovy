package com.example.hevygroovy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutSessionServiceImplTest {
    @Test
    void getActiveWorkoutReturnsEmptyWithoutLoadingChildren() {
        assertTrue(workoutSessionService.getActiveWorkout(1L).isEmpty());
        verifyNoInteractions(workoutExerciseRepository, setEntryRepository);
    }

    @Test
    void getActiveWorkoutRejectsInvalidUsersBeforeRepositoryAccess() {
        assertThrows(RuntimeException.class, () -> workoutSessionService.getActiveWorkout(0L));
        assertThrows(RuntimeException.class, () -> workoutSessionService.getActiveWorkout(-1L));
        verifyNoInteractions(loggedWorkoutRepository, workoutExerciseRepository, setEntryRepository);
    }

    @Test
    void getActiveWorkoutRejectsWrongOwnerAndCompletedResults() {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(2L);
        when(loggedWorkoutRepository.findActiveWorkoutByUserId(1L)).thenReturn(Optional.of(workout));
        assertThrows(RuntimeException.class, () -> workoutSessionService.getActiveWorkout(1L));
        workout.setUserId(1L);
        workout.setEndedAtEpochMillis(1000L);
        assertThrows(RuntimeException.class, () -> workoutSessionService.getActiveWorkout(1L));
        verifyNoInteractions(workoutExerciseRepository, setEntryRepository);
    }

    private WorkoutSessionServiceImpl workoutSessionService;
    private ExerciseService exerciseService;
    private LoggedWorkoutRepository loggedWorkoutRepository;
    private TemplateWorkoutRepository templateWorkoutRepository;
    private TemplateExerciseRepository templateExerciseRepository;
    private WorkoutExerciseRepository workoutExerciseRepository;
    private SetEntryRepository setEntryRepository;
    private TemplateServiceImpl templateService;


    @BeforeEach
    void setUp() {
        loggedWorkoutRepository = mock(LoggedWorkoutRepository.class);
        templateWorkoutRepository = mock(TemplateWorkoutRepository.class);
        templateExerciseRepository = mock(TemplateExerciseRepository.class);
        workoutExerciseRepository = mock(WorkoutExerciseRepository.class);
        setEntryRepository = mock(SetEntryRepository.class);

        exerciseService = mock(ExerciseService.class);

        templateService = new TemplateServiceImpl(
                templateWorkoutRepository,
                templateExerciseRepository,
                exerciseService
        );

        workoutSessionService = new WorkoutSessionServiceImpl(
                loggedWorkoutRepository,
                templateWorkoutRepository,
                templateExerciseRepository,
                workoutExerciseRepository,
                setEntryRepository,
                templateService,
                exerciseService
        );
    }

    @Test
    void finishWorkoutSuccess() {

        long userId = 1L;
        long workoutId = 100L;

        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(workoutId);
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(1000L);
        workout.setEndedAtEpochMillis(null);

        when(loggedWorkoutRepository.findById(workoutId))
                .thenReturn(Optional.of(workout));

        when(setEntryRepository.existsByLoggedWorkoutId(workoutId))
                .thenReturn(true);

        when(loggedWorkoutRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        long beforeFinish = System.currentTimeMillis();
        LoggedWorkout result =
                workoutSessionService.finishWorkout(userId, workoutId);
        long afterFinish = System.currentTimeMillis();

        assertSame(workout, result);
        assertNotNull(result.getEndedAtEpochMillis());
        assertTrue(result.getEndedAtEpochMillis() >= beforeFinish);
        assertTrue(result.getEndedAtEpochMillis() <= afterFinish);
        verify(loggedWorkoutRepository).save(workout);
        verify(loggedWorkoutRepository).findById(workoutId);
        verify(setEntryRepository)
                .existsByLoggedWorkoutId(workoutId);
    }

    @Test
    void finishWorkout_throwsWhenAlreadyFinished(){

        // Set test variables
        long userId = 1L;
        long workoutId = 100L;

        //build new test workout
        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(workoutId);
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(1000L);
        workout.setEndedAtEpochMillis(1001L);

        //Mock repository to return the completed workout
        when(loggedWorkoutRepository.findById(workoutId))
                .thenReturn(Optional.of(workout));


        //Attempt to finish an already completed workout
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId)
        );

        assertEquals(
                "This workout has been completed",
                exception.getMessage()
        );
        assertEquals(Long.valueOf(1001L), workout.getEndedAtEpochMillis());
        verifyNoInteractions(setEntryRepository);
        verify(loggedWorkoutRepository, never()).save(any());
    }

    @Test
    void workoutNotFound(){
        // Set test variables
        long userId = 1L;
        long workoutId = 100L;

        // Mock repository to return no workout
        when(loggedWorkoutRepository.findById(workoutId))
                .thenReturn(Optional.empty());

        // Attempt to finish a nonexistent workout
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId)
        );
        //confirms correct error is returned
        assertEquals(
                "Workout Not Found",
                exception.getMessage()
        );
        verifyNoInteractions(setEntryRepository);
        verify(loggedWorkoutRepository, never()).save(any());

    }

    @Test
    void finishWorkout_throwsWhenWrongOwner(){
        long ownerUserId = 1L;
        long requestingUserId = 2L;
        long workoutId = 100L;


        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(workoutId);
        workout.setUserId(ownerUserId);
        workout.setStartedAtEpochMillis(1000L);

        // Mock repository to return a workout owned by another user
        when(loggedWorkoutRepository.findById(workoutId))
                .thenReturn(Optional.of(workout));

        // Attempt to finish another user's workout
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> workoutSessionService.finishWorkout(requestingUserId, workoutId)
        );
        //confirms correct error is returned
        assertEquals(
                "Workout does not belong to user",
                exception.getMessage()
        );
        assertNull(workout.getEndedAtEpochMillis());
        verifyNoInteractions(setEntryRepository);
        verify(loggedWorkoutRepository, never()).save(any());

    }

    @Test
    void finishWorkout_throwsWhenWorkoutIsEmpty(){
        long userId = 1L;
        long workoutId = 100L;


        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(workoutId);
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(1000L);


        // Mock repository to return the active workout
        when(loggedWorkoutRepository.findById(workoutId))
                .thenReturn(Optional.of(workout));

        // Mock repository to report that the workout has no sets
        when(setEntryRepository.existsByLoggedWorkoutId(workoutId))
                .thenReturn(false);

        // Attempt to finish an empty workout
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId)
        );

        // Confirm the correct error is returned
        assertEquals(
                "Cannot finish an empty workout",
                exception.getMessage()
        );
        assertNull(workout.getEndedAtEpochMillis());
        verify(loggedWorkoutRepository, never()).save(any());
    }

    @Test
    void finishWorkout_rejectsInvalidUserIdsBeforeRepositoryAccess() {
        for (long userId : new long[]{0L, -1L}) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> workoutSessionService.finishWorkout(userId, 100L));
            assertEquals("Invalid User Id", exception.getMessage());
        }
        verifyNoInteractions(loggedWorkoutRepository, setEntryRepository);
    }

    @Test
    void finishWorkout_rejectsInvalidWorkoutIdsBeforeRepositoryAccess() {
        for (long workoutId : new long[]{0L, -1L}) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> workoutSessionService.finishWorkout(1L, workoutId));
            assertEquals("Invalid Id Provided", exception.getMessage());
        }
        verifyNoInteractions(loggedWorkoutRepository, setEntryRepository);
    }

    @Test
    void finishWorkout_rejectsSecondFinishWithoutChangingCompletionTime() {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(100L);
        workout.setUserId(1L);
        workout.setStartedAtEpochMillis(1000L);
        when(loggedWorkoutRepository.findById(100L)).thenReturn(Optional.of(workout));
        when(setEntryRepository.existsByLoggedWorkoutId(100L)).thenReturn(true);
        when(loggedWorkoutRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        workoutSessionService.finishWorkout(1L, 100L);
        Long originalCompletionTime = workout.getEndedAtEpochMillis();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> workoutSessionService.finishWorkout(1L, 100L));

        assertEquals("This workout has been completed", exception.getMessage());
        assertEquals(originalCompletionTime, workout.getEndedAtEpochMillis());
        verify(loggedWorkoutRepository, times(1)).save(workout);
        verify(setEntryRepository, times(1)).existsByLoggedWorkoutId(100L);
    }
}
