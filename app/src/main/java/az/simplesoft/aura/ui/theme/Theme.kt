package az.simplesoft.aura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraColors = darkColorScheme(
    primary = Color(0xFFB9AFFF),
    onPrimary = Color(0xFF17112E),
    secondary = Color(0xFF58E1C1),
    onSecondary = Color(0xFF06231D),
    tertiary = Color(0xFFFFB6D1),
    background = Color(0xFF080911),
    onBackground = Color(0xFFF7F5FF),
    surface = Color(0xFF111321),
    onSurface = Color(0xFFF7F5FF),
    surfaceVariant = Color(0xFF1A1D2E),
    onSurfaceVariant = Color(0xFFAEB2C8),
    outline = Color(0xFF42465C),
    error = Color(0xFFFF6B7D)
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColors,
        typography = AuraTypography,
        content = content
    )
}
