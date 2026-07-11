package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.SetEntry;

public interface SetEntryService {

    SetEntry addSet(long userId, long workoutExerciseId);

    SetEntry updateSet(long userId, long setEntryId, UpdateSetEntryRequest request);

    void deleteSet(long userId, long setEntryId);
}
