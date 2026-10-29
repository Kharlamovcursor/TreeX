package ru.hdlabs.treex.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Pinch-zoom and drag-pan for tree canvases (FamilyTree, RelativesTreeView). */
internal fun Modifier.treeViewportTransform(
    onGesture: (zoom: Float, pan: Offset, centroid: Offset) -> Unit,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        var panning = false
        var accumulated = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid()
                if (abs(zoom - 1f) > 0.001f || pan.getDistance() > 0.5f) {
                    onGesture(zoom, pan, centroid)
                    event.changes.forEach { change -> change.consume() }
                }
                panning = true
            } else {
                val change = pressed.first()
                val delta = change.position - change.previousPosition
                if (!panning) {
                    accumulated += delta
                    if (accumulated.getDistance() > slop) {
                        panning = true
                        onGesture(1f, accumulated, change.position)
                        change.consume()
                    }
                } else {
                    onGesture(1f, delta, change.position)
                    change.consume()
                }
            }
        }
    }
}
