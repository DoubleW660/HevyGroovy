package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.dto.CatalogExercise;
import com.example.hevygroovy.dto.ExercisePerformance;
import com.example.hevygroovy.dto.ExerciseRepRecord;
import java.util.List;

/** User-scoped saved record reads on the worker, delivered on main. */
public interface RecordGateway {
    final class Snapshot {
        private final List<PersonalRecord> records;
        private final double estimatedOneRepMaxKg;
        private final CatalogExercise exercise;
        private final List<ExercisePerformance> history;
        private final List<ExerciseRepRecord> repRecords;

        public Snapshot(List<PersonalRecord> records, double estimatedOneRepMaxKg) {
            this(records, estimatedOneRepMaxKg, null, List.of());
        }

        public Snapshot(List<PersonalRecord> records, double estimatedOneRepMaxKg,
                        CatalogExercise exercise, List<ExercisePerformance> history) {
            this.records = List.copyOf(records);
            this.estimatedOneRepMaxKg = estimatedOneRepMaxKg;
            this.exercise = exercise;
            this.history = List.copyOf(history);
            this.repRecords = ExerciseRepRecord.fromHistory(this.history);
        }

        public List<PersonalRecord> getRecords() { return records; }
        public double getEstimatedOneRepMaxKg() { return estimatedOneRepMaxKg; }
        public CatalogExercise getExercise() { return exercise; }
        public List<ExercisePerformance> getHistory() { return history; }
        public List<ExerciseRepRecord> getRepRecords() { return repRecords; }
    }

    interface Callback {
        void onSuccess(Snapshot snapshot);
        void onFailure(Exception error);
    }
    void loadExercise(long exerciseId, Callback callback);
}
