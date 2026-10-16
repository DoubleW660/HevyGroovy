package com.example.hevygroovy.app;

import java.util.List;

final class ServiceRecordGateway implements RecordGateway {
    private final AppWorker worker;
    ServiceRecordGateway(AppWorker worker) { this.worker = worker; }

    @Override public void loadExercise(long exerciseId, Callback callback) {
        worker.submit(() -> List.copyOf(worker.services().getPersonalRecords()
                .getExerciseRecords(worker.userId(), exerciseId)), callback::onSuccess, callback::onFailure);
    }
}
