package az.simplesoft.aura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraColors = darkColorScheme(
    primary = Color(0xFF7C3CFF),
    onPrimary = Color.White,
    secondary = Color(0xFFC13DFF),
    onSecondary = Color.White,
    tertiary = Color(0xFF2467FF),
    background = Color(0xFF050A11),
    onBackground = Color(0xFFF7F6FC),
    surface = Color(0xFF111720),
    onSurface = Color(0xFFF7F5FF),
    surfaceVariant = Color(0xFF1A202A),
    onSurfaceVariant = Color(0xFFA9AFBB),
    outline = Color(0xFF353C48),
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
