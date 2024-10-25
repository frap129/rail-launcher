package core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import core.ui.R

fun getColorScheme(accent: Color, backgroundLight: Color, backgroundMid: Color, backgroundDark: Color) = darkColorScheme(
    primary = backgroundMid,
    onPrimary = accent,
    primaryContainer = backgroundLight,
    onPrimaryContainer = accent,
    secondary = backgroundMid,
    onSecondary = accent,
    secondaryContainer = backgroundLight,
    onSecondaryContainer = accent,
    tertiary = backgroundMid,
    onTertiary = accent,
    tertiaryContainer = backgroundLight,
    onTertiaryContainer = accent,
    background = backgroundDark,
    onBackground = accent,
    surface = backgroundMid,
    onSurface = accent,
    surfaceVariant = backgroundLight,
    onSurfaceVariant = accent
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val view = LocalView.current
    val colorScheme = getColorScheme(
        colorResource(id = R.color.accent),
        colorResource(id = R.color.backgroundLight),
        colorResource(id = R.color.backgroundMid),
        colorResource(id = R.color.backgroundDark)
    )
    val typography = getTypography()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
