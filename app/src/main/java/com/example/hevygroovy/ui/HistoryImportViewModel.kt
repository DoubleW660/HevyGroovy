package com.example.hevygroovy.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.app.HistoryImportGateway
import com.example.hevygroovy.dto.HistoryImportResult
import java.time.ZoneId

data class HistoryImportState(val busy: Boolean = false, val result: HistoryImportResult? = null,
    val error: String? = null, val changes: Long = 0)

class HistoryImportViewModel(private val gateway: HistoryImportGateway) : ViewModel() {
    var state by mutableStateOf(HistoryImportState())
        private set
    private var cleared = false
    fun importFile(uri: String, timezone: String) {
        if (cleared || state.busy) return
        try { ZoneId.of(timezone) } catch (_: java.time.DateTimeException) {
            state = state.copy(error = "Enter a valid timezone, such as America/New_York."); return
        }
        state = state.copy(busy = true, error = null, result = null)
        gateway.importCsv(uri, timezone, object : HistoryImportGateway.Callback {
            override fun onSuccess(result: HistoryImportResult) {
                if (!cleared) state = state.copy(busy = false, result = result,
                    changes = state.changes + if (result.importedWorkouts > 0) 1 else 0)
            }
            override fun onFailure(error: Exception) {
                if (!cleared) state = state.copy(busy = false, error = if (error is IllegalArgumentException)
                    error.message else "Couldn't import the file. Please try again.")
            }
        })
    }
    override fun onCleared() { cleared = true }
    class Factory(private val gateway: HistoryImportGateway) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == HistoryImportViewModel::class.java)
            @Suppress("UNCHECKED_CAST") return HistoryImportViewModel(gateway) as T
        }
    }
}
