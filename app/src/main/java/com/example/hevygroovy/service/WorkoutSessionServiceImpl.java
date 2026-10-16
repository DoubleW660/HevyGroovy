package com.example.hevygroovy.service;

import com.example.hevygroovy.entity.Exercise;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.TemplateExercise;
import com.example.hevygroovy.entity.TemplateWorkout;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.model.ExerciseModel;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.TemplateExerciseRepository;
import com.example.hevygroovy.repo.TemplateWorkoutRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import com.example.hevygroovy.dto.ActiveWorkoutDetail;
import com.example.hevygroovy.dto.ActiveWorkoutExercise;
import com.example.hevygroovy.dto.ActiveSetEntry;
import com.example.hevygroovy.dto.PreviousExercisePerformance;

public class WorkoutSessionServiceImpl implements WorkoutSessionService {

    private final LoggedWorkoutRepository loggedWorkoutRepository;
    private final TemplateWorkoutRepository templateWorkoutRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final SetEntryRepository setEntryRepository;
    private final ExerciseService exerciseService;

    public WorkoutSessionServiceImpl(
            LoggedWorkoutRepository loggedWorkoutRepository,
            TemplateWorkoutRepository templateWorkoutRepository,
            TemplateExerciseRepository templateExerciseRepository,
            WorkoutExerciseRepository workoutExerciseRepository,
            SetEntryRepository setEntryRepository,
            ExerciseService exerciseService
    ) {
        this.loggedWorkoutRepository = loggedWorkoutRepository;
        this.templateWorkoutRepository = templateWorkoutRepository;
        this.templateExerciseRepository = templateExerciseRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.setEntryRepository = setEntryRepository;
        this.exerciseService = exerciseService;
    }

    @Override
    public Optional<ActiveWorkoutDetail> getActiveWorkout(long userId) {
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }
        Optional<LoggedWorkout> active = loggedWorkoutRepository.findActiveWorkoutByUserId(userId);
        if (!active.isPresent()) { return Optional.empty(); }
        LoggedWorkout workout = active.get();
        if (workout.getUserId() != userId) {
            throw new RuntimeException("Workout does not belong to user");
        }
        requireActiveWorkout(workout);
        List<WorkoutExercise> rows = orderedWorkoutExercises(workout.getId());
        Map<Long, List<SetEntry>> setsByExercise = setEntryRepository.findByLoggedWorkoutId(workout.getId())
                .stream().collect(Collectors.groupingBy(SetEntry::getWorkoutExerciseId));
        List<Long> exerciseIds = rows.stream().map(WorkoutExercise::getExerciseId).distinct()
                .collect(Collectors.toList());
        Map<Long, PreviousExercisePerformance> previous = exerciseIds.isEmpty() ? Map.of()
                : workoutExerciseRepository.findPreviousPerformancesForUserExercises(userId, exerciseIds);
        Map<Long, Integer> occurrences = new HashMap<>();
        List<ActiveWorkoutExercise> result = new ArrayList<>();
        for (WorkoutExercise row : rows) {
            List<SetEntry> sets = new ArrayList<>(setsByExercise.getOrDefault(row.getId(), List.of()));
            sets.sort(Comparator.comparingInt(SetEntry::getSetNumber).thenComparingLong(SetEntry::getId));
            int occurrence = occurrences.merge(row.getExerciseId(), 1, Integer::sum) - 1;
            PreviousExercisePerformance performance = previous.get(row.getExerciseId());
            List<ActiveSetEntry> previousSets = performance == null
                    || occurrence >= performance.getSetsByOccurrence().size() ? List.of()
                    : performance.getSetsByOccurrence().get(occurrence);
            result.add(new ActiveWorkoutExercise(row,
                    sets.stream().map(ActiveSetEntry::new).collect(Collectors.toList()), previousSets,
                    performance == null ? null : performance.getWorkoutId(),
                    performance == null ? null : performance.getEndedAtEpochMillis()));
        }
        return Optional.of(new ActiveWorkoutDetail(workout, result));
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

        TemplateWorkout templateWorkout = templateWorkoutRepository
                .findById(templateId)
                .orElseThrow(() ->
                        new RuntimeException("Workout Template Not Found"));

        if (templateWorkout.getUserId() != userId) {
            throw new RuntimeException("You are not the owner of this template.");
        }

        if (templateWorkout.isArchived()) {
            throw new RuntimeException(
                    "Archived templates cannot be used to start a workout.");
        }

        List<TemplateExercise> templateExerciseList =
                new ArrayList<>(templateExerciseRepository.findByTemplateWorkoutId(templateId));

