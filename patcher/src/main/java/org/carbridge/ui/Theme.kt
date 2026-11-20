package org.carbridge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.carbridge.R

/** Road-sign fields that keep their meaning in both themes, like real signs at night. */
@Immutable
data class SignColors(
    val guide: Color,
    val onGuide: Color,
    val onGuideMuted: Color,
    val caution: Color,
    val onCaution: Color,
    val onCautionMuted: Color,
)

private val LightSigns = SignColors(
    guide = Color(0xFF0B6B4C),
    onGuide = Color.White,
    onGuideMuted = Color(0xFFCDEBDD),
    caution = Color(0xFFF6C700),
    onCaution = Color(0xFF1C1800),
    onCautionMuted = Color(0xFF4A4000),
)

private val DarkSigns = LightSigns.copy(
    guide = Color(0xFF0A5C42),
    caution = Color(0xFFE3B800),
)

val LocalSigns = staticCompositionLocalOf { LightSigns }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B6B4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0D2),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092017),
    tertiary = Color(0xFF6B5E00),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F0),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDEE4DF),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AD6B3),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA6F2CE),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF344B40),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFDCC85A),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
)

// Overpass descends from Highway Gothic, the lettering on US guide signs. Bundled, not downloaded,
// so it needs neither Play Services nor a network request.
@OptIn(ExperimentalTextApi::class)
private val Overpass = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map {
        Font(R.font.overpass, weight = it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    },
)

private fun TextStyle.overpass(weight: FontWeight? = null) =
    copy(fontFamily = Overpass, fontWeight = weight ?: fontWeight)

private val CarBridgeType = Typography().run {
    copy(
        displaySmall = displaySmall.overpass(FontWeight.ExtraBold),
        headlineMedium = headlineMedium.overpass(FontWeight.ExtraBold),
        headlineSmall = headlineSmall.overpass(FontWeight.Bold),
        titleLarge = titleLarge.overpass(FontWeight.Bold),
        titleMedium = titleMedium.overpass(FontWeight.Bold),
        titleSmall = titleSmall.overpass(FontWeight.SemiBold),
        // Overpass is already wide; Material's body tracking makes paragraphs look spaced out
        bodyLarge = bodyLarge.overpass().copy(letterSpacing = 0.sp),
        bodyMedium = bodyMedium.overpass().copy(letterSpacing = 0.sp),
        bodySmall = bodySmall.overpass().copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.overpass(FontWeight.Bold),
        labelMedium = labelMedium.overpass(FontWeight.Bold),
        labelSmall = labelSmall.overpass(FontWeight.Bold),
    )
}

@Composable
fun CarBridgeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSigns provides if (dark) DarkSigns else LightSigns) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = CarBridgeType,
            content = content,
        )
    }
}
