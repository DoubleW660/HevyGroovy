package com.example.hevygroovy.ui

import com.example.hevygroovy.app.HistoryImportGateway
import com.example.hevygroovy.dto.HistoryImportResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HistoryImportViewModelTest {
    private class Gateway : HistoryImportGateway {
        var calls = 0
        lateinit var callback: HistoryImportGateway.Callback
        override fun importCsv(documentUri: String, timezone: String, callback: HistoryImportGateway.Callback) {
            calls++; this.callback = callback
        }
    }
    @Test fun rejectsInvalidTimezoneAndPreventsConcurrentImports() {
        val gateway = Gateway(); val model = HistoryImportViewModel(gateway)
        model.importFile("content://test", "invalid zone")
        assertEquals(0, gateway.calls); assertNotNull(model.state.error)
        model.importFile("content://test", "America/New_York")
        model.importFile("content://test", "America/New_York")
        assertEquals(1, gateway.calls); assertTrue(model.state.busy)
        gateway.callback.onFailure(IllegalArgumentException("Bad row 2"))
        assertFalse(model.state.busy); assertEquals("Bad row 2", model.state.error)
        assertEquals(0L, model.state.changes)
        model.importFile("content://test", "America/New_York")
        gateway.callback.onSuccess(HistoryImportResult(2, 1, 10, 0))
        assertEquals(1L, model.state.changes); assertEquals(2, model.state.result!!.importedWorkouts)
        model.importFile("content://test", "America/New_York")
        gateway.callback.onSuccess(HistoryImportResult(0, 3, 0, 0))
        assertEquals(1L, model.state.changes); assertEquals(3, model.state.result!!.skippedWorkouts)
    }
}
