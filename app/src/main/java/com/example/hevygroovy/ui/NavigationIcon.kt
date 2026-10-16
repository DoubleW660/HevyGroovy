package com.example.hevygroovy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

internal enum class NavigationIcon { Home, Exercises, Workout, History, Profile }

@Composable
internal fun NavigationGlyph(icon: NavigationIcon, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)
            when (icon) {
                NavigationIcon.Home -> {
                    drawPath(Path().apply { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f) }, color, style = stroke)
                    drawPath(Path().apply { moveTo(5f, 9f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 9f) }, color, style = stroke)
                    drawRoundRect(color, Offset(10f, 14f), Size(4f, 7f), CornerRadius(1f), style = stroke)
                }
                NavigationIcon.Workout -> {
                    line(1.5f, 9f, 1.5f, 15f); line(22.5f, 9f, 22.5f, 15f)
                    drawRoundRect(color, Offset(4f, 6f), Size(4f, 12f), CornerRadius(1f), style = stroke)
                    drawRoundRect(color, Offset(16f, 6f), Size(4f, 12f), CornerRadius(1f), style = stroke)
                    line(8f, 12f, 16f, 12f)
                }
                NavigationIcon.Exercises -> {
                    repeat(3) { index ->
                        val y = 5f + index * 7f
                        drawCircle(color, 1.6f, Offset(4f, y))
                        line(9f, y, 21f, y)
                    }
                }
                NavigationIcon.History -> {
                    drawRoundRect(color, Offset(3f, 5f), Size(18f, 16f), CornerRadius(3f), style = stroke)
                    line(7f, 3f, 7f, 7f); line(17f, 3f, 17f, 7f); line(3f, 10f, 21f, 10f)
                    line(7f, 14f, 10f, 14f); line(14f, 14f, 17f, 14f)
                    line(7f, 17f, 10f, 17f)
                }
                NavigationIcon.Profile -> {
                    drawCircle(color, 4f, Offset(12f, 7f), style = stroke)
                    drawPath(Path().apply { moveTo(4f, 22f); cubicTo(4f, 12f, 20f, 12f, 20f, 22f) }, color, style = stroke)
                }
            }
        }
    }
}
