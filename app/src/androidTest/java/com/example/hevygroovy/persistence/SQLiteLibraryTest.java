package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.entity.*;
import com.example.hevygroovy.entity.enums.MuscleGroup;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SQLiteLibraryTest {
    @Test
    public void detailLookupJoinsCurrentTitlesScopesTemplateAndSortsRows() {
        TemplateWorkout mine = template();
        TemplateWorkout other = template();
        Exercise bench = exercise("Bench");
        Exercise squat = exercise("Squat");
        TemplateExercise later = row(mine.getId(), squat.getId(), 4);
        later.setRestTimeSeconds(90);
        rows.save(later);
        TemplateExercise first = row(mine.getId(), bench.getId(), 0);
        TemplateExercise tied = row(mine.getId(), squat.getId(), 0);
        row(other.getId(), bench.getId(), 0);
        bench.setTitle("Renamed bench");
        bench.setArchived(true);
        exercises.save(bench);
        List<com.example.hevygroovy.model.TemplateExerciseItemModel> detail = rows.findDetailsByTemplateWorkoutId(mine.getId());
        assertEquals(3, detail.size());
        assertEquals(first.getId(), detail.get(0).getTemplateExerciseId());
        assertEquals(tied.getId(), detail.get(1).getTemplateExerciseId());
        assertEquals(later.getId(), detail.get(2).getTemplateExerciseId());
        assertEquals("Renamed bench", detail.get(0).getExerciseTitle());
        assertNull(detail.get(0).getRestTargetSeconds());
        assertEquals(Integer.valueOf(90), detail.get(2).getRestTargetSeconds());
        assertTrue(rows.findDetailsByTemplateWorkoutId(999L).isEmpty());
    }
    @Test
    public void templateListingFiltersUserAndArchiveStateInRepository() {
        TemplateWorkout mine = template();
        TemplateWorkout archived = template();
        archived.setArchived(true);
        templates.save(archived);
        TemplateWorkout other = template();
        other.setUserId(2L);
        templates.save(other);
        assertEquals(1, templates.findByUserId(1L, false).size());
        assertEquals(mine.getId(), templates.findByUserId(1L, false).get(0).getId());
        assertEquals(2, templates.findByUserId(1L, true).size());
        assertTrue(templates.findByUserId(3L, true).isEmpty());
    }
    private SQLiteDatabase db;
    private WorkoutDatabase helper;
    private SQLiteExerciseRepository exercises;
    private SQLiteTemplateWorkoutRepository templates;
    private SQLiteTemplateExerciseRepository rows;

    @Before
    public void setUp() {
        db = SQLiteDatabase.create(null);
        helper = new WorkoutDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext());
        helper.onConfigure(db);
        helper.onCreate(db);
        exercises = new SQLiteExerciseRepository(db);
        templates = new SQLiteTemplateWorkoutRepository(db);
        rows = new SQLiteTemplateExerciseRepository(db);
    }

    @After
    public void tearDown() { db.close(); helper.close(); }

    @Test
    public void exerciseRoundTripSearchAndArchive() {
        Exercise exercise = exercise("Bench_100%");
        exercise.setDescription("Paused");
        exercise.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        exercise.setSecondaryMuscleGroups(List.of(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS));
        exercises.save(exercise);
        Exercise loaded = exercises.findById(exercise.getId()).get();
        assertEquals("Paused", loaded.getDescription());
        assertEquals(MuscleGroup.CHEST, loaded.getPrimaryMuscleGroup());
        assertEquals(exercise.getSecondaryMuscleGroups(), loaded.getSecondaryMuscleGroups());
        exercise("BenchX100X");
        assertEquals(1, exercises.searchByTitle("_100%", true).size());
        assertEquals(2, exercises.searchByTitle("bench", true).size());
        exercise.setArchived(true);
        exercises.save(exercise);
        assertEquals(1, exercises.findAll(false).size());
        assertEquals(2, exercises.findAll(true).size());
        assertTrue(exercises.searchByTitle("_100%", false).isEmpty());
        assertFalse(exercises.findById(999L).isPresent());
    }

    @Test
    public void templatesRoundTripAndFilterByArchivedFlag() {
        TemplateWorkout template = template();
        template.setDescription("Push day");
        template.setArchived(true);
        templates.save(template);
        TemplateWorkout loaded = templates.findById(template.getId()).get();
        assertEquals(1L, loaded.getUserId());
        assertEquals("Upper", loaded.getTitle());
        assertEquals("Push day", loaded.getDescription());
        assertEquals(10L, loaded.getCreatedAtEpochMillis());
        assertTrue(loaded.isArchived());
        assertTrue(templates.existsById(template.getId()));
        assertFalse(templates.existsById(999L));
        assertTrue(templates.findByArchive(false).isEmpty());
        assertEquals(1, templates.findByArchive(true).size());
        assertEquals(1, templates.findAll().size());
    }

    @Test
    public void templateExercisesPreserveNullableRestAndOrder() {
        TemplateWorkout template = template();
        Exercise exercise = exercise("Squat");
        assertNull(rows.findMaxOrderIndex(template.getId()));
        TemplateExercise later = row(template.getId(), exercise.getId(), 4);
        TemplateExercise earlier = row(template.getId(), exercise.getId(), 0);
        assertEquals(earlier.getId(), rows.findByTemplateWorkoutId(template.getId()).get(0).getId());
        assertEquals(Integer.valueOf(4), rows.findMaxOrderIndex(template.getId()));
        assertTrue(rows.existsInTemplate(template.getId(), exercise.getId()));
        assertFalse(rows.existsInTemplate(999L, exercise.getId()));
        assertNull(rows.findById(later.getId()).get().getRestTimeSeconds());
        later.setRestTimeSeconds(90);
        rows.save(later);
        assertEquals(Integer.valueOf(90), rows.findById(later.getId()).get().getRestTimeSeconds());
        rows.delete(later.getId());
        assertFalse(rows.findById(later.getId()).isPresent());
        assertThrows(IllegalStateException.class, () -> rows.save(later));
    }

    @Test
    public void versionOneMigrationPreservesWorkoutData() {
        SQLiteLoggedWorkoutRepository workouts = new SQLiteLoggedWorkoutRepository(db);
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(1L);
        workout.setStartedAtEpochMillis(10L);
        workouts.save(workout);
        // Remove only the v2 tables to reproduce the original v1 schema.
        db.execSQL("DROP TABLE template_exercise");
        db.execSQL("DROP TABLE template_workout");
        db.execSQL("DROP TABLE exercise");
        helper.onUpgrade(db, 1, 2);
        assertEquals(workout.getId(), workouts.findActiveWorkoutByUserId(1L).get().getId());
        assertTrue(exercise("Row").getId() > 0);
        assertTrue(template().getId() > 0);
    }

    private Exercise exercise(String title) {
        Exercise exercise = new Exercise();
        exercise.setTitle(title);
        return exercises.save(exercise);
    }

    private TemplateWorkout template() {
        TemplateWorkout template = new TemplateWorkout();
        template.setUserId(1L);
        template.setTitle("Upper");
        template.setCreatedAtEpochMillis(10L);
        return templates.save(template);
    }

    private TemplateExercise row(long template, long exercise, int order) {
        TemplateExercise row = new TemplateExercise();
        row.setTemplateWorkoutId(template);
        row.setExerciseId(exercise);
        row.setOrderIndex(order);
        return rows.save(row);
    }
}
