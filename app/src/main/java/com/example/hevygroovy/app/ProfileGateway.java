package com.example.hevygroovy.app;

import com.example.hevygroovy.entity.enums.Unit;

/** Local settings are persisted on the worker and delivered to the main thread. */
public interface ProfileGateway {
    interface Callback {
        void onSuccess(Unit defaultUnit);
        void onFailure(Exception error);
    }

    void load(Callback callback);
    void setDefaultUnit(Unit unit, Callback callback);
}
