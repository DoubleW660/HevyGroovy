package com.example.hevygroovy.service;

import static com.example.hevygroovy.entity.enums.SetType.DROP;
import static com.example.hevygroovy.entity.enums.SetType.NORMAL;
import static com.example.hevygroovy.entity.enums.Unit.KG;
import static com.example.hevygroovy.entity.enums.Unit.LB;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Queue;

@ExtendWith(MockitoExtension.class)
public class SetEntryServiceImplTest {

    private SetEntryRepository setEntryRepository;
    private WorkoutExerciseRepository workoutExerciseRepository;
    private LoggedWorkoutRepository loggedWorkoutRepository;

    private SetEntryServiceImpl setEntryService;

    @BeforeEach
    void setUp() {
        setEntryRepository = mock(SetEntryRepository.class);
        workoutExerciseRepository = mock(WorkoutExerciseRepository.class);
        loggedWorkoutRepository = mock(LoggedWorkoutRepository.class);

        setEntryService = new SetEntryServiceImpl(
                setEntryRepository,
                workoutExerciseRepository,
                loggedWorkoutRepository
        );
    }

    void addSet_validWorkoutExercise_createsFirstSet() {
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;


        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(setEntryRepository.save(any(SetEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SetEntry result = setEntryService.addSet(userId, workoutExerciseId);

        Assertions.assertEquals(1, result.getSetNumber());
        Assertions.assertEquals(workoutExerciseId, result.getWorkoutExerciseId());
        Assertions.assertEquals(NORMAL, result.getSetType());
        Assertions.assertEquals(LB, result.getUnit());

        verify(setEntryRepository).save(any(SetEntry.class));
    }
    void addSet_existingSets_addsSetToEnd(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;


        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        SetEntry setEntry = new SetEntry();
        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setSetNumber(1);
        setEntry.setId(99L);

        when(setEntryRepository.findByWorkoutExerciseId(workoutExerciseId))
                .thenReturn(List.of(setEntry));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(setEntryRepository.save(any(SetEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SetEntry result = setEntryService.addSet(userId, workoutExerciseId);

        Assertions.assertEquals(2, result.getSetNumber());
        Assertions.assertEquals(workoutExerciseId, result.getWorkoutExerciseId());
        Assertions.assertEquals(NORMAL, result.getSetType());
        Assertions.assertEquals(LB, result.getUnit());

        verify(setEntryRepository).save(any(SetEntry.class));
    }
    void addSet_invalidWorkoutExerciseId_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 0;
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.addSet(userId, workoutExerciseId)
        );

        Assertions.assertEquals(
                "Invalid Workout Exercise Id Provided",
                exception.getMessage()
        );
    }

    void addSet_workoutExerciseNotFound_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.addSet(userId, workoutExerciseId)
        );

        Assertions.assertEquals(
                "Workout Exercise Not Found",
                exception.getMessage()
        );
    }
    void addSet_workoutDoesNotBelongToUser_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setUserId(2L);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.addSet(userId, workoutExerciseId)
        );

        Assertions.assertEquals(
                "Workout does not belong to user",
                exception.getMessage()
        );
    }
    void addSet_finishedWorkout_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setEndedAtEpochMillis(1005L);
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.addSet(userId, workoutExerciseId)
        );

        Assertions.assertEquals(
                "This workout has been completed",
                exception.getMessage()
        );
    }

    void updateSet_validSet_updatesFields(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setSetType(NORMAL);
        setEntry.setRpe(2F);
        setEntry.setWeight(150);
        setEntry.setSetNumber(1);
        setEntry.setUnit(KG);
        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setCreatedAtEpochMillis(1000L);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        when(setEntryRepository.save(any(SetEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setReps(10);
        request.setRpe(9F);
        request.setSetType(DROP);
        request.setWeight(200D);
        request.setRestSeconds(90);
        request.setUnit(LB);

        SetEntry result = setEntryService.updateSet(userId, setEntryId, request);

        Assertions.assertEquals(10, result.getReps());
        Assertions.assertEquals(9F, result.getRpe());
        Assertions.assertEquals(DROP, result.getSetType());
        Assertions.assertEquals(200D, result.getWeight());
        Assertions.assertEquals(90, result.getRestSeconds());
        Assertions.assertEquals(LB, result.getUnit());
    }
    void updateSet_setNotFound_throwsException(){

    }
    void updateSet_setDoesNotBelongToUser_throwsException(){

    }
    void updateSet_finishedWorkout_throwsException(){

    }

    void deleteSet_middleSet_reordersFollowingSets(){

    }
    void deleteSet_lastSet_doesNotReorderSets(){

    }
    void deleteSet_setDoesNotBelongToUser_throwsException(){

    }
    void deleteSet_finishedWorkout_throwsException(){

    }
}
