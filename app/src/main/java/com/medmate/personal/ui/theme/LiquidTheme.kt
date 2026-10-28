package com.medmate.personal.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val Night = Color(0xFF0B1222)
val SurfaceGlass = Color(0xED17283E)
val Turquoise = Color(0xFF62F4D4)
val Lilac = Color(0xFFA89DFF)
val Sky = Color(0xFF77C6FF)
val Muted = Color(0xFFB6C8DD)

private val colors = darkColorScheme(
    primary = Turquoise,
    onPrimary = Night,
    secondary = Lilac,
    tertiary = Sky,
    background = Night,
    onBackground = Color(0xFFF4FAFF),
    surface = SurfaceGlass,
    onSurface = Color(0xFFF4FAFF),
    onSurfaceVariant = Muted,
)

@Composable
fun MedMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content
    )
}

/** Static translucent bubbles avoid GPU-expensive real-time blur. */
@Composable
fun LiquidBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Night)) {
        drawRect(brush = Brush.linearGradient(
            listOf(Color(0xFF18395D), Night, Color(0xFF132F38)),
            start = Offset.Zero, end = Offset(size.width, size.height)
        ))
        fun bubble(cx: Float, cy: Float, radius: Float, tint: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = .24f), tint.copy(alpha = .035f), Color.Transparent),
                    center = Offset(cx, cy), radius = radius
                ),
                radius = radius, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = .11f), radius, Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
        }
        bubble(size.width * .78f, size.height * .13f, size.minDimension * .30f, Lilac)
        bubble(size.width * .11f, size.height * .83f, size.minDimension * .27f, Turquoise)
        bubble(size.width * .06f, size.height * .26f, size.minDimension * .10f, Sky)
    }
}
