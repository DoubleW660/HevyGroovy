package com.example.hevygroovy.service;

import static com.example.hevygroovy.entity.enums.SetType.DROP;
import static com.example.hevygroovy.entity.enums.SetType.NORMAL;
import static com.example.hevygroovy.entity.enums.Unit.KG;
import static com.example.hevygroovy.entity.enums.Unit.LB;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    void updateSet_setNotFound_throwsException(){
        long userId = 1L;
        long setEntryId = 50L;

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.empty());

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "Set Entry Not Found",
                exception.getMessage()
        );
    }

    @Test
    void updateSet_setDoesNotBelongToUser_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setUserId(2L);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setSetNumber(1);
        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setCreatedAtEpochMillis(1000L);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "Workout does not belong to user",
                exception.getMessage()
        );
    }

    @Test
    void updateSet_finishedWorkout_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setEndedAtEpochMillis(1004L);
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setSetNumber(1);
        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setCreatedAtEpochMillis(1000L);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "This workout has been completed",
                exception.getMessage()
        );
    }

    @Test
    void updateSet_negativeReps_throwsException() {
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setWorkoutExerciseId(workoutExerciseId);

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setReps(-1);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "Reps cannot be negative",
                exception.getMessage()
        );
    }

    @Test
    void updateSet_negativeWeight_throwsException() {
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setWorkoutExerciseId(workoutExerciseId);

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setWeight(-1D);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "Weight cannot be negative",
                exception.getMessage()
        );
    }

    @Test
    void updateSet_invalidRpe_throwsException() {
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setWorkoutExerciseId(workoutExerciseId);

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setRpe(11F);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "RPE must be between 0 and 10",
                exception.getMessage()
        );
    }

    @Test
    void updateSet_negativeRestSeconds_throwsException() {
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setWorkoutExerciseId(workoutExerciseId);

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        UpdateSetEntryRequest request = new UpdateSetEntryRequest();
        request.setRestSeconds(-1);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.updateSet(userId, setEntryId, request)
        );

        Assertions.assertEquals(
                "Rest seconds cannot be negative",
                exception.getMessage()
        );
    }

    @Test
    void deleteSet_middleSet_reordersFollowingSets(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;

        long setEntryId = 50L;
        long setEntryDeleteId = 60L;
        long setEntryId3 = 70L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);

        SetEntry setEntry1 = new SetEntry();
        setEntry1.setId(setEntryId);
        setEntry1.setWorkoutExerciseId(workoutExerciseId);
        setEntry1.setSetNumber(1);

        SetEntry setEntry2 = new SetEntry();
        setEntry2.setId(setEntryDeleteId);
        setEntry2.setWorkoutExerciseId(workoutExerciseId);
        setEntry2.setSetNumber(2);

        SetEntry setEntry3 = new SetEntry();
        setEntry3.setId(setEntryId3);
        setEntry3.setWorkoutExerciseId(workoutExerciseId);
        setEntry3.setSetNumber(3);

        when(setEntryRepository.findById(setEntryDeleteId))
                .thenReturn(Optional.of(setEntry2));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(setEntryRepository.findByWorkoutExerciseId(workoutExerciseId))
                .thenReturn(new ArrayList<>(List.of(setEntry1, setEntry3)));

        setEntryService.deleteSet(userId, setEntryDeleteId);

        Assertions.assertEquals(1, setEntry1.getSetNumber());
        Assertions.assertEquals(2, setEntry3.getSetNumber());

        verify(setEntryRepository).delete(setEntryDeleteId);
        verify(setEntryRepository).save(setEntry3);
    }

    @Test
    void deleteSet_lastSet_doesNotReorderSets(){

        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;

        long setEntryId = 50L;
        long setEntryId2 = 60L;
        long setEntryDeleteId = 70L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);

        SetEntry setEntry1 = new SetEntry();
        setEntry1.setId(setEntryId);
        setEntry1.setWorkoutExerciseId(workoutExerciseId);
        setEntry1.setSetNumber(1);

        SetEntry setEntry2 = new SetEntry();
        setEntry2.setId(setEntryId2);
        setEntry2.setWorkoutExerciseId(workoutExerciseId);
        setEntry2.setSetNumber(2);

        SetEntry setEntry3 = new SetEntry();
        setEntry3.setId(setEntryDeleteId);
        setEntry3.setWorkoutExerciseId(workoutExerciseId);
        setEntry3.setSetNumber(3);

        when(setEntryRepository.findById(setEntryDeleteId))
                .thenReturn(Optional.of(setEntry3));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(setEntryRepository.findByWorkoutExerciseId(workoutExerciseId))
                .thenReturn(new ArrayList<>(List.of(setEntry1, setEntry2)));

        setEntryService.deleteSet(userId, setEntryDeleteId);

        Assertions.assertEquals(1, setEntry1.getSetNumber());
        Assertions.assertEquals(2, setEntry2.getSetNumber());

        verify(setEntryRepository).delete(setEntryDeleteId);
        verify(setEntryRepository, never()).save(any(SetEntry.class));
    }

    @Test
    void deleteSet_setDoesNotBelongToUser_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setUserId(2L);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setSetNumber(1);
        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setCreatedAtEpochMillis(1000L);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.deleteSet(userId, setEntryId)
        );

        Assertions.assertEquals(
                "Workout does not belong to user",
                exception.getMessage()
        );

        verify(setEntryRepository, never()).delete(anyLong());
    }

    @Test
    void deleteSet_finishedWorkout_throwsException(){
        long userId = 1L;
        long workoutExerciseId = 10L;
        long loggedWorkoutId = 20L;
        long setEntryId = 50L;

        LoggedWorkout loggedWorkout = new LoggedWorkout();
        loggedWorkout.setStartedAtEpochMillis(1000L);
        loggedWorkout.setEndedAtEpochMillis(1004L);
        loggedWorkout.setUserId(userId);
        loggedWorkout.setId(loggedWorkoutId);

        WorkoutExercise current = new WorkoutExercise();
        current.setId(workoutExerciseId);
        current.setExerciseId(workoutExerciseId);
        current.setLoggedWorkoutId(loggedWorkoutId);
        current.setOrderIndex(1);

        SetEntry setEntry = new SetEntry();
        setEntry.setId(setEntryId);
        setEntry.setSetNumber(1);
        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setCreatedAtEpochMillis(1000L);

        when(loggedWorkoutRepository.findById(loggedWorkoutId))
                .thenReturn(Optional.of(loggedWorkout));

        when(workoutExerciseRepository.findById(workoutExerciseId))
                .thenReturn(Optional.of(current));

        when(setEntryRepository.findById(setEntryId))
                .thenReturn(Optional.of(setEntry));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> setEntryService.deleteSet(userId, setEntryId)
        );

        Assertions.assertEquals(
                "This workout has been completed",
                exception.getMessage()
        );

        verify(setEntryRepository, never()).delete(anyLong());
    }
}
