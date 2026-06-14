package com.example.hevygroovy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.User;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutSessionServiceImplTest {

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

        LoggedWorkout result =
                workoutSessionService.finishWorkout(userId, workoutId);

        assertNotNull(result.getEndedAtEpochMillis());
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
    }
}
