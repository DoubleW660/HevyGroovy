package com.example.hevygroovy.benchmark;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.example.hevygroovy.app.LocalUserIdentity;
import com.example.hevygroovy.persistence.WorkoutDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Exists only in the separate benchmark APK. Fixture creation is never timed. */
public final class BenchmarkSetupActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView status = new TextView(this);
        status.setText("Preparing benchmark fixture");
        setContentView(status);
        int historyCount = getIntent().getIntExtra("history_count", 10);
        worker.execute(() -> {
            try {
                if (!getPackageName().equals("com.example.hevygroovy.benchmark")) {
                    throw new IllegalStateException("Fixture setup requires the isolated benchmark app");
                }
                if (historyCount != 10 && historyCount != 1000) throw new IllegalArgumentException("Unknown fixture");
                SharedPreferences fixture = getSharedPreferences("benchmark_fixture", MODE_PRIVATE);
                long userId = new LocalUserIdentity(this).getUserId();
                if (fixture.getInt("version", 0) != 1 || fixture.getInt("history_count", 0) != historyCount) {
                    seed(historyCount, userId);
                    if (!fixture.edit().putInt("version", 1).putInt("history_count", historyCount).commit()) {
                        throw new IllegalStateException("Could not persist fixture state");
                    }
                }
                runOnUiThread(() -> status.setText("Benchmark fixture ready"));
            } catch (Exception error) {
                Log.e("BenchmarkSetup", "Fixture setup failed", error);
                runOnUiThread(() -> status.setText("Benchmark fixture failed: " + error.getClass().getSimpleName()));
            }
        });
    }

    private void seed(int historyCount, long userId) {
        try (WorkoutDatabase helper = new WorkoutDatabase(this)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                for (String table : new String[]{"set_entry", "workout_exercise", "logged_workout",
                        "template_exercise", "template_workout", "exercise"}) db.delete(table, null, null);
                db.delete("sqlite_sequence", null, null);
                long now = System.currentTimeMillis();
                int exerciseCount = historyCount == 10 ? 200 : 500;
                int templateCount = historyCount == 10 ? 20 : 100;
                try (SQLiteStatement exercise = db.compileStatement("INSERT INTO exercise "
                        + "(id,title,primary_muscle,secondary_muscles,archived) VALUES (?,?,?,'',0)")) {
                    for (int i = 1; i <= exerciseCount; i++) {
                        exercise.bindLong(1, i);
                        exercise.bindString(2, i == 1 ? "Bench" : String.format(java.util.Locale.ROOT, "Exercise %03d", i));
                        exercise.bindString(3, i == 1 ? "CHEST" : "OTHER");
                        exercise.executeInsert();
                    }
                }
                try (SQLiteStatement template = db.compileStatement("INSERT INTO template_workout "
                        + "(id,user_id,title,created_at,archived) VALUES (?,?,?,?,0)");
                     SQLiteStatement row = db.compileStatement("INSERT INTO template_exercise "
                        + "(template_workout_id,exercise_id,order_index,rest_time_seconds) VALUES (?,?,?,90)")) {
                    for (int i = 1; i <= templateCount; i++) {
                        template.bindLong(1, i); template.bindLong(2, userId);
                        template.bindString(3, String.format(java.util.Locale.ROOT, "Template %03d", i));
                        template.bindLong(4, now - i);
                        template.executeInsert();
                        for (int j = 0; j < 8; j++) {
                            row.bindLong(1, i); row.bindLong(2, j + 1); row.bindLong(3, j);
                            row.executeInsert();
                        }
                    }
                }
                try (SQLiteStatement workout = db.compileStatement("INSERT INTO logged_workout "
                        + "(id,user_id,name_snapshot,started_at,ended_at) VALUES (?,?,?,?,?)");
                     SQLiteStatement row = db.compileStatement("INSERT INTO workout_exercise "
                        + "(logged_workout_id,exercise_id,exercise_name_snapshot,order_index,rest_time_seconds,notes) "
                        + "VALUES (?,?,?,?,90,'Previous performance note')");
                     SQLiteStatement set = db.compileStatement("INSERT INTO set_entry "
                        + "(workout_exercise_id,set_number,reps,weight,unit,set_type,created_at,completed) "
                        + "VALUES (?,?,8,60,'KG','NORMAL',?,?)")) {
                    for (int i = 0; i <= historyCount; i++) {
                        long started = i == 0 ? now : now - (long) i * 86_400_000;
                        workout.bindLong(1, i + 1); workout.bindLong(2, userId);
                        workout.bindString(3, i == 0 ? "Benchmark session" : "Session " + i);
                        workout.bindLong(4, started);
                        if (i == 0) workout.bindNull(5); else workout.bindLong(5, started + 3_600_000);
                        workout.executeInsert();
                        for (int j = 0; j < (i == 0 ? 1 : 6); j++) {
                            row.bindLong(1, i + 1); row.bindLong(2, j + 1);
                            row.bindString(3, j == 0 ? "Bench" : String.format(java.util.Locale.ROOT, "Exercise %03d", j + 1));
                            row.bindLong(4, j);
                            long rowId = row.executeInsert();
                            for (int k = 1; k <= (i == 0 ? 1 : 4); k++) {
                                set.bindLong(1, rowId); set.bindLong(2, k); set.bindLong(3, started);
                                set.bindLong(4, i == 0 ? 0 : 1);
                                set.executeInsert();
                            }
                        }
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
