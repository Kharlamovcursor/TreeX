package ru.hdlabs.treex.ui.motion

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

@Immutable
data class HdMotionSpec(
    val buttonPressScale: Float = 0.93f,
    val buttonPressDurationMs: Int = 130,
    val screenEnterMs: Int = 280,
    val screenExitMs: Int = 180,
    val screenSlideDivisor: Int = 12,
)

val LocalHdMotion = compositionLocalOf { HdMotionSpec() }
