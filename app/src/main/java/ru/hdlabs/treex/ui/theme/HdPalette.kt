package ru.hdlabs.treex.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class HdPalette(
    val black: Color,
    val panel: Color,
    val panelElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val spaceGradientInner: Color,
    val useSpaceEffects: Boolean,
) {
    companion object {
        val Dark = HdPalette(
            black = Color(0xFF050508),
            panel = Color(0xFF12141C),
            panelElevated = Color(0xFF1A1E2A),
            textPrimary = Color(0xFFF2F4FA),
            textSecondary = Color(0xFFB0B8C8),
            spaceGradientInner = Color(0xFF102033),
            useSpaceEffects = true,
        )
        val Light = HdPalette(
            black = Color(0xFFF0F3FA),
            panel = Color(0xFFFFFFFF),
            panelElevated = Color(0xFFE6EBF5),
            textPrimary = Color(0xFF141824),
            textSecondary = Color(0xFF5A6478),
            spaceGradientInner = Color(0xFFD8E4F5),
            useSpaceEffects = false,
        )
    }
}

val LocalHdPalette = staticCompositionLocalOf { HdPalette.Dark }

val HdBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHdPalette.current.black

val HdPanel: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHdPalette.current.panel

val HdPanelElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHdPalette.current.panelElevated

val HdTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHdPalette.current.textPrimary

val HdTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalHdPalette.current.textSecondary
