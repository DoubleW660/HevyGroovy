package com.example.hevygroovy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GroovyColors = darkColorScheme(
    primary = GroovyMint,
    onPrimary = Color(0xFF18230D),
    primaryContainer = Color(0xFF2A3B20),
    onPrimaryContainer = GroovyMint,
    secondary = Color(0xFF9BCDD1),
    onSecondary = Color(0xFF072F33),
    secondaryContainer = Color(0xFF21383C),
    onSecondaryContainer = Color(0xFFBCE8EB),
    background = GroovyBackground,
    onBackground = GroovyText,
    surface = GroovyBackground,
    onSurface = GroovyText,
    surfaceVariant = GroovySurface,
    surfaceDim = GroovyBackground,
    surfaceBright = GroovyRaised,
    surfaceContainerLowest = GroovyBackground,
    surfaceContainerLow = GroovySurface,
    surfaceContainer = GroovySurface,
    surfaceContainerHigh = GroovyRaised,
    surfaceContainerHighest = GroovyRaised,
    onSurfaceVariant = GroovyMuted,
    inverseSurface = GroovyText,
    inverseOnSurface = GroovyBackground,
    inversePrimary = Color(0xFF2A3B20),
    outline = Color(0xFF526173),
    outlineVariant = Color(0xFF2C3543),
    error = Color(0xFFFFACA6),
    errorContainer = Color(0xFF492725),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun HevyGroovyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GroovyColors,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
