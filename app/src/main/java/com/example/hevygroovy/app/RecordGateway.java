package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.PersonalRecord;
import java.util.List;

/** User-scoped saved record reads on the worker, delivered on main. */
public interface RecordGateway {
    final class Snapshot {
        private final List<PersonalRecord> records;
        private final double estimatedOneRepMaxKg;

        public Snapshot(List<PersonalRecord> records, double estimatedOneRepMaxKg) {
            this.records = List.copyOf(records);
            this.estimatedOneRepMaxKg = estimatedOneRepMaxKg;
        }

        public List<PersonalRecord> getRecords() { return records; }
        public double getEstimatedOneRepMaxKg() { return estimatedOneRepMaxKg; }
    }

    interface Callback {
        void onSuccess(Snapshot snapshot);
        void onFailure(Exception error);
    }
    void loadExercise(long exerciseId, Callback callback);
}
