package dev.deviceai.demo.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Electric violet — voice/audio aesthetic
private val Purple10  = Color(0xFF1A0042)
private val Purple20  = Color(0xFF2D0072)
private val Purple30  = Color(0xFF4A1B8C)
private val Purple80  = Color(0xFFCFBCFF)
private val Purple90  = Color(0xFFE9DDFF)

// Cyan — recording / active state accent
private val Cyan10 = Color(0xFF001F29)
private val Cyan40 = Color(0xFF00729A)
private val Cyan80 = Color(0xFF72D6FF)
private val Cyan90 = Color(0xFFB3EEFF)

private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFF9D6FF7),   // Electric violet
    onPrimary            = Purple10,
    primaryContainer     = Purple30,
    onPrimaryContainer   = Purple90,
    secondary            = Color(0xFF4DD9E8),   // Cyan accent
    onSecondary          = Cyan10,
    secondaryContainer   = Cyan40,
    onSecondaryContainer = Cyan90,
    background           = Color(0xFF0F0F17),   // Near-black with blue tint
    onBackground         = Color(0xFFE6E0F0),
    surface              = Color(0xFF1C1B2E),
    onSurface            = Color(0xFFE6E0F0),
    surfaceVariant       = Color(0xFF2D2B40),
    onSurfaceVariant     = Color(0xFFCAC4DD),
    outline              = Color(0xFF938FA9),
    error                = Color(0xFFFF6B6B),
    onError              = Color(0xFF3A0000),
    errorContainer       = Color(0xFF6B0000),
    onErrorContainer     = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary              = Color(0xFF5B2DB0),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Purple90,
    onPrimaryContainer   = Purple10,
    secondary            = Color(0xFF006782),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Cyan90,
    onSecondaryContainer = Cyan10,
    background           = Color(0xFFFBF8FF),
    onBackground         = Color(0xFF1C1B2E),
    surface              = Color(0xFFFBF8FF),
    onSurface            = Color(0xFF1C1B2E),
    surfaceVariant       = Color(0xFFE7E0EB),
    onSurfaceVariant     = Color(0xFF4A4459),
)

@Composable
fun DeviceAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
