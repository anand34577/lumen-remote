package com.lumen.tvremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lumen.tvremote.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tvremote.data.AppSettings
import com.lumen.tvremote.data.ThemeMode

data class Accent(val name: String, val seed: Color, val secondary: Color, val tertiary: Color)

val Accents = listOf(
    Accent("Violet", Color(0xFF6D4AFF), Color(0xFF5E5A7A), Color(0xFF1FB6FF)),
    Accent("Ocean", Color(0xFF1677D2), Color(0xFF526070), Color(0xFF00A99D)),
    Accent("Emerald", Color(0xFF0E9F6E), Color(0xFF4E6356), Color(0xFF3C8DBC)),
    Accent("Sunset", Color(0xFFE8590C), Color(0xFF77574A), Color(0xFFD6336C)),
    Accent("Rose", Color(0xFFD6336C), Color(0xFF74565F), Color(0xFF7950F2)),
)

private fun tone(c: Color, towardWhite: Float) =
    if (towardWhite >= 0) lerp(c, Color.White, towardWhite) else lerp(c, Color.Black, -towardWhite)

private fun lightScheme(a: Accent): ColorScheme = lightColorScheme(
    primary = a.seed,
    onPrimary = Color.White,
    primaryContainer = tone(a.seed, 0.82f),
    onPrimaryContainer = tone(a.seed, -0.65f),
    secondary = a.secondary,
    onSecondary = Color.White,
    secondaryContainer = tone(a.seed, 0.9f),
    onSecondaryContainer = tone(a.secondary, -0.6f),
    tertiary = a.tertiary,
    onTertiary = Color.White,
    tertiaryContainer = tone(a.tertiary, 0.85f),
    onTertiaryContainer = tone(a.tertiary, -0.65f),
    background = tone(a.seed, 0.97f),
    onBackground = Color(0xFF1C1B1F),
    surface = tone(a.seed, 0.97f),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = tone(a.seed, 0.88f),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = tone(a.seed, 0.95f),
    surfaceContainer = tone(a.seed, 0.93f),
    surfaceContainerHigh = tone(a.seed, 0.9f),
    surfaceContainerHighest = tone(a.seed, 0.87f),
    outline = Color(0xFF79747E),
    outlineVariant = tone(a.seed, 0.78f),
)

private fun darkScheme(a: Accent): ColorScheme {
    val base = Color(0xFF0A0A12)
    return darkColorScheme(
        // Kept saturated so gradients and white labels on the accent stay vivid and legible.
        primary = tone(a.seed, 0.18f),
        onPrimary = Color.White,
        primaryContainer = tone(a.seed, -0.35f),
        onPrimaryContainer = tone(a.seed, 0.85f),
        secondary = tone(a.secondary, 0.5f),
        onSecondary = tone(a.secondary, -0.7f),
        secondaryContainer = lerp(base, a.seed, 0.22f),
        onSecondaryContainer = tone(a.seed, 0.85f),
        tertiary = tone(a.tertiary, 0.1f),
        onTertiary = Color.White,
        tertiaryContainer = tone(a.tertiary, -0.45f),
        onTertiaryContainer = tone(a.tertiary, 0.85f),
        background = base,
        onBackground = Color(0xFFE6E1E9),
        surface = base,
        onSurface = Color(0xFFE6E1E9),
        surfaceVariant = lerp(base, a.seed, 0.14f),
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceContainerLowest = Color(0xFF0E0D12),
        surfaceContainerLow = lerp(base, a.seed, 0.05f),
        surfaceContainer = lerp(base, a.seed, 0.08f),
        surfaceContainerHigh = lerp(base, a.seed, 0.12f),
        surfaceContainerHighest = lerp(base, a.seed, 0.16f),
        outline = Color(0xFF938F99),
        outlineVariant = lerp(base, a.seed, 0.25f),
    )
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.5f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.6f),
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val LumenTypography = Typography().run {
    fun TextStyle.j(weight: FontWeight, spacing: Float? = null) =
        copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = spacing?.sp ?: letterSpacing)
    copy(
        displaySmall = displaySmall.j(FontWeight.ExtraBold, -0.5f),
        headlineLarge = headlineLarge.j(FontWeight.ExtraBold, -0.5f),
        headlineMedium = headlineMedium.j(FontWeight.Bold, -0.4f),
        headlineSmall = headlineSmall.j(FontWeight.Bold, -0.3f),
        titleLarge = titleLarge.j(FontWeight.Bold, -0.2f),
        titleMedium = titleMedium.j(FontWeight.SemiBold),
        titleSmall = titleSmall.j(FontWeight.SemiBold),
        bodyLarge = bodyLarge.j(FontWeight.Normal),
        bodyMedium = bodyMedium.j(FontWeight.Normal),
        bodySmall = bodySmall.j(FontWeight.Normal),
        labelLarge = labelLarge.j(FontWeight.SemiBold),
        labelMedium = labelMedium.j(FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = Jakarta, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    )
}

private val LumenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LumenTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    val accent = Accents[settings.accentIndex.coerceIn(Accents.indices)]
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }
    if (dark && settings.amoledBlack) scheme = scheme.amoled()
    MaterialTheme(colorScheme = scheme, typography = LumenTypography, shapes = LumenShapes, content = content)
}
