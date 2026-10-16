package com.example.hevygroovy;

import android.app.Application;
import com.example.hevygroovy.app.AppContainer;

public final class HevyGroovyApplication extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        // The container opens SQLite lazily on its worker, never on the main thread.
        container = new AppContainer(this);
    }

    public AppContainer getContainer() { return container; }
}
