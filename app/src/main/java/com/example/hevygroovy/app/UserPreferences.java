package com.example.hevygroovy.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.hevygroovy.entity.enums.Unit;
import java.util.Objects;

/** Local profile settings. Construct anywhere; read and write only on the app worker. */
public final class UserPreferences {
    private static final String DEFAULT_WEIGHT_UNIT = "default_weight_unit";
    private final Context context;
    private SharedPreferences preferences;

    public UserPreferences(Context context) { this.context = context; }

    public Unit readDefaultUnit() {
        String stored = preferences().getString(DEFAULT_WEIGHT_UNIT, Unit.LB.name());
        if (stored == null) return Unit.LB;
        try {
            return Unit.valueOf(stored);
        } catch (IllegalArgumentException unsupportedValue) {
            return Unit.LB;
        }
    }

    public void setDefaultUnit(Unit unit) {
        Objects.requireNonNull(unit, "Default weight unit is required");
        if (!preferences().edit().putString(DEFAULT_WEIGHT_UNIT, unit.name()).commit()) {
            throw new IllegalStateException("Could not save weight unit");
        }
    }

    private SharedPreferences preferences() {
        if (preferences == null) preferences = context.getSharedPreferences("local_profile", Context.MODE_PRIVATE);
        return preferences;
    }
}
