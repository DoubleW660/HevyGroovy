package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.PersonalRecord;
import java.util.List;

/** User-scoped saved record reads on the worker, delivered on main. */
public interface RecordGateway {
    interface Callback {
        void onSuccess(List<PersonalRecord> records);
        void onFailure(Exception error);
    }
    void loadExercise(long exerciseId, Callback callback);
}
