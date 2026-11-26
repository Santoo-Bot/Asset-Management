package id.perusahaan.asetga.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.perusahaan.asetga.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5CAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3C),
    secondary = Color(0xFF4F5F75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4FD),
    onSecondaryContainer = Color(0xFF0B1C2E),
    tertiary = Color(0xFF006B5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9EF2E0),
    onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FB),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE0E2E7),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003061),
    primaryContainer = Color(0xFF004689),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFB7C8E1),
    onSecondary = Color(0xFF213245),
    secondaryContainer = Color(0xFF37485C),
    onSecondaryContainer = Color(0xFFD3E4FD),
    tertiary = Color(0xFF82D5C4),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFF9EF2E0),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

/** Warna status (sukses/peringatan/bahaya) yang konsisten di terang & gelap. */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF1B7F3B), successContainer = Color(0xFFD4F5DC), onSuccessContainer = Color(0xFF00391A),
    warning = Color(0xFF9A6200), warningContainer = Color(0xFFFFE8C2), onWarningContainer = Color(0xFF3A2400),
    danger = Color(0xFFBA1A1A), dangerContainer = Color(0xFFFFDAD6), onDangerContainer = Color(0xFF410002),
)
private val DarkStatus = StatusColors(
    success = Color(0xFF7EDB98), successContainer = Color(0xFF0F5228), onSuccessContainer = Color(0xFFD4F5DC),
    warning = Color(0xFFFFC66E), warningContainer = Color(0xFF5C3B00), onWarningContainer = Color(0xFFFFE8C2),
    danger = Color(0xFFFFB4AB), dangerContainer = Color(0xFF93000A), onDangerContainer = Color(0xFFFFDAD6),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun AsetGaTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
