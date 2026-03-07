package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.repo.ExerciseRepository;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.util.Optional;

public class WorkoutSessionServiceImpl implements WorkoutSessionService{

    private final LoggedWorkoutRepository loggedWorkoutRepository;
    private final TemplateWorkoutRepository templateWorkoutRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final ExerciseRepository exerciseRepository;

    public WorkoutSessionServiceImpl(LoggedWorkoutRepository loggedWorkoutRepository){
        this.loggedWorkoutRepository = loggedWorkoutRepository;
        this.templateWorkoutRepository = templateWorkoutRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.exerciseRepository = exerciseRepository;

    }


    @Override
    public LoggedWorkout startEmptyWorkout(long userId) {
        requireNoActiveWorkout(userId);

        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(System.currentTimeMillis());
        workout.setEndedAtEpochMillis(null);

        return loggedWorkoutRepository.save(workout);
    }

    @Override
    public LoggedWorkout startWorkoutFromTemplate(long userId, long templateId) {
        requireNoActiveWorkout(userId);

        return null;
    }

    @Override
    public LoggedWorkout finishWorkout(long userId, long workoutId) {
        return null;
    }

    private validateUser(long userID){
        if(userID < 0){
            throw new RuntimeException("Invalid User ID");
        }
    }

    private void requireNoActiveWorkout(long userId) {
        if (loggedWorkoutRepository.findActiveWorkoutByUserId(userId).isPresent()) {
            throw new RuntimeException("User already has an active workout");
        }
    }

    private LoggedWorkout requireWorkout(long workoutId){
        if (workoutId <= 0){
            throw new RuntimeException("Invalid Id Provided");
        }

        Optional<LoggedWorkout> loggedWorkoutOptional = loggedWorkoutRepository.findById(workoutId);

        if(loggedWorkoutOptional.isEmpty()){
            throw new RuntimeException("Workout Not Found");
        }

        return loggedWorkoutOptional.get();
    }

    private void requireActiveWorkout(long workoutId) {
        if (loggedWorkoutRepository.findActiveWorkoutByUserId(userId).isPresent()) {
            throw new RuntimeException("User already has an active workout");
        }
    }
}
