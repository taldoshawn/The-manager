package dev.themanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF10295D),
    primaryContainer = Color(0xFF1D376E),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC0C6D4),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE4E7EC),
    surface = Color(0xFF0F1217),
    surfaceVariant = Color(0xFF1A1E25),
    onSurfaceVariant = Color(0xFFC3C7D0),
    outline = Color(0xFF5D626C),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF44474F),
)

@Composable
fun TheManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
