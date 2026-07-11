package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.ExerciseRepository;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.util.List;
import java.util.Optional;

public class SetEntryServiceImpl implements SetEntryService{
    private final SetEntryRepository setEntryRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final LoggedWorkoutRepository loggedWorkoutRepository;

    public SetEntryServiceImpl(
            SetEntryRepository setEntryRepository,
            WorkoutExerciseRepository workoutExerciseRepository,
            LoggedWorkoutRepository loggedWorkoutRepository
    ) {
        this.setEntryRepository = setEntryRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.loggedWorkoutRepository = loggedWorkoutRepository;
    }


    @Override
    public SetEntry addSet(long userId, long workoutExerciseId){

        //Check the passed variables are not invalid Ids <= 0
        if (workoutExerciseId <= 0) {
            throw new RuntimeException("Invalid Workout Exercise Id Provided");
        }

        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id Provided");
        }

        // use workoutExerciseId to load workoutExercise or throw
        WorkoutExercise workoutExercise = workoutExerciseRepository
                .findById(workoutExerciseId)
                .orElseThrow(() ->
                        new RuntimeException("Workout Exercise Not Found"));

        // Get loggedWorkoutId from workoutExercise
        long loggedWorkoutId = workoutExercise.getLoggedWorkoutId();

        // use loggedWorkoutId to load LoggedWorkout or throw
        LoggedWorkout workout = loggedWorkoutRepository
                .findById(loggedWorkoutId)
                .orElseThrow(() ->
                        new RuntimeException("Logged Workout Not Found"));

        //Check if userId matches logged workout owner
        if (userId != workout.getUserId()) {
            throw new RuntimeException("Workout does not belong to user");
        }

        //Check if workout has an end time
        if (workout.getEndedAtEpochMillis() != null) {
            throw new RuntimeException("Cannot add set to finished workout");
        }

        //
        List<SetEntry> existingSets =
                setEntryRepository.findByWorkoutExerciseId(workoutExerciseId);

        SetEntry setEntry = new SetEntry();

        setEntry.setWorkoutExerciseId(workoutExerciseId);
        setEntry.setSetNumber(existingSets.size() + 1);
        setEntry.setSetType(SetType.NORMAL);
        setEntry.setUnit(Unit.LB);
        setEntry.setCreatedAtEpochMillis(System.currentTimeMillis());

        return setEntryRepository.save(setEntry);
    }

    public SetEntry updateSet(long userId, long setEntryId, UpdateSetEntryRequest request){
        //Check the passed variables are not invalid Ids <= 0
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id Provided");
        }

        if (setEntryId <= 0) {
            throw new RuntimeException("Invalid Set Entry Id Provided");
        }

        SetEntry setEntry = setEntryRepository
                .findById(setEntryId)
                .orElseThrow(() ->
                        new RuntimeException("Set Entry Not Found"));

        long workoutExerciseId = setEntry.getWorkoutExerciseId();

        WorkoutExercise workoutExercise = workoutExerciseRepository
                .findById(workoutExerciseId)
                .orElseThrow(() ->
                        new RuntimeException("Workout Exercise Not Found"));

        long loggedWorkoutId = workoutExercise.getLoggedWorkoutId();

        LoggedWorkout loggedWorkout = loggedWorkoutRepository
                .findById(loggedWorkoutId)
                .orElseThrow(() ->
                        new RuntimeException("Logged Workout Not Found"));

        if (userId != loggedWorkout.getUserId()) {
            throw new RuntimeException("Logged Workout does not belong to user");
        }

        requireActiveWorkout(loggedWorkout);

        if (request.getReps() != null){
            if (request.getReps() < 0) {
                throw new RuntimeException("Reps cannot be negative");
            }

            setEntry.setReps(request.getReps());
        }
        if (request.getWeight() != null){
            if (request.getWeight() < 0) {
                throw new RuntimeException("Weight cannot be negative");
            }

            setEntry.setWeight(request.getWeight());
        }
        if (request.getUnit() != null){

            setEntry.setUnit(request.getUnit());
        }
        if (request.getRpe() != null){
            if (request.getRpe() < 0 || request.getRpe() > 10){
                throw new RuntimeException("RPE must be between 0 and 10");
            }

            setEntry.setRpe(request.getRpe());
        }

        if (request.getSetType() != null){

            setEntry.setSetType(request.getSetType());
        }

        if (request.getRestSeconds() != null){
            if (request.getRestSeconds() < 0) {
                throw new RuntimeException("Rest seconds cannot be negative");
            }

            setEntry.setRestSeconds(request.getRestSeconds());
        }

        return setEntryRepository.save(setEntry);
    }

    public SetEntry deleteSet(long userId, long setEntryId){
        //Check the passed variables are not invalid Ids <= 0
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id Provided");
        }

        if (setEntryId <= 0) {
            throw new RuntimeException("Invalid Set Entry Id Provided");
        }


    }


    private SetEntry requireOwnedActiveSet(long userId,  long setEntryId) {
        //Check the passed variables are not invalid Ids <= 0
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id Provided");
        }

        if (setEntryId <= 0) {
            throw new RuntimeException("Invalid Set Entry Id Provided");
        }

        SetEntry setEntry = setEntryRepository
                .findById(setEntryId)
                .orElseThrow(() ->
                        new RuntimeException("Set Entry Not Found"));

        long workoutExerciseId = setEntry.getWorkoutExerciseId();

        WorkoutExercise workoutExercise = workoutExerciseRepository
                .findById(workoutExerciseId)
                .orElseThrow(() ->
                        new RuntimeException("Workout Exercise Not Found"));

        long loggedWorkoutId = workoutExercise.getLoggedWorkoutId();

        LoggedWorkout loggedWorkout =
                requireOwnedWorkout(userId, loggedWorkoutId);

        requireActiveWorkout(loggedWorkout);

        return setEntry;
    }

    private LoggedWorkout requireOwnedWorkout(long userId,  long loggedWorkoutId) {
        if (loggedWorkoutId <= 0) {
            throw new RuntimeException("Invalid Id Provided");
        }
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }

        LoggedWorkout loggedWorkout = loggedWorkoutRepository
                .findById(loggedWorkoutId)
                .orElseThrow(() ->
                        new RuntimeException("Logged Workout Not Found"));

        if (userId != loggedWorkout.getUserId() ){
            throw new RuntimeException("Workout does not belong to user");
        }

        return loggedWorkout;
    }

    private void requireActiveWorkout(LoggedWorkout workout) {
        if (workout.getEndedAtEpochMillis() != null) {
            throw new RuntimeException("This workout has been completed");
        }
    }
}
