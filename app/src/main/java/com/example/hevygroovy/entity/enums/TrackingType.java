package com.example.hevygroovy.entity.enums;

/** The measurements entered for each set, independent of movement and muscle categories. */
public enum TrackingType {
    WEIGHT_REPS(true, true, false, false),
    REPS(false, true, false, false),
    TIME(false, false, true, false),
    DISTANCE(false, false, false, true),
    DISTANCE_TIME(false, false, true, true),
    WEIGHT_TIME(true, false, true, false);

    private final boolean weight, reps, duration, distance;
    TrackingType(boolean weight, boolean reps, boolean duration, boolean distance) {
        this.weight = weight; this.reps = reps; this.duration = duration; this.distance = distance;
    }
    public boolean tracksWeight() { return weight; }
    public boolean tracksReps() { return reps; }
    public boolean tracksDuration() { return duration; }
    public boolean tracksDistance() { return distance; }
}
