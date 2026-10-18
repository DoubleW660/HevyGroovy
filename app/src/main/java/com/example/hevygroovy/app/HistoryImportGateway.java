package com.example.hevygroovy.app;

import com.example.hevygroovy.dto.HistoryImportResult;

public interface HistoryImportGateway {
    interface Callback {
        void onSuccess(HistoryImportResult result);
        void onFailure(Exception error);
    }
    void importCsv(String documentUri, String timezone, Callback callback);
}
