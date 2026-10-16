package com.example.hevygroovy.persistence;

import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.hevygroovy.entity.LoggedWorkout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SQLiteLoggedWorkoutRepositoryTest {
    @Test
    public void completedPaginationFiltersBeforeLimitAndOffset() {
        LoggedWorkout old = repository.save(workout(1L, 1000L, 2000L));
        LoggedWorkout tiedFirst = repository.save(workout(1L, 3000L, 4000L));
        LoggedWorkout tiedLast = repository.save(workout(1L, 3000L, 4000L));
        repository.save(workout(1L, 9000L, null));
        repository.save(workout(2L, 8000L, 10000L));
        List<LoggedWorkout> firstPage = repository.findCompletedByUserIdOrderByStartedAtDesc(1L, 2, 0);
        assertEquals(2, firstPage.size());
        assertEquals(tiedLast.getId(), firstPage.get(0).getId());
        assertEquals(tiedFirst.getId(), firstPage.get(1).getId());
        List<LoggedWorkout> nextPage = repository.findCompletedByUserIdOrderByStartedAtDesc(1L, 2, 2);
        assertEquals(1, nextPage.size());
        assertEquals(old.getId(), nextPage.get(0).getId());
        assertTrue(repository.findCompletedByUserIdOrderByStartedAtDesc(1L, 2, 3).isEmpty());
        assertTrue(repository.findCompletedByUserIdOrderByStartedAtDesc(1L, 0, 0).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> repository.findCompletedByUserIdOrderByStartedAtDesc(1L, 10, -1));
    }
    private SQLiteDatabase db;
    private SQLiteLoggedWorkoutRepository repository;

    @Before
    public void setUp() {
        db = SQLiteDatabase.create(null);
        // Exercise the production schema without creating or deleting the app's file.
        WorkoutDatabase helper = new WorkoutDatabase(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        helper.onConfigure(db);
        helper.onCreate(db);
        helper.close();
        repository = new SQLiteLoggedWorkoutRepository(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void saveRoundTripsNullableFieldsAndUpdatesExistingRow() {
        LoggedWorkout workout = workout(1L, 1000L, null);
        repository.save(workout);
        assertTrue(workout.getId() > 0);
        LoggedWorkout loaded = repository.findById(workout.getId()).get();
        assertEquals(1L, loaded.getUserId());
        assertEquals(1000L, loaded.getStartedAtEpochMillis());
        assertNull(loaded.getTemplateId());
        assertNull(loaded.getEndedAtEpochMillis());
        assertNull(loaded.getNameSnapshot());
        assertNull(loaded.getNotes());
        loaded.setTemplateId(7L);
        loaded.setNameSnapshot("Upper body");
        loaded.setNotes("Good session");
        loaded.setEndedAtEpochMillis(2000L);
        repository.save(loaded);
        LoggedWorkout updated = repository.findById(workout.getId()).get();
        assertEquals(Long.valueOf(7L), updated.getTemplateId());
        assertEquals("Upper body", updated.getNameSnapshot());
        assertEquals("Good session", updated.getNotes());
        assertEquals(Long.valueOf(2000L), updated.getEndedAtEpochMillis());
        assertEquals(1, repository.findByUserId(1L).size());
        assertFalse(repository.findActiveWorkoutByUserId(1L).isPresent());
    }

    @Test
    public void databaseRejectsSecondActiveWorkoutEvenThroughAnotherRepository() {
        repository.save(workout(1L, 1000L, null));
        SQLiteLoggedWorkoutRepository other = new SQLiteLoggedWorkoutRepository(db);
        assertThrows(SQLiteConstraintException.class,
                () -> other.save(workout(1L, 2000L, null)));
        other.save(workout(2L, 2000L, null));
        assertEquals(1, repository.findByUserId(1L).size());
        assertEquals(2L, repository.findActiveWorkoutByUserId(2L).get().getUserId());
    }

    @Test
    public void finishingAllowsANewActiveWorkout() {
        LoggedWorkout first = repository.save(workout(1L, 1000L, null));
        first.setEndedAtEpochMillis(2000L);
        repository.save(first);
        LoggedWorkout second = repository.save(workout(1L, 3000L, null));
        assertEquals(second.getId(), repository.findActiveWorkoutByUserId(1L).get().getId());
    }

    @Test
    public void historyIsUserScopedAndPaginatesWithDeterministicTies() {
        LoggedWorkout older = repository.save(workout(1L, 1000L, 2000L));
        LoggedWorkout tiedFirst = repository.save(workout(1L, 3000L, 4000L));
        LoggedWorkout tiedLast = repository.save(workout(1L, 3000L, 4000L));
        repository.save(workout(2L, 5000L, 6000L));
        List<LoggedWorkout> all = repository.findByUserId(1L);
        assertEquals(3, all.size());
        assertEquals(tiedLast.getId(), all.get(0).getId());
        assertEquals(tiedFirst.getId(), all.get(1).getId());
        assertEquals(older.getId(), all.get(2).getId());
        assertEquals(tiedFirst.getId(), repository.findByUserIdOrderByStartedAtDesc(1L, 1, 1).get(0).getId());
        assertTrue(repository.findByUserIdOrderByStartedAtDesc(1L, 0, 0).isEmpty());
        assertTrue(repository.findByUserIdOrderByStartedAtDesc(1L, 5, 99).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByUserIdOrderByStartedAtDesc(1L, -1, 0));
    }

    @Test
    public void missingRowsAreNotSilentlyInsertedOnUpdate() {
        assertFalse(repository.findById(999L).isPresent());
        LoggedWorkout missing = workout(1L, 1000L, null);
        missing.setId(999L);
        assertThrows(IllegalStateException.class, () -> repository.save(missing));
        assertTrue(repository.findByUserId(1L).isEmpty());
    }

    @Test
    public void rollbackRemovesWritesFromSharedRepository() {
        db.beginTransaction();
        try {
            repository.save(workout(1L, 1000L, null));
            // No setTransactionSuccessful: simulate a later child-write failure.
        } finally {
            db.endTransaction();
        }
        assertTrue(repository.findByUserId(1L).isEmpty());
    }

    private LoggedWorkout workout(long userId, long startedAt, Long endedAt) {
        LoggedWorkout workout = new LoggedWorkout();
        workout.setUserId(userId);
        workout.setStartedAtEpochMillis(startedAt);
        workout.setEndedAtEpochMillis(endedAt);
        return workout;
    }
}
