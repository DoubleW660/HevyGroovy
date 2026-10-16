package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.UpdateSetEntryRequest;
import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.Unit;

public interface SetEntryService {

    SetEntry addSet(long userId, long workoutExerciseId);

    /** Chooses the unit before saving the new set; existing sets keep their own units. */
    SetEntry addSet(long userId, long workoutExerciseId, Unit unit);

    SetEntry updateSet(long userId, long setEntryId, UpdateSetEntryRequest request);

    void deleteSet(long userId, long setEntryId);
}
