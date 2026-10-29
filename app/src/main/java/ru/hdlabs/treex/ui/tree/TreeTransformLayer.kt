package ru.hdlabs.treex.ui.tree

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import ru.hdlabs.treex.domain.layout.Bounds
import kotlin.math.abs

/**
 * Pan/zoom on a full-screen gesture layer above content so [PersonCard] does not steal pointers.
 */
@Composable
fun TreeTransformLayer(
    targetTransform: CanvasTransform,
    contentBounds: Bounds,
    viewWidth: Float,
    viewHeight: Float,
    paddingPx: Float,
    onTransformCommitted: (CanvasTransform) -> Unit,
    onViewportTap: (Offset, CanvasTransform) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var displayScale by remember { mutableFloatStateOf(targetTransform.scale) }
    var displayOx by remember { mutableFloatStateOf(targetTransform.offsetX) }
    var displayOy by remember { mutableFloatStateOf(targetTransform.offsetY) }
    var isGesturing by remember { mutableStateOf(false) }
    var wasGesturing by remember { mutableStateOf(false) }

    val gestureSnapshot = remember {
        object {
            var scale = targetTransform.scale
            var ox = targetTransform.offsetX
            var oy = targetTransform.offsetY
        }
    }
    SideEffect {
        if (!isGesturing) {
            gestureSnapshot.scale = displayScale
            gestureSnapshot.ox = displayOx
            gestureSnapshot.oy = displayOy
        }
    }

    LaunchedEffect(isGesturing) {
        if (wasGesturing && !isGesturing) {
            onTransformCommitted(CanvasTransform(displayScale, displayOx, displayOy))
        }
        wasGesturing = isGesturing
    }

    LaunchedEffect(targetTransform, isGesturing) {
        if (isGesturing) return@LaunchedEffect
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

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = displayOx
                    translationY = displayOy
                    scaleX = displayScale
                    scaleY = displayScale
                },
        ) {
            content()
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(contentBounds, viewWidth, viewHeight, paddingPx) {
                    while (true) {
                        var workingScale = gestureSnapshot.scale
                        var workingOx = gestureSnapshot.ox
                        var workingOy = gestureSnapshot.oy
                        var moved = false
                        var tapAt = Offset.Zero
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (!isGesturing) isGesturing = true
                            if (tapAt == Offset.Zero) tapAt = centroid
                            if (pan.getDistance() > 3f || abs(zoom - 1f) > 0.01f) moved = true
                            workingScale = (workingScale * zoom).coerceIn(Gestures.MIN_SCALE, Gestures.MAX_SCALE)
                            workingOx += pan.x
                            workingOy += pan.y
                            val next = Gestures.clampTransform(
                                CanvasTransform(workingScale, workingOx, workingOy),
                                contentBounds,
                                viewWidth,
                                viewHeight,
                                paddingPx,
                            )
                            workingScale = next.scale
                            workingOx = next.offsetX
                            workingOy = next.offsetY
                            gestureSnapshot.scale = workingScale
                            gestureSnapshot.ox = workingOx
                            gestureSnapshot.oy = workingOy
                            displayScale = workingScale
                            displayOx = workingOx
                            displayOy = workingOy
                        }
                        isGesturing = false
                        if (!moved && tapAt != Offset.Zero) {
                            onViewportTap(
                                tapAt,
                                CanvasTransform(displayScale, displayOx, displayOy),
                            )
                        }
                    }
                },
        )
    }
}
