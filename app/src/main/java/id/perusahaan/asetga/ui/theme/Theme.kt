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

// Palet bernuansa BCA Life: biru BCA sebagai warna utama, navy & biru muda sebagai pendukung.
object BrandColors {
    val BcaBlue = Color(0xFF0060AF)
    val BcaNavy = Color(0xFF003D7A)
    val BcaSky = Color(0xFF00A0DF)
}

private val LightColors = lightColorScheme(
    primary = BrandColors.BcaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E5FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7F8),
    onSecondaryContainer = Color(0xFF0D1E36),
    tertiary = Color(0xFF00729F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEBFA),
    onTertiaryContainer = Color(0xFF001E2C),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF181C22),
    surface = Color(0xFFF5F8FC),
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFDDE3EE),
    onSurfaceVariant = Color(0xFF424852),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF3F9),
    surfaceContainer = Color(0xFFE9EEF6),
    surfaceContainerHigh = Color(0xFFE3E9F2),
    surfaceContainerHighest = Color(0xFFDDE4EE),
    outline = Color(0xFF727884),
    outlineVariant = Color(0xFFC2C8D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C8FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF00468E),
    onPrimaryContainer = Color(0xFFD7E5FF),
    secondary = Color(0xFFB9C8E4),
    onSecondary = Color(0xFF23324B),
    secondaryContainer = Color(0xFF394A66),
    onSecondaryContainer = Color(0xFFDCE7F8),
    tertiary = Color(0xFF86D1F5),
    onTertiary = Color(0xFF003549),
    tertiaryContainer = Color(0xFF004D69),
    onTertiaryContainer = Color(0xFFCDEBFA),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F141B),
    onBackground = Color(0xFFDFE3EA),
    surface = Color(0xFF0F141B),
    onSurface = Color(0xFFDFE3EA),
    surfaceVariant = Color(0xFF424852),
    onSurfaceVariant = Color(0xFFC2C8D3),
    surfaceContainerLowest = Color(0xFF0A0E14),
    surfaceContainerLow = Color(0xFF171C23),
    surfaceContainer = Color(0xFF1B2028),
    surfaceContainerHigh = Color(0xFF262B33),
    surfaceContainerHighest = Color(0xFF31363E),
    outline = Color(0xFF8C929D),
    outlineVariant = Color(0xFF424852),
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
val LocalIsDarkTheme = staticCompositionLocalOf { false }

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
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalIsDarkTheme provides dark,
    ) {
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
    val isDark: Boolean
        @Composable get() = LocalIsDarkTheme.current
}
