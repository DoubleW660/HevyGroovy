package com.example.hevygroovy.app;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Serializes service access and posts results to main. Storage opens lazily on the worker. */
final class AppWorker {
    private final Executor worker;
    private final Executor main;
    private final Supplier<AppServices> openServices;
    private final LongSupplier identity;
    private AppServices services;
    private long userId;

    AppWorker(Executor worker, Executor main, Supplier<AppServices> openServices, LongSupplier identity) {
        this.worker = worker;
        this.main = main;
        this.openServices = openServices;
        this.identity = identity;
    }

    // Access only from an operation submitted below.
    AppServices services() { return services; }
    long userId() { return userId; }

    <T> void submit(Supplier<T> operation, Consumer<T> success, Consumer<Exception> failure) {
        worker.execute(() -> {
            T result;
            try {
                if (services == null) {
                    userId = identity.getAsLong();
                    services = openServices.get();
                }
                result = operation.get();
            } catch (Exception error) {
                main.execute(() -> failure.accept(error));
                return;
            }
            main.execute(() -> success.accept(result));
        });
    }

    void closeStorage() { if (services != null) services.close(); }
}
