package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

@Composable
fun ProfileScreen(model: ProfileViewModel, onHistory: () -> Unit, onExercises: () -> Unit) {
    val state = model.state
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profile-screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("MAKE IT YOURS", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 1.6.sp)
                Text("Profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }
        item(key = "athlete") {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(Modifier.size(66.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("YOU", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Local athlete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Your profile on this phone", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.loading) {
            item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.error?.let { message ->
            item(key = "error") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = model::retry, enabled = !state.loading,
                            modifier = Modifier.testTag("profile-retry")) { Text("Retry") }
                    }
                }
            }
        }
        item(key = "settings") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Workout settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Default weight unit", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(WeightUnit.KG, WeightUnit.LB).forEach { unit ->
                                FilterChip(
                                    selected = state.loaded && state.unit == unit,
                                    onClick = { model.setDefaultUnit(unit) },
                                    enabled = state.loaded && !state.loading,
                                    modifier = Modifier.weight(1f).testTag("profile-unit-${unit.name}"),
                                    label = {
                                        Text(if (unit == WeightUnit.KG) "Kilograms (kg)" else "Pounds (lb)")
                                    },
                                )
                            }
                        }
                        Text("Used for new sets. Existing sets keep their recorded unit.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "shortcuts") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your training", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ProfileShortcut("Workout history", "Review your completed sessions", onHistory)
                ProfileShortcut("Exercise library", "Manage your exercises", onExercises)
            }
        }
        item(key = "storage") {
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved on this phone", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text("Your workouts, routines, and exercises are stored locally and available offline.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileShortcut(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
