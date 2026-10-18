package com.example.hevygroovy.app;

import com.example.hevygroovy.service.PersonalRecordService;
import com.example.hevygroovy.dto.CatalogExercise;

final class ServiceRecordGateway implements RecordGateway {
    private final AppWorker worker;
    ServiceRecordGateway(AppWorker worker) { this.worker = worker; }

    @Override public void loadExercise(long exerciseId, Callback callback) {
        worker.submit(() -> {
            PersonalRecordService records = worker.services().getPersonalRecords();
            long userId = worker.userId();
            return new Snapshot(records.getExerciseRecords(userId, exerciseId),
                    records.getEstimatedOneRepMax(userId, exerciseId),
                    new CatalogExercise(worker.services().getExercises().getExercise(exerciseId)),
                    records.getExerciseHistory(userId, exerciseId));
        }, callback::onSuccess, callback::onFailure);
    }
}
