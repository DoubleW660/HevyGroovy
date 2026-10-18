package com.example.hevygroovy.app;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.InputStream;
import java.time.ZoneId;

final class ServiceHistoryImportGateway implements HistoryImportGateway {
    private final AppWorker worker;
    private final ContentResolver resolver;
    ServiceHistoryImportGateway(AppWorker worker, ContentResolver resolver) { this.worker = worker; this.resolver = resolver; }
    @Override public void importCsv(String documentUri, String timezone, Callback callback) {
        worker.submit(() -> {
            try (InputStream stream = resolver.openInputStream(Uri.parse(documentUri))) {
                if (stream == null) throw new IllegalArgumentException("The selected file could not be opened.");
                return worker.services().getWorkoutImporter().importCsv(worker.userId(), stream, ZoneId.of(timezone));
            } catch (java.io.IOException error) {
                throw new IllegalArgumentException("Could not read the CSV. Choose the original UTF-8 Hevy export.", error);
            }
        }, callback::onSuccess, callback::onFailure);
    }
}
