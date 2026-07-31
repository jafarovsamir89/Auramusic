package az.simplesoft.aura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraColors = darkColorScheme(
    primary = Color(0xFFE8F1FF),
    onPrimary = Color(0xFF07101D),
    secondary = Color(0xFFAEC7FF),
    background = Color(0xFF08090B),
    onBackground = Color(0xFFF4F6FA),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFF1A1E25),
    onSurfaceVariant = Color(0xFFB9C0CC)
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColors,
        typography = AuraTypography,
        content = content
    )
}
