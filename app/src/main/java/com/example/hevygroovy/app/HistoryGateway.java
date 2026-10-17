package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.WorkoutHistory;
import com.example.hevygroovy.dto.WorkoutHistoryDetail;
import java.util.List;

/** User-scoped history reads on the database worker, with results delivered on main. */
public interface HistoryGateway {
    interface PageCallback {
        void onSuccess(HistoryPage page);
        void onFailure(Exception error);
    }

    interface DetailCallback {
        void onSuccess(WorkoutHistoryDetail detail);
        void onFailure(Exception error);
    }

    interface DeleteCallback {
        void onSuccess();
        void onFailure(Exception error);
    }

    final class HistoryPage {
        private final List<WorkoutHistory> items;
        private final boolean hasMore;
        private final int nextOffset;

        public HistoryPage(List<WorkoutHistory> items, boolean hasMore, int nextOffset) {
            this.items = List.copyOf(items);
            this.hasMore = hasMore;
            this.nextOffset = nextOffset;
        }

        public List<WorkoutHistory> getItems() { return items; }
        public boolean hasMore() { return hasMore; }
        public int getNextOffset() { return nextOffset; }
    }

    void loadPage(int offset, PageCallback callback);
    void loadDetail(long workoutId, DetailCallback callback);
    void deleteWorkout(long workoutId, DeleteCallback callback);
}
