package com.yourstore.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Kutuku UI kit palette (sampled from the design)
val Brand = Color(0xFF514EB7)          // primary purple: buttons, active tabs, links
val BrandLight = Color(0xFFC4C3FF)     // lilac accent (banner shape, soft highlights)
val BrandSoft = Color(0xFFEEEDFB)      // very light purple backgrounds
val Ink = Color(0xFF1E222B)            // titles and body text
val InkMuted = Color(0xFF8A8FA0)       // secondary text
val InkHint = Color(0xFFA7AEC1)        // placeholders and inactive icons
val Field = Color(0xFFFAFAFC)          // input fields
val Tile = Color(0xFFF3F3F3)           // product image tiles, list cards
val Line = Color(0xFFEDEDF2)           // borders and dividers
val Discount = Color(0xFF16A34A)
val Warning = Color(0xFFE5484D)
val HotBg = Color(0xFFFFE7E7)
val NewBg = Color(0xFFFFEEE3)
val NewFg = Color(0xFFF97316)
val PopularBg = Color(0xFFE3F8EC)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    secondary = BrandLight,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Tile,
    onSurfaceVariant = InkMuted,
    outline = Line,
    outlineVariant = Line,
    error = Warning,
    errorContainer = HotBg,
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink),
    bodyLarge = TextStyle(fontSize = 15.sp, color = Ink, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Ink, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = InkMuted, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // The Kutuku design is light-only, so the app always uses the light palette.
    MaterialTheme(
        colorScheme = Light,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
