package com.example.hevygroovy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val pickerDensity = LocalDensity.current
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
        CompositionLocalProvider(LocalDensity provides pickerDensity) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.85f)
            .testTag("rest-duration-layout")
            .padding(horizontal = GroovyLayout.ScreenPadding).padding(bottom = GroovyLayout.ScreenPadding)) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val selectedTextStyle = MaterialTheme.typography.titleLarge
            // Measure the selected label at the actual width so larger fonts and wrapped labels fit.
            val labelHeight = remember(durations, constraints.maxWidth, selectedTextStyle, textMeasurer,
                density.density, density.fontScale) {
                durations.maxOf { seconds ->
                    textMeasurer.measure(label(seconds), style = selectedTextStyle,
                        constraints = Constraints(maxWidth = constraints.maxWidth)).size.height
                }
            }
            val rowHeight = maxOf(48.dp, with(density) { labelHeight.toDp() } + 16.dp)
            val wheelHeight = maxOf(rowHeight, (screenHeight * 0.35f).coerceIn(144.dp, 240.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GroovyLayout.ItemGap)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .testTag("rest-duration-content"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rest target", style = MaterialTheme.typography.titleLarge)
                    Text("Scroll to choose a duration", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Box(Modifier.fillMaxWidth().height(wheelHeight), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(rowHeight)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)))
                        LazyColumn(state = list, flingBehavior = rememberSnapFlingBehavior(lazyListState = list),
                            userScrollEnabled = enabled,
                            contentPadding = PaddingValues(vertical = (wheelHeight - rowHeight) / 2),
                            modifier = Modifier.fillMaxSize().testTag("rest-duration-wheel").semantics {
                                contentDescription = "Rest duration"
                                stateDescription = label(selectedSeconds)
                                customActions = listOf(
                                    CustomAccessibilityAction("Shorter rest") { moveTo(selectedIndex - 1) },
                                    CustomAccessibilityAction("Longer rest") { moveTo(selectedIndex + 1) },
                                )
                            }) {
                            itemsIndexed(durations, key = { _, seconds -> seconds ?: -1 }) { index, seconds ->
                                Box(Modifier.fillMaxWidth().heightIn(min = rowHeight)
                                    .testTag("rest-duration-${seconds ?: "inherit"}")
                                    .selectable(selected = index == selectedIndex, enabled = enabled,
                                        role = Role.RadioButton, onClick = { moveTo(index) }), contentAlignment = Alignment.Center) {
                                    Text(label(seconds), style = if (index == selectedIndex) selectedTextStyle
                                        else MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center,
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
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = GroovyLayout.ButtonHeight).testTag("cancel-rest-duration")) { Text("Cancel") }
                    Button(onClick = { onSave(selectedSeconds) }, enabled = enabled && !list.isScrollInProgress,
                        modifier = Modifier.weight(1f).heightIn(min = GroovyLayout.ButtonHeight).testTag("save-rest-duration")) { Text("Save rest") }
                }
        }
        }
        }
    }
}
