package com.example.hevygroovy.app;

import android.content.Context;
import android.content.SharedPreferences;

/** One offline profile per installation until account support exists. Access on the worker. */
public final class LocalUserIdentity {
    private final SharedPreferences preferences;

    public LocalUserIdentity(Context context) {
        preferences = context.getSharedPreferences("local_profile", Context.MODE_PRIVATE);
    }

    public long getUserId() {
        long id = preferences.getLong("user_id", 0L);
        if (id > 0) { return id; }
        // Retain compatibility with workouts created under the original local profile.
        id = 1L;
        if (!preferences.edit().putLong("user_id", id).commit()) {
            throw new IllegalStateException("Could not save local profile");
        }
        return id;
    }
}