        templateExerciseList.sort(
                Comparator.comparingInt(TemplateExercise::getOrderIndex));

        List<Long> exerciseIds = new ArrayList<>();

        for (TemplateExercise current : templateExerciseList) {
            exerciseIds.add(current.getExerciseId());
        }

        Map<Long, String> lastNotesByExerciseId =
                workoutExerciseRepository.findLastNotesForUserExercises(
                        userId,
                        exerciseIds);

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
            workoutExercise.setRestTimeSeconds(current.getRestTimeSeconds() == null
                    ? 0 : current.getRestTimeSeconds());

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

    @Override
    public WorkoutExercise addExerciseToWorkout(long userId, long workoutId, long exerciseId) {
        LoggedWorkout workout = requireOwnedWorkout(userId, workoutId);
        requireActiveWorkout(workout);
        if (exerciseId <= 0) {
            throw new RuntimeException("Invalid Exercise Id");
        }
        ExerciseModel exercise = exerciseService.requireActiveExercise(exerciseId);
        int nextIndex = workoutExerciseRepository.findMaxOrderIndex(workoutId) + 1;
        Map<Long, String> notes = workoutExerciseRepository.findLastNotesForUserExercises(
                userId, java.util.Collections.singletonList(exerciseId));
        WorkoutExercise row = new WorkoutExercise();
        row.setLoggedWorkoutId(workoutId);
        row.setExerciseId(exerciseId);
        row.setExerciseNameSnapshot(exercise.getTitle());
        row.setOrderIndex(nextIndex);
        row.setRestTimeSeconds(0);
        row.setNotes(notes.get(exerciseId));
        return workoutExerciseRepository.save(row);
    }

    @Override
    public void removeExerciseFromWorkout(long userId, long workoutExerciseId) {
        WorkoutExercise row = requireOwnedActiveExercise(userId, workoutExerciseId);
        // Persistence must eventually wrap deletion and reindexing in one transaction.
        for (SetEntry set :
                setEntryRepository.findByWorkoutExerciseId(workoutExerciseId)) {
            setEntryRepository.delete(set.getId());
        }
        workoutExerciseRepository.delete(workoutExerciseId);
        List<WorkoutExercise> remaining = orderedWorkoutExercises(row.getLoggedWorkoutId());
        remaining.removeIf(current -> current.getId() == workoutExerciseId);
        saveOrder(remaining);
    }

