package com.example.hevygroovy.app;

import com.example.hevygroovy.entity.enums.Unit;

final class ServiceProfileGateway implements ProfileGateway {
    private final AppWorker worker;
    private final UserPreferences preferences;

    ServiceProfileGateway(AppWorker worker, UserPreferences preferences) {
        this.worker = worker;
        this.preferences = preferences;
    }

    @Override
    public void load(Callback callback) {
        worker.submit(preferences::readDefaultUnit, callback::onSuccess, callback::onFailure);
    }

    @Override
    public void setDefaultUnit(Unit unit, Callback callback) {
        worker.submit(() -> {
            preferences.setDefaultUnit(unit);
            // Return the committed value directly; no follow-up read can turn a save into a retry.
            return unit;
        }, callback::onSuccess, callback::onFailure);
    }
}
