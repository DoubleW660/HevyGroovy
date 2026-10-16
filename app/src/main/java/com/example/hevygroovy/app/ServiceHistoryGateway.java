package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.WorkoutHistory;
import java.util.List;

/** Uses a one-row lookahead so a full last page does not require an empty-page request. */
final class ServiceHistoryGateway implements HistoryGateway {
    private static final int PAGE_SIZE = 20;
    private final AppWorker worker;

    ServiceHistoryGateway(AppWorker worker) { this.worker = worker; }

    @Override
    public void loadPage(int offset, PageCallback callback) {
        worker.submit(() -> {
            if (offset < 0) throw new IllegalArgumentException("Offset cannot be negative");
            List<WorkoutHistory> rows = worker.services().getHistory()
                    .getWorkoutHistory(worker.userId(), PAGE_SIZE + 1, offset);
            int count = Math.min(rows.size(), PAGE_SIZE);
            return new HistoryPage(rows.subList(0, count), rows.size() > PAGE_SIZE, Math.addExact(offset, count));
        }, callback::onSuccess, callback::onFailure);
    }

    @Override
    public void loadDetail(long workoutId, DetailCallback callback) {
        worker.submit(() -> worker.services().getHistory().getWorkoutHistoryDetail(worker.userId(), workoutId),
                callback::onSuccess, callback::onFailure);
    }
}
