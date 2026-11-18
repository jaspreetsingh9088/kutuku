package com.yourstore.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourstore.app.R

/** Outfit — the typeface used throughout the Kutuku Figma kit (SIL Open Font License). */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

// Kutuku palette (sampled from the Figma frames)
val Brand = Color(0xFF514EB7)          // primary purple
val BrandLight = Color(0xFFC4C3FF)     // lilac accent (banner shape, halos)
val BrandSoft = Color(0xFFEDEDFB)      // selected backgrounds
val Ink = Color(0xFF1E222B)            // headings and body text
val Ink2 = Color(0xFF393F4E)           // secondary dark text
val InkMuted = Color(0xFF8A8FA0)       // secondary text
val InkHint = Color(0xFFAEB3C2)        // placeholders, inactive tabs and icons
val Field = Color(0xFFFAFAFC)          // input fields, list rows
val Tile = Color(0xFFF3F3F3)           // product image tiles, banners
val Line = Color(0xFFEFEFF3)           // borders and dividers
val Success = Color(0xFF16C25B)        // green check, "Completed"
val Discount = Color(0xFF16A34A)
val Warning = Color(0xFFE5484D)        // red: logout, delete, badges
val Star = Color(0xFFFFB800)
val Teal = Color(0xFF14A3B8)           // "On Progress"
val HotBg = Color(0xFFFFE7E7)
val NewBg = Color(0xFFFFEEE3)
val NewFg = Color(0xFFF97316)
val PopularBg = Color(0xFFE3F8EC)

private val Light = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = BrandSoft, onPrimaryContainer = Brand,
    secondary = BrandLight,
    background = Color.White, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Tile, onSurfaceVariant = InkMuted,
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color.White,
    outline = Line, outlineVariant = Line,
    error = Warning, errorContainer = HotBg,
)

// Colour is left unspecified so buttons, chips and app bars can supply their own content colour.
private fun t(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified, line: Int = (size * 1.4).toInt()) =
    TextStyle(fontFamily = Outfit, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = line.sp)

private val AppTypography = Typography(
    displaySmall = t(32, FontWeight.Bold),
    headlineMedium = t(24, FontWeight.SemiBold, line = 30),       // "Login Account", "Create Account"
    headlineSmall = t(22, FontWeight.SemiBold, line = 30),        // onboarding titles, "Verification Code"
    titleLarge = t(20, FontWeight.SemiBold),                      // product name on detail
    titleMedium = t(18, FontWeight.SemiBold),                     // section titles ("New Arrivals", "Address")
    titleSmall = t(16, FontWeight.Medium),                        // list titles, product names
    bodyLarge = t(15),
    bodyMedium = t(14),
    bodySmall = t(12, line = 18),
    labelLarge = t(16, FontWeight.Medium),                        // buttons
    labelMedium = t(14, FontWeight.Medium),                       // field labels
    labelSmall = t(11, FontWeight.Medium),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // The Kutuku design is light-only.
    MaterialTheme(
        colorScheme = Light,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
