package ru.hdlabs.treex.ui.tree

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ru.hdlabs.treex.domain.layout.Bounds

/**
 * Applies pan/zoom on a single [graphicsLayer] so tree content below does not recompose each frame.
 */
@Composable
fun TreeTransformLayer(
    targetTransform: CanvasTransform,
    contentBounds: Bounds,
    viewWidth: Float,
    viewHeight: Float,
    paddingPx: Float,
    onTransformCommitted: (CanvasTransform) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var displayScale by remember { mutableFloatStateOf(targetTransform.scale) }
    var displayOx by remember { mutableFloatStateOf(targetTransform.offsetX) }
    var displayOy by remember { mutableFloatStateOf(targetTransform.offsetY) }
    var wasTransforming by remember { mutableStateOf(false) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = Gestures.clampTransform(
            CanvasTransform(
                displayScale * zoomChange,
                displayOx + panChange.x,
                displayOy + panChange.y,
            ),
            contentBounds,
            viewWidth,
            viewHeight,
            paddingPx,
        )
        displayScale = next.scale
        displayOx = next.offsetX
        displayOy = next.offsetY
    }

    LaunchedEffect(transformableState.isTransformInProgress) {
        if (wasTransforming && !transformableState.isTransformInProgress) {
            onTransformCommitted(CanvasTransform(displayScale, displayOx, displayOy))
        }
        wasTransforming = transformableState.isTransformInProgress
    }

    LaunchedEffect(targetTransform, transformableState.isTransformInProgress) {
        if (transformableState.isTransformInProgress) return@LaunchedEffect
        val endScale = targetTransform.scale
        val endOx = targetTransform.offsetX
        val endOy = targetTransform.offsetY
        if (displayScale == endScale && displayOx == endOx && displayOy == endOy) return@LaunchedEffect
        val startScale = displayScale
        val startOx = displayOx
        val startOy = displayOy
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        ) { t, _ ->
            displayScale = startScale + (endScale - startScale) * t
            displayOx = startOx + (endOx - startOx) * t
            displayOy = startOy + (endOy - startOy) * t
        }
        displayScale = endScale
        displayOx = endOx
        displayOy = endOy
    }

    Box(
        modifier
            .graphicsLayer {
                translationX = displayOx
                translationY = displayOy
                scaleX = displayScale
                scaleY = displayScale
            }
            .transformable(state = transformableState),
    ) {
        content()
    }
}
