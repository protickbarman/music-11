package com.xrotune.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Accent = Color(0xFFB388FF)
private val AccentAlt = Color(0xFFFF6E9C)
private val BackgroundDark = Color(0xFF0E0B14)
private val SurfaceDark = Color(0xFF17131F)
private val SurfaceVariantDark = Color(0xFF241E33)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1030),
    primaryContainer = Color(0xFF3A2A5C),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = AccentAlt,
    onSecondary = Color(0xFF3A0A1E),
    background = BackgroundDark,
    onBackground = Color(0xFFEDE7F6),
    surface = SurfaceDark,
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF4A4458),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFFB3265F),
)

@Composable
fun XroTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Bar colours come from the theme; only the icon contrast needs
            // setting explicitly, since edge-to-edge is enabled.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
