package com.example.hevygroovy.app

/** Rest actions follow committed workout writes, independently of the screen lifecycle. */
interface WorkoutRestTimer {
    fun start(workoutId: Long, exerciseRowId: Long, setId: Long, exerciseName: String, seconds: Int)
    fun cancelForWorkout(workoutId: Long)
    fun reconcileWorkout(activeWorkoutId: Long?)

    object None : WorkoutRestTimer {
        override fun start(workoutId: Long, exerciseRowId: Long, setId: Long, exerciseName: String, seconds: Int) = Unit
        override fun cancelForWorkout(workoutId: Long) = Unit
        override fun reconcileWorkout(activeWorkoutId: Long?) = Unit
    }
}
