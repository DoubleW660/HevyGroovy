package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.repo.ExerciseRepository;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WorkoutSessionServiceImpl implements WorkoutSessionService {

    private final LoggedWorkoutRepository loggedWorkoutRepository;
    private final TemplateWorkoutRepository templateWorkoutRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final SetEntryRepository setEntryRepository;
    private final TemplateService templateService;
    private final ExerciseService exerciseService;


    public WorkoutSessionServiceImpl(
            LoggedWorkoutRepository loggedWorkoutRepository,
            TemplateWorkoutRepository templateWorkoutRepository,
            TemplateExerciseRepository templateExerciseRepository,
            WorkoutExerciseRepository workoutExerciseRepository,
            SetEntryRepository setEntryRepository,
            TemplateService templateService,
            ExerciseService exerciseService
    ) {
        this.loggedWorkoutRepository = loggedWorkoutRepository;
        this.templateWorkoutRepository = templateWorkoutRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.setEntryRepository = setEntryRepository;
        this.templateService = templateService;
        this.exerciseService = exerciseService;
    }

    @Override
    public LoggedWorkout startEmptyWorkout(long userId) {
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }

        requireNoActiveWorkout(userId);

        return createActiveWorkout(userId, null, null);
    }

    @Override
    public LoggedWorkout startWorkoutFromTemplate(long userId, long templateId) {
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }

        if (templateId <= 0) {
            throw new RuntimeException("Invalid Template Id");
        }

        requireNoActiveWorkout(userId);

        Optional<TemplateWorkout> optionalTemplateWorkout = templateWorkoutRepository.findById(templateId);
        if (optionalTemplateWorkout.isEmpty()) {
            throw new RuntimeException("Workout Template Not Found");
        }

        TemplateWorkout templateWorkout = optionalTemplateWorkout.get();

        if (templateWorkout.getUserId() != userId) {
            throw new RuntimeException("You are not the owner of this template.");
        }

        if (templateWorkout.isArchived()) {
            throw new RuntimeException("Archived templates cannot be used to start a workout.");
        }

        List<TemplateExercise> templateExerciseList =
                templateExerciseRepository.findByTemplateWorkoutId(templateId);

        templateExerciseList.sort(Comparator.comparingInt(TemplateExercise::getOrderIndex));

        List<Long> exerciseIds = new ArrayList<>();
        for (TemplateExercise current : templateExerciseList) {
            exerciseIds.add(current.getExerciseId());
        }

        Map<Long, String> lastNotesByExerciseId =
                workoutExerciseRepository.findLastNotesForUserExercises(userId, exerciseIds);

        LoggedWorkout loggedWorkout = createActiveWorkout(
                userId,
                templateWorkout.getId(),
                templateWorkout.getTitle()
        );

        for (TemplateExercise current : templateExerciseList) {
            long exerciseId = current.getExerciseId();
            Exercise exercise = exerciseService.requireExercise(exerciseId);

            WorkoutExercise workoutExercise = new WorkoutExercise();
            workoutExercise.setLoggedWorkoutId(loggedWorkout.getId());
            workoutExercise.setExerciseId(exerciseId);
            workoutExercise.setExerciseNameSnapshot(exercise.getTitle());
            workoutExercise.setOrderIndex(current.getOrderIndex());
            workoutExercise.setRestTimeSeconds(current.getRestTimeSeconds());

            String lastNote = lastNotesByExerciseId.get(exerciseId);
            if (lastNote != null) {
                workoutExercise.setNotes(lastNote);
            }

            workoutExerciseRepository.save(workoutExercise);
        }

        return loggedWorkout;
    }

    @Override
    public LoggedWorkout finishWorkout(long userId, long workoutId) {

        LoggedWorkout workout = requireOwnedWorkout(userId, workoutId);

        requireActiveWorkout(workout);

        requireWorkoutNotEmpty(workout.getId());

        workout.setEndedAtEpochMillis(System.currentTimeMillis());

        return loggedWorkoutRepository.save(workout);
    }

    private void requireNoActiveWorkout(long userId) {

        if (loggedWorkoutRepository.findActiveWorkoutByUserId(userId).isPresent()) {
            throw new RuntimeException("User already has an active workout");
        }
    }

    private LoggedWorkout requireOwnedWorkout(long userId,  long workoutId) {
        if (workoutId <= 0) {
            throw new RuntimeException("Invalid Id Provided");
        }
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }

        Optional<LoggedWorkout> loggedWorkoutOptional = loggedWorkoutRepository.findById(workoutId);

        if (loggedWorkoutOptional.isEmpty()) {
            throw new RuntimeException("Workout Not Found");
        }

        LoggedWorkout workout = loggedWorkoutOptional.get();

        if (userId != workout.getUserId() ){
            throw new RuntimeException("Workout does not belong to user");
        }

        return workout;
    }

    private void requireActiveWorkout(LoggedWorkout workout) {
        if (workout.getEndedAtEpochMillis() != null) {
            throw new RuntimeException("This workout has been completed");
        }
    }

    private void requireWorkoutNotEmpty(long workoutId){
        if (!setEntryRepository.existsByLoggedWorkoutId(workoutId)) {
            throw new RuntimeException("Cannot finish an empty workout");
        }
    }
    private LoggedWorkout createActiveWorkout(long userId, Long templateId, String nameSnapshot) {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(System.currentTimeMillis());
        workout.setEndedAtEpochMillis(null);
        workout.setTemplateId(templateId);
        workout.setNameSnapshot(nameSnapshot);

        return loggedWorkoutRepository.save(workout);
    }
}