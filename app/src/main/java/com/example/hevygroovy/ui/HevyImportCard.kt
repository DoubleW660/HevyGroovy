package com.example.hevygroovy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.ZoneId

@Composable
internal fun HevyImportCard(model: HistoryImportViewModel) {
    val state = model.state
    var timezone by rememberSaveable { mutableStateOf(ZoneId.systemDefault().id) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.importFile(it.toString(), timezone) }
    }
    Card(Modifier.fillMaxWidth().testTag("hevy-import")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import from Hevy", style = MaterialTheme.typography.titleMedium)
            Text("Choose your Hevy workout CSV. Only missing workouts are added; existing workouts and edits are kept.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(timezone, { timezone = it }, enabled = !state.busy,
                label = { Text("Export timezone") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("import-timezone"))
            Text("Hevy dates have no timezone. Use the timezone where the workouts were logged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                enabled = !state.busy, modifier = Modifier.testTag("import-hevy-csv")) { Text("Choose Hevy CSV") }
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Importing workouts…") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import-error")) }
            state.result?.let { result ->
                Text("${result.importedWorkouts} workouts added · ${result.skippedWorkouts} already imported or in history",
                    modifier = Modifier.testTag("import-result"))
                Text("${result.importedSets} sets imported · ${result.createdExercises} new exercises",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
