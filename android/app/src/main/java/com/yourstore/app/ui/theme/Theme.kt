package com.yourstore.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Brand = Color(0xFF0F766E)
val BrandDark = Color(0xFF115E59)
val Discount = Color(0xFF15803D)
val Warning = Color(0xFFC2410C)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F0EC),
    onPrimaryContainer = Color(0xFF053B36),
    secondary = Color(0xFFB45309),
    secondaryContainer = Color(0xFFFDEBD3),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F5),
    outlineVariant = Color(0xFFE3E6EA),
    error = Color(0xFFB91C1C),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EC4B6),
    onPrimary = Color(0xFF00201C),
    primaryContainer = BrandDark,
    secondary = Color(0xFFF5B66B),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
