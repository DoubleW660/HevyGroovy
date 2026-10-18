package com.example.hevygroovy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val restDurationOptions = (0..60 step 5).toList() + (75..600 step 15).toList()

internal fun restDurationLabel(seconds: Int): String = when {
    seconds == 0 -> "Off · 0 seconds"
    seconds < 60 -> "$seconds seconds"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} sec"
}

/** Changes remain local until Save, including durations outside the current picker increments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestDurationPicker(
    initialSeconds: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
    allowInherit: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
    testTag: String = "rest-duration-picker",
) {
    val durations = remember(initialSeconds, allowInherit) {
        val options = (restDurationOptions + listOfNotNull(initialSeconds)).distinct().sorted()
        if (allowInherit) listOf(null) + options else options
    }
    val initialIndex = durations.indexOf(initialSeconds).coerceAtLeast(0)
    var selectedSeconds by rememberSaveable(initialSeconds, allowInherit) { mutableStateOf(initialSeconds ?: if (allowInherit) null else 0) }
    val selectedIndex = durations.indexOf(selectedSeconds).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val centeredIndex by remember {
        derivedStateOf {
            val layout = list.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        }
    }
    LaunchedEffect(centeredIndex) {
        centeredIndex?.let { selectedSeconds = durations[it] }
    }
    fun label(seconds: Int?) = seconds?.let(::restDurationLabel) ?: "Use exercise rest"
    fun moveTo(index: Int): Boolean {
        if (!enabled || index !in durations.indices) return false
        scope.launch { list.animateScrollToItem(index) }
        return true
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(testTag)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rest target", style = MaterialTheme.typography.titleLarge)
            Text("Scroll to choose a duration", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)))
                LazyColumn(state = list, flingBehavior = rememberSnapFlingBehavior(lazyListState = list),
                    userScrollEnabled = enabled,
                    contentPadding = PaddingValues(vertical = 96.dp),
                    modifier = Modifier.fillMaxSize().testTag("rest-duration-wheel").semantics {
                        contentDescription = "Rest duration"
                        stateDescription = label(selectedSeconds)
                        customActions = listOf(
                            CustomAccessibilityAction("Shorter rest") { moveTo(selectedIndex - 1) },
                            CustomAccessibilityAction("Longer rest") { moveTo(selectedIndex + 1) },
                        )
                    }) {
                    itemsIndexed(durations, key = { _, seconds -> seconds ?: -1 }) { index, seconds ->
                        Box(Modifier.fillMaxWidth().height(48.dp)
                            .testTag("rest-duration-${seconds ?: "inherit"}")
                            .clickable(enabled = enabled) { moveTo(index) }
                            .semantics { selected = index == selectedIndex }, contentAlignment = Alignment.Center) {
                            Text(label(seconds), style = if (index == selectedIndex) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.bodyLarge,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (initialSeconds != null && initialSeconds !in restDurationOptions) {
                Text("Your saved duration is kept until you save a new choice.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-rest-duration")) { Text("Cancel") }
                Button(onClick = { onSave(selectedSeconds) }, enabled = enabled && !list.isScrollInProgress,
                    modifier = Modifier.testTag("save-rest-duration")) { Text("Save rest") }
            }
        }
    }
}
