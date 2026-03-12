package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.repo.ExerciseRepository;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.util.List;
import java.util.Optional;

public class WorkoutSessionServiceImpl implements WorkoutSessionService{

    private final LoggedWorkoutRepository loggedWorkoutRepository;
    private final TemplateWorkoutRepository templateWorkoutRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final ExerciseRepository exerciseRepository;

    private final TemplateService templateService;

    public WorkoutSessionServiceImpl(LoggedWorkoutRepository loggedWorkoutRepository){
        this.loggedWorkoutRepository = loggedWorkoutRepository;
        this.templateWorkoutRepository = templateWorkoutRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.templateService = templateService;

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

        if (userId <= 0){
            throw new RuntimeException("Invalid User Id");
        }

        if (templateId <= 0){
            throw new RuntimeException("Invalid Template Id");
        }

        requireNoActiveWorkout(userId);

        Optional<TemplateWorkout> optionalTemplateWorkout = templateWorkoutRepository.findById(templateId);

        if (optionalTemplateWorkout.isEmpty()){
            throw new RuntimeException("Workout Template Not Found");
        }

        TemplateWorkout templateWorkout = optionalTemplateWorkout.get();

        if (templateWorkout.getUserId() != userId){
            throw new RuntimeException("You are not the owner of this template.");
        }


        LoggedWorkout loggedWorkout = startEmptyWorkout(userId);
        List<TemplateExercise> templateExerciseList = templateExerciseRepository.findByTemplateWorkoutId(templateId);

        for (TemplateExercise current : templateExerciseList) {
            WorkoutExercise workoutExercise = new WorkoutExercise();
            Optional<Exercise> exercise = exerciseRepository.findById(current.getExerciseId());

            current.getExerciseId()

            workoutExercise.setLoggedWorkoutId(loggedWorkout.getId());
            workoutExercise.setExerciseId(current.getExerciseId());
            workoutExercise.setExerciseNameSnapshot(exercise.getTitle());

        }






        return loggedWorkout;
    }

    @Override
    public LoggedWorkout finishWorkout(long userId, long workoutId) {
        return null;
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

    private LoggedWorkout requireWorkoutNotEmpty(long workoutId){
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
        if(workoutId < 0){
            throw new RuntimeException("Workout Id is Invalid")
        }
        Optional<LoggedWorkout> optionalLoggedWorkout = loggedWorkoutRepository.findById(workoutId);

        if(optionalLoggedWorkout.isEmpty()){
            throw new RuntimeException("No workout found with provided Id");
        }

        LoggedWorkout loggedWorkout = optionalLoggedWorkout.get();

        if(loggedWorkout.getEndedAtEpochMillis() != null){
            throw new RuntimeException("This workout has been completed");
        }
    }
}
