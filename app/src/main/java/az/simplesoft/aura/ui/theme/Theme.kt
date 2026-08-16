package az.simplesoft.aura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraColors = darkColorScheme(
    primary = Color(0xFFCBB9FF),
    onPrimary = Color(0xFF25232D),
    secondary = Color(0xFFAFA4CD),
    onSecondary = Color(0xFF24222B),
    tertiary = Color(0xFF9294B2),
    background = Color(0xFF28282F),
    onBackground = Color(0xFFF2EFF7),
    surface = Color(0xFF303039),
    onSurface = Color(0xFFF2EFF7),
    surfaceVariant = Color(0xFF393842),
    onSurfaceVariant = Color(0xFFB5B1BD),
    outline = Color(0xFF55535F),
    error = Color(0xFFFF8B9A)
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColors,
        typography = AuraTypography,
        content = content
    )
}
