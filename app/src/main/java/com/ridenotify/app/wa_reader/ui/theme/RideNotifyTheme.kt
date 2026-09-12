package com.ridenotify.app.wa_reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridenotify.app.wa_reader.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2DA),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DE),
    onSecondaryContainer = Color(0xFF07201A),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF8FAF7),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5BE),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFF9EF2DA),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1D352F),
    secondaryContainer = Color(0xFF344C45),
    onSecondaryContainer = Color(0xFFCDE8DE),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val RideNotifyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun RideNotifyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = RideNotifyShapes,
        content = content,
    )
}
