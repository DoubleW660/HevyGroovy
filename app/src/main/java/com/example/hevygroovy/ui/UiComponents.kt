package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared rhythm for phone layouts and centered content on larger displays. */
internal object GroovyLayout {
    val ScreenPadding = 20.dp
    val CardPadding = 16.dp
    val SectionGap = 16.dp
    val ItemGap = 12.dp
    val ButtonHeight = 48.dp
    val ContentMaxWidth = 840.dp
}

@Composable
internal fun groovyCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
internal fun ScreenHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier,
    retryEnabled: Boolean = true) {
    Card(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Column(Modifier.padding(GroovyLayout.CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry, enabled = retryEnabled,
                modifier = Modifier.align(androidx.compose.ui.Alignment.End).heightIn(min = GroovyLayout.ButtonHeight)) {
                Text("Retry")
            }
        }
    }
}

/** One destination, one focus target; the inset action label is visual, not a nested button. */
@Composable
internal fun PrimaryActionCard(title: String, description: String, actionLabel: String,
    onClick: () -> Unit, enabled: Boolean, highlighted: Boolean = false) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Column(Modifier.padding(GroovyLayout.CardPadding),
            verticalArrangement = Arrangement.spacedBy(GroovyLayout.ItemGap)) {
            Text(title, style = if (highlighted) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                shape = ButtonDefaults.shape) {
                Row(Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
