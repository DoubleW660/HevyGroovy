package com.example.hevygroovy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** Mount inside an open form. Dispatching a save leaves the draft open until it commits. */
@Composable
internal fun SaveResultEffect(savedChanges: Long, onSaved: () -> Unit) {
    val openedAt = remember { savedChanges }
    LaunchedEffect(savedChanges) { if (savedChanges > openedAt) onSaved() }
}
