package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.entity.LoggedWorkout;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.WorkoutExercise;
import com.example.hevygroovy.entity.enums.RecordType;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;
import com.example.hevygroovy.repo.LoggedWorkoutRepository;
import com.example.hevygroovy.repo.PersonalRecordRepository;
import com.example.hevygroovy.repo.SetEntryRepository;
import com.example.hevygroovy.repo.WorkoutExerciseRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Record values come from persisted performances; active achievements are provisional. */
public final class PersonalRecordServiceImpl implements PersonalRecordService {
    private static final double EPSILON = 0.000001;
    private final PersonalRecordRepository records;
    private final LoggedWorkoutRepository workouts;
    private final WorkoutExerciseRepository exercises;
    private final SetEntryRepository sets;

    public PersonalRecordServiceImpl(PersonalRecordRepository records, LoggedWorkoutRepository workouts,
                                     WorkoutExerciseRepository exercises, SetEntryRepository sets) {
        this.records = records;
        this.workouts = workouts;
        this.exercises = exercises;
        this.sets = sets;
    }

    @Override
    public List<PersonalRecord> getExerciseRecords(long userId, long exerciseId) {
        requirePositive(userId, "user");
        requirePositive(exerciseId, "exercise");
        return List.copyOf(records.findBestRecords(userId, List.of(exerciseId), null, 0));
    }

    @Override
    public List<PersonalRecord> getWorkoutRecords(long userId, long workoutId) {
        requirePositive(userId, "user");
        requirePositive(workoutId, "workout");
        LoggedWorkout workout = workouts.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Workout Not Found"));
        if (workout.getUserId() != userId) {
            throw new IllegalArgumentException("Workout does not belong to user");
        }
        List<WorkoutExercise> rows = new ArrayList<>(exercises.findByLoggedWorkoutId(workoutId));
        rows.sort(Comparator.comparingInt(WorkoutExercise::getOrderIndex)
                .thenComparingLong(WorkoutExercise::getId));
        if (rows.isEmpty()) return List.of();
        Map<Long, List<SetEntry>> setsByRow = sets.findByLoggedWorkoutId(workoutId).stream()
                .filter(set -> set.isCompleted() && set.getReps() > 0
                        && set.getSetType() != null && set.getSetType() != SetType.WARMUP)
                .collect(Collectors.groupingBy(SetEntry::getWorkoutExerciseId));
        Map<Long, EnumMap<RecordType, PersonalRecord>> candidates = new LinkedHashMap<>();
        Map<Long, Double> volumes = new HashMap<>();
        Map<Long, Integer> totalReps = new HashMap<>();
        Map<Long, WorkoutExercise> firstRows = new HashMap<>();
        for (WorkoutExercise row : rows) {
            List<SetEntry> rowSets = new ArrayList<>(setsByRow.getOrDefault(row.getId(), List.of()));
            rowSets.sort(Comparator.comparingInt(SetEntry::getSetNumber).thenComparingLong(SetEntry::getId));
            for (SetEntry set : rowSets) {
                if (set.getUnit() == null || !Double.isFinite(set.getWeight()) || set.getWeight() < 0) {
                    throw new IllegalStateException("Record sets require a valid weight and unit");
                }
                double kg = set.getWeight() * (set.getUnit() == Unit.LB ? 0.45359237 : 1);
                double volume = kg * set.getReps();
                if (!Double.isFinite(volume)) throw new IllegalStateException("Record volume must be finite");
                firstRows.putIfAbsent(row.getExerciseId(), row);
                volumes.merge(row.getExerciseId(), volume, Double::sum);
                totalReps.merge(row.getExerciseId(), set.getReps(), Math::addExact);
                long achieved = workout.getEndedAtEpochMillis() == null
                        ? Math.max(workout.getStartedAtEpochMillis(), set.getCreatedAtEpochMillis())
                        : workout.getEndedAtEpochMillis();
                if (kg > 0) add(candidates, record(RecordType.HEAVIEST_WEIGHT, workoutId, row, set, kg, achieved));
                add(candidates, record(RecordType.MOST_REPS, workoutId, row, set, set.getReps(), achieved));
                if (volume > 0) add(candidates, record(RecordType.BEST_SET_VOLUME, workoutId, row, set, volume, achieved));
            }
        }
        for (Map.Entry<Long, Double> volume : volumes.entrySet()) {
            if (volume.getValue() <= 0) continue;
            if (!Double.isFinite(volume.getValue())) throw new IllegalStateException("Record volume must be finite");
            WorkoutExercise first = firstRows.get(volume.getKey());
            add(candidates, new PersonalRecord(RecordType.WORKOUT_VOLUME, first.getExerciseId(),
                    first.getExerciseNameSnapshot(), workoutId, first.getId(), 0, volume.getValue(),
                    0, Unit.KG, totalReps.get(volume.getKey()), workout.getEndedAtEpochMillis() == null
                    ? workout.getStartedAtEpochMillis() : workout.getEndedAtEpochMillis()));
        }
        if (candidates.isEmpty()) return List.of();
        List<Long> exerciseIds = new ArrayList<>(candidates.keySet());
        Map<Long, EnumMap<RecordType, PersonalRecord>> baseline = new HashMap<>();
        for (PersonalRecord record : records.findBestRecords(userId, exerciseIds,
                workout.getEndedAtEpochMillis(), workoutId)) {
            add(baseline, record);
        }
        List<PersonalRecord> awards = new ArrayList<>();
        for (Map.Entry<Long, EnumMap<RecordType, PersonalRecord>> entry : candidates.entrySet()) {
            Map<RecordType, PersonalRecord> previous = baseline.getOrDefault(entry.getKey(), new EnumMap<>(RecordType.class));
            for (PersonalRecord candidate : entry.getValue().values()) {
                PersonalRecord prior = previous.get(candidate.getType());
                if (prior == null || candidate.getValue() > prior.getValue() + EPSILON) awards.add(candidate);
            }
        }
        return List.copyOf(awards);
    }

    private PersonalRecord record(RecordType type, long workoutId, WorkoutExercise row,
                                   SetEntry set, double value, long achieved) {
        return new PersonalRecord(type, row.getExerciseId(), row.getExerciseNameSnapshot(), workoutId,
                row.getId(), set.getId(), value, set.getWeight(), set.getUnit(), set.getReps(), achieved);
    }

    private static void add(Map<Long, EnumMap<RecordType, PersonalRecord>> target, PersonalRecord record) {
        EnumMap<RecordType, PersonalRecord> values = target.computeIfAbsent(record.getExerciseId(),
                ignored -> new EnumMap<>(RecordType.class));
        PersonalRecord previous = values.get(record.getType());
        if (previous == null || record.getValue() > previous.getValue()) values.put(record.getType(), record);
    }

    private static void requirePositive(long id, String label) {
        if (id <= 0) throw new IllegalArgumentException("Invalid " + label + " ID");
    }
}
