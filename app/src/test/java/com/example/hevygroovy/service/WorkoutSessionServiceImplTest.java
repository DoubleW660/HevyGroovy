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

        SetEntry setEntry = new SetEntry();

        when(setEntryRepository.findByLoggedWorkoutId(workoutId))
                .thenReturn(List.of(setEntry));

        when(loggedWorkoutRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LoggedWorkout result =
                workoutSessionService.finishWorkout(userId, workoutId);

        assertNotNull(result.getEndedAtEpochMillis());
        verify(loggedWorkoutRepository).save(workout);
        verify(loggedWorkoutRepository).findById(workoutId);
        verify(setEntryRepository)
                .findByLoggedWorkoutId(workoutId);
    }

    @Test
    void finishWorkout_throwsWhenAlreadyFinished(){
        long userId = 1L;
        long workoutId = 100L;

        LoggedWorkout workout = new LoggedWorkout();
        workout.setId(workoutId);
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(1000L);
        workout.setEndedAtEpochMillis(1001L);

        workout.setEndedAtEpochMillis(System.currentTimeMillis());

        assertThrows(
                RuntimeException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId)
        );
    }
}
