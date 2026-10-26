package com.example.hevygroovy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.ZoneId

@Composable
internal fun HevyImportCard(model: HistoryImportViewModel) {
    val state = model.state
    var timezone by rememberSaveable { mutableStateOf(ZoneId.systemDefault().id) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.importFile(it.toString(), timezone) }
    }
    Card(Modifier.fillMaxWidth().testTag("hevy-import"),
        colors = groovyCardColors()) {
        Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(GroovyLayout.ItemGap)) {
            Text("Import from Hevy", style = MaterialTheme.typography.titleMedium)
            Text("Choose your Hevy workout CSV. Only missing workouts are added; existing workouts and edits are kept.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(timezone, { timezone = it }, enabled = !state.busy,
                label = { Text("Export timezone") }, singleLine = true,
                supportingText = { Text("Use the timezone where the workouts were logged, such as America/New_York.") },
                modifier = Modifier.fillMaxWidth().testTag("import-timezone"))
            Button(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight)
                    .testTag("import-hevy-csv")) { Text("Choose Hevy CSV") }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Importing workouts…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("import-error").semantics { liveRegion = LiveRegionMode.Polite })
            }
            state.result?.let { result ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${result.importedWorkouts} workouts added · ${result.skippedWorkouts} already imported or in history",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("import-result"))
                        Text("${result.importedSets} sets imported · ${result.createdExercises} new exercises",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
