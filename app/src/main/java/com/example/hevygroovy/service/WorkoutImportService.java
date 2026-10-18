package com.example.hevygroovy.service;

import com.example.hevygroovy.dto.HistoryImportResult;
import java.io.InputStream;
import java.time.ZoneId;

public interface WorkoutImportService {
    HistoryImportResult importCsv(long userId, InputStream input, ZoneId sourceTimezone) throws java.io.IOException;
}
