package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.PersonalRecord;
import com.example.hevygroovy.dto.ExercisePerformance;
import java.util.List;

public interface PersonalRecordService {
    /** Current all-time records from finished workouts, never active drafts. */
    List<PersonalRecord> getExerciseRecords(long userId, long exerciseId);

    /** Best all-time Epley estimate in KG; this is separate from the four record categories. */
    double getEstimatedOneRepMax(long userId, long exerciseId);

    /** Finished exercise performances across every routine, newest first. */
    List<ExercisePerformance> getExerciseHistory(long userId, long exerciseId);

    /**
     * Best categories established or improved in this workout. Active results are provisional;
     * finished results compare only earlier completed workouts, so historical awards persist.
     */
    List<PersonalRecord> getWorkoutRecords(long userId, long workoutId);
}