    @Override
    public void moveExerciseInWorkout(long userId, long workoutExerciseId, int orderIndex) {
        WorkoutExercise row = requireOwnedActiveExercise(userId, workoutExerciseId);
        List<WorkoutExercise> rows = orderedWorkoutExercises(row.getLoggedWorkoutId());
        if (orderIndex < 0 || orderIndex >= rows.size()) {
            throw new RuntimeException("Position is not available");
        }
        int currentPosition = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == workoutExerciseId) {
                currentPosition = i;
                break;
            }
        }
        if (currentPosition < 0) {
            throw new RuntimeException("Workout Exercise Not Found");
        }
        if (currentPosition == orderIndex) {
            return;
        }
        WorkoutExercise moving = rows.remove(currentPosition);
        rows.add(orderIndex, moving);
        saveOrder(rows);
    }

    @Override
    public WorkoutExercise updateExerciseNotes(long userId, long workoutExerciseId, String notes) {
        WorkoutExercise row = requireOwnedActiveExercise(userId, workoutExerciseId);
        row.setNotes(notes);
        return workoutExerciseRepository.save(row);
    }

    @Override
    public WorkoutExercise updateExerciseRestTime(long userId, long workoutExerciseId,
                                                   int restTimeSeconds) {
        WorkoutExercise row = requireOwnedActiveExercise(userId, workoutExerciseId);
        if (restTimeSeconds < 0) {
            throw new RuntimeException("Rest seconds cannot be negative");
        }
        row.setRestTimeSeconds(restTimeSeconds);
        return workoutExerciseRepository.save(row);
    }

    private WorkoutExercise requireOwnedActiveExercise(long userId, long workoutExerciseId) {
        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }
        if (workoutExerciseId <= 0) {
            throw new RuntimeException("Invalid Workout Exercise Id Provided");
        }
        WorkoutExercise row = workoutExerciseRepository.findById(workoutExerciseId)
                .orElseThrow(() -> new RuntimeException("Workout Exercise Not Found"));
        LoggedWorkout workout = requireOwnedWorkout(userId, row.getLoggedWorkoutId());
        requireActiveWorkout(workout);
        return row;
    }

    private List<WorkoutExercise> orderedWorkoutExercises(long workoutId) {
        List<WorkoutExercise> rows = new ArrayList<>(
                workoutExerciseRepository.findByLoggedWorkoutId(workoutId));
        rows.sort(Comparator.comparingInt(WorkoutExercise::getOrderIndex).thenComparingLong(WorkoutExercise::getId));
        return rows;
    }

    private void saveOrder(List<WorkoutExercise> rows) {
        for (int i = 0; i < rows.size(); i++) {
            WorkoutExercise row = rows.get(i);
            if (row.getOrderIndex() != i) {
                row.setOrderIndex(i);
                workoutExerciseRepository.save(row);
            }
        }
    }

    @Override
    public LoggedWorkout updateWorkoutDetails(long userId, long workoutId, String name, String notes) {
        LoggedWorkout workout = requireOwnedWorkout(userId, workoutId);
        requireActiveWorkout(workout);
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Workout name is required");
        }
        workout.setNameSnapshot(name.trim().replaceAll("\\s+", " "));
        workout.setNotes(notes);
        return loggedWorkoutRepository.save(workout);
    }

    @Override
    public WorkoutExercise updateExerciseDetails(long userId, long workoutExerciseId, String notes, int restSeconds) {
        WorkoutExercise exercise = requireOwnedActiveExercise(userId, workoutExerciseId);
        if (restSeconds < 0) {
            throw new RuntimeException("Rest seconds cannot be negative");
        }
        exercise.setNotes(notes);
        exercise.setRestTimeSeconds(restSeconds);
        return workoutExerciseRepository.save(exercise);
    }

    @Override
    public LoggedWorkout renameWorkout(long userId, long workoutId, String name) {
        LoggedWorkout workout = requireOwnedWorkout(userId, workoutId);
        requireActiveWorkout(workout);
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Workout name is required");
        }
        workout.setNameSnapshot(name.trim().replaceAll("\\s+", " "));
        return loggedWorkoutRepository.save(workout);
    }

    @Override
    public LoggedWorkout updateWorkoutNotes(long userId, long workoutId, String notes) {
        LoggedWorkout workout = requireOwnedWorkout(userId, workoutId);
        requireActiveWorkout(workout);
        workout.setNotes(notes);
        return loggedWorkoutRepository.save(workout);
    }

    @Override
    public void discardWorkout(long userId, long workoutId) {
        LoggedWorkout workout = requireOwnedWorkout(userId, workoutId);
        requireActiveWorkout(workout);
        // The SQLite service wrapper makes all child and parent deletes atomic.
        for (SetEntry set : setEntryRepository.findByLoggedWorkoutId(workoutId)) {
            setEntryRepository.delete(set.getId());
        }
        for (WorkoutExercise row : workoutExerciseRepository.findByLoggedWorkoutId(workoutId)) {
            workoutExerciseRepository.delete(row.getId());
        }
        loggedWorkoutRepository.delete(workoutId);
    }

    private void requireNoActiveWorkout(long userId) {

        if (loggedWorkoutRepository
                .findActiveWorkoutByUserId(userId)
                .isPresent()) {

            throw new RuntimeException(
                    "User already has an active workout");
        }
    }

    private LoggedWorkout requireOwnedWorkout(
            long userId,
            long workoutId
    ) {
        if (workoutId <= 0) {
            throw new RuntimeException("Invalid Id Provided");
        }

        if (userId <= 0) {
            throw new RuntimeException("Invalid User Id");
        }

        LoggedWorkout workout = loggedWorkoutRepository
                .findById(workoutId)
                .orElseThrow(() ->
                        new RuntimeException("Workout Not Found"));

        if (userId != workout.getUserId()) {
            throw new RuntimeException(
                    "Workout does not belong to user");
        }

        return workout;
    }

    private void requireActiveWorkout(LoggedWorkout workout) {

        if (workout.getEndedAtEpochMillis() != null) {
            throw new RuntimeException(
                    "This workout has been completed");
        }
    }

    private void requireWorkoutNotEmpty(long workoutId) {

        if (!setEntryRepository.existsByLoggedWorkoutId(workoutId)) {
            throw new RuntimeException(
                    "Cannot finish an empty workout");
        }
    }

    private LoggedWorkout createActiveWorkout(
            long userId,
            Long templateId,
            String nameSnapshot
    ) {
        LoggedWorkout workout = new LoggedWorkout();

        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(System.currentTimeMillis());
        workout.setEndedAtEpochMillis(null);
        workout.setTemplateId(templateId);
        workout.setNameSnapshot(nameSnapshot);

        return loggedWorkoutRepository.save(workout);
    }
}
