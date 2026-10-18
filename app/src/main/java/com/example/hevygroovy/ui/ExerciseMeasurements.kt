package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.ActiveSetEntry
import com.example.hevygroovy.entity.enums.TrackingType
import java.text.NumberFormat

internal fun TrackingType.label() = when (this) {
    TrackingType.WEIGHT_REPS -> "Weight & reps"
    TrackingType.REPS -> "Reps only"
    TrackingType.TIME -> "Time"
    TrackingType.DISTANCE -> "Distance"
    TrackingType.DISTANCE_TIME -> "Distance & time"
    TrackingType.WEIGHT_TIME -> "Weight & time"
}

@Composable
internal fun TrackingTypeSelector(value: TrackingType, enabled: Boolean, onChange: (TrackingType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Set measurements", style = MaterialTheme.typography.titleSmall)
        TrackingType.entries.chunked(2).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option -> FilterChip(value == option, { onChange(option) }, enabled = enabled,
                    label = { Text(option.label()) }, modifier = Modifier.testTag("tracking-${option.name}")) }
            }
        }
    }
}

internal fun ActiveSetEntry.measurementDetails(): List<String> = buildList {
    durationSeconds?.let { add("Time ${it / 60}:${(it % 60).toString().padStart(2, '0')}") }
    distanceMeters?.let { meters ->
        val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }
        add(if (meters >= 1000) "Distance ${number.format(meters / 1000)} km" else "Distance ${number.format(meters)} m")
    }
}
