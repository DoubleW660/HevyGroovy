package com.example.hevygroovy.ui

import android.content.ContentValues
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.UserPreferences
import com.example.hevygroovy.dto.AddTemplateExerciseRequest
import com.example.hevygroovy.dto.CreateExerciseRequest
import com.example.hevygroovy.dto.CreateTemplateRequest
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.persistence.WorkoutDatabase
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Deliberately leaves demonstration data in the QA app for manual phone inspection.
 * Never runs against the regular app. Run this class after the ordinary test suite
 * when a consistent fixture is wanted for offline, rotation and process-restart checks.
 */
class PhoneFixtureSetupTest {
    @Test
    fun prepareManualPhoneFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Manual fixtures are restricted to the separate QA installation",
            context.packageName.endsWith(".qa"))

        val database = "hevy_groovy.db"
        WorkoutDatabase(context, database).use { helper ->
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                // Named tables and explicit QA-package guard prevent touching personal app data.
                listOf("set_entry", "workout_exercise", "logged_workout",
                    "template_exercise", "template_workout", "exercise").forEach { table ->
                    db.delete(table, null, null)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        assertTrue(context.getSharedPreferences("local_profile", Context.MODE_PRIVATE)
            .edit().putLong("user_id", 1L).commit())
        UserPreferences(context).setDefaultUnit(WeightUnit.LB)

        val finishedIds = mutableListOf<Long>()
        SQLiteAppStorage(context, database).use { storage ->
            val bench = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Bench Press"
                description = "Barbell bench press. Keep your feet planted and control the descent."
                primaryMuscleGroup = MuscleGroup.CHEST
                secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS)
            }).exerciseId
            val squat = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Squat"
                description = "Brace, sit between your hips, and drive through your whole foot."
                primaryMuscleGroup = MuscleGroup.QUADRICEPS
                secondaryMuscleGroups = listOf(MuscleGroup.GLUTES)
            }).exerciseId
            val pulldown = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Lat Pulldown"
                description = "Pull toward your upper chest with a controlled return."
                primaryMuscleGroup = MuscleGroup.LATS
                secondaryMuscleGroups = listOf(MuscleGroup.BICEPS)
            }).exerciseId

            listOf(
                Triple("Upper Body", "Push and pull, with a second round of each movement.",
                    listOf(bench, pulldown, bench, pulldown)),
                Triple("Full Body", "A balanced session built around the squat, press and pull.",
                    listOf(squat, bench, pulldown, squat)),
            ).forEach { (name, description, exercises) ->
                val template = storage.templates.createTemplate(1L, CreateTemplateRequest().apply {
                    title = name; this.description = description
                })
                exercises.forEach { exerciseId ->
                    storage.templates.addExerciseToTemplate(1L, AddTemplateExerciseRequest().apply {
                        templateId = template.templateWorkoutId
                        this.exerciseId = exerciseId
                        restTimeSeconds = 90
                    })
                }
            }

            repeat(30) { index ->
                val workout = storage.workoutSessions.startEmptyWorkout(1L)
                storage.workoutSessions.updateWorkoutDetails(1L, workout.id,
                    if (index % 2 == 0) "Upper Body" else "Full Body",
                    if (index % 3 == 0) "Strong session. Kept the last reps controlled." else "Steady progress, one session at a time.")
                listOf(bench, squat, pulldown).forEachIndexed { exerciseIndex, exerciseId ->
                    val row = storage.workoutSessions.addExerciseToWorkout(1L, workout.id, exerciseId)
                    storage.workoutSessions.updateExerciseDetails(1L, row.id,
                        if (exerciseIndex == 0) "Controlled reps" else "Keep the same range of motion", 90)
                    repeat(3) { setIndex ->
                        val set = storage.setEntries.addSet(1L, row.id)
                        storage.setEntries.updateSet(1L, set.id, UpdateSetEntryRequest().apply {
                            weight = listOf(135.0, 185.0, 90.0)[exerciseIndex] + (index / 10) * 5
                            reps = 10 - setIndex
                            unit = WeightUnit.LB
                            completed = true
                            rpe = (7 + setIndex).toFloat()
                            restSeconds = 90
                        })
                    }
                }
                storage.workoutSessions.finishWorkout(1L, workout.id)
                finishedIds += workout.id
            }

            val active = storage.workoutSessions.startEmptyWorkout(1L)
            storage.workoutSessions.updateWorkoutDetails(1L, active.id,
                "Phone recovery check", "Saved offline on this phone")
            val row = storage.workoutSessions.addExerciseToWorkout(1L, active.id, bench)
            storage.workoutSessions.updateExerciseDetails(1L, row.id, "Controlled reps", 90)
            storage.setEntries.addSet(1L, row.id).also { set ->
                storage.setEntries.updateSet(1L, set.id, UpdateSetEntryRequest().apply {
                    weight = 60.0; reps = 8; unit = WeightUnit.KG; completed = true
                })
            }
            storage.setEntries.addSet(1L, row.id).also { set ->
                storage.setEntries.updateSet(1L, set.id, UpdateSetEntryRequest().apply {
                    weight = 135.0; reps = 6; unit = WeightUnit.LB; completed = false
                })
            }
        }

        // Spread the log across actual days so the phone history is readable and paginated.
        val now = System.currentTimeMillis()
        WorkoutDatabase(context, database).use { helper ->
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                finishedIds.forEachIndexed { index, workoutId ->
                    val started = now - TimeUnit.DAYS.toMillis((30L - index) * 2)
                    db.update("logged_workout", ContentValues().apply {
                        put("started_at", started)
                        put("ended_at", started + TimeUnit.MINUTES.toMillis(45L + index % 12))
                    }, "id = ?", arrayOf(workoutId.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
