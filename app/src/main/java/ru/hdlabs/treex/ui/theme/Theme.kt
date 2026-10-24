package ru.hdlabs.treex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HdColorScheme = darkColorScheme(
    primary = HdBlue,
    secondary = HdOrange,
    background = HdBlack,
    surface = HdPanel,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = HdTextPrimary,
    onSurface = HdTextPrimary,
    error = HdDanger,
)

@Composable
fun HdLabsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HdColorScheme,
        typography = HdTypography,
        content = content,
    )
}
