package com.example.hevygroovy.dto;

import com.example.hevygroovy.entity.SetEntry;
import com.example.hevygroovy.entity.enums.SetType;
import com.example.hevygroovy.entity.enums.Unit;

/** Read-only copy so screen state cannot mutate a repository entity. */
public final class ActiveSetEntry {
    private final long id;
    private final int setNumber;
    private final int reps;
    private final double weight;
    private final Unit unit;
    private final Integer restSeconds;
    private final Float rpe;
    private final SetType setType;
    private final boolean completed;
    private final long createdAtEpochMillis;

    public ActiveSetEntry(SetEntry set) {
        id = set.getId();
        setNumber = set.getSetNumber();
        reps = set.getReps();
        weight = set.getWeight();
        unit = set.getUnit();
        restSeconds = set.getRestSeconds();
        rpe = set.getRpe();
        setType = set.getSetType();
        completed = set.isCompleted();
        createdAtEpochMillis = set.getCreatedAtEpochMillis();
    }

    public long getId() { return id; }
    public int getSetNumber() { return setNumber; }
    public int getReps() { return reps; }
    public double getWeight() { return weight; }
    public Unit getUnit() { return unit; }
    public Integer getRestSeconds() { return restSeconds; }
    public Float getRpe() { return rpe; }
    public SetType getSetType() { return setType; }
    public boolean isCompleted() { return completed; }
    public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
}
