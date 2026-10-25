package ru.hdlabs.treex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val HdDarkScheme = darkColorScheme(
    primary = HdBlue,
    secondary = HdOrange,
    background = HdPalette.Dark.black,
    surface = HdPalette.Dark.panel,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = HdPalette.Dark.textPrimary,
    onSurface = HdPalette.Dark.textPrimary,
    error = HdDanger,
)

private val HdLightScheme = lightColorScheme(
    primary = HdBlue,
    secondary = HdOrange,
    background = HdPalette.Light.black,
    surface = HdPalette.Light.panel,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = HdPalette.Light.textPrimary,
    onSurface = HdPalette.Light.textPrimary,
    error = HdDanger,
)

@Composable
fun HdLabsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) HdPalette.Dark else HdPalette.Light
    CompositionLocalProvider(LocalHdPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) HdDarkScheme else HdLightScheme,
            typography = HdTypography,
            content = content,
        )
    }
}
