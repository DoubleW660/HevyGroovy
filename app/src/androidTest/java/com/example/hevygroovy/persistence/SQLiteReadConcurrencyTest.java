package com.example.hevygroovy.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SQLiteReadConcurrencyTest {
    @Test
    public void walReadSnapshotAllowsWriterAndRemainsConsistent() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 35);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "concurrency-" + UUID.randomUUID() + ".db";
        WorkoutDatabase helper = new WorkoutDatabase(context, name);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch snapshotStarted = new CountDownLatch(1);
        CountDownLatch writerFinished = new CountDownLatch(1);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            Future<Integer> reader = workers.submit(() -> new SQLiteTransactions(db).read(() -> {
                int initial = count(db);
                snapshotStarted.countDown();
                try {
                    assertTrue("Writer blocked by read snapshot", writerFinished.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(error);
                }
                assertEquals(initial, count(db));
                return initial;
            }));
            assertTrue(snapshotStarted.await(5, TimeUnit.SECONDS));
            Future<?> writer = workers.submit(() -> {
                try {
                    new SQLiteTransactions(db).run(() -> {
                        db.execSQL("INSERT INTO logged_workout(user_id,started_at) VALUES (1,1)");
                        return null;
                    });
                } finally { writerFinished.countDown(); }
            });
            writer.get(10, TimeUnit.SECONDS);
            assertEquals(Integer.valueOf(0), reader.get(10, TimeUnit.SECONDS));
            assertEquals(1, count(db));
        } finally {
            writerFinished.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
            helper.close();
            context.deleteDatabase(name);
        }
    }

    private int count(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM logged_workout", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
