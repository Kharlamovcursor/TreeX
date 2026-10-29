package ru.hdlabs.treex.ui.tree

import ru.hdlabs.treex.domain.layout.Bounds

object Gestures {
    const val MIN_SCALE = 0.4f
    const val MAX_SCALE = 2.5f

    fun clampTransform(
        transform: CanvasTransform,
        contentBounds: Bounds,
        viewWidth: Float,
        viewHeight: Float,
        margin: Float = 48f,
    ): CanvasTransform {
        val scale = transform.scale.coerceIn(MIN_SCALE, MAX_SCALE)
        val contentW = (contentBounds.maxX - contentBounds.minX) * scale
        val contentH = (contentBounds.maxY - contentBounds.minY) * scale
        val minOffsetX = if (contentW <= viewWidth) {
            (viewWidth - contentW) / 2f - contentBounds.minX * scale
        } else {
            viewWidth - contentW - margin - contentBounds.minX * scale
        }
        val maxOffsetX = if (contentW <= viewWidth) minOffsetX else margin - contentBounds.minX * scale
        val minOffsetY = if (contentH <= viewHeight) {
            (viewHeight - contentH) / 2f - contentBounds.minY * scale
        } else {
            viewHeight - contentH - margin - contentBounds.minY * scale
        }
        val maxOffsetY = if (contentH <= viewHeight) minOffsetY else margin - contentBounds.minY * scale
        return transform.copy(
            scale = scale,
            offsetX = transform.offsetX.coerceIn(minOf(minOffsetX, maxOffsetX), maxOf(minOffsetX, maxOffsetX)),
            offsetY = transform.offsetY.coerceIn(minOf(minOffsetY, maxOffsetY), maxOf(minOffsetY, maxOffsetY)),
        )
    }

    fun fitToScreen(contentBounds: Bounds, viewWidth: Float, viewHeight: Float): CanvasTransform {
        if (viewWidth <= 1f || viewHeight <= 1f) return CanvasTransform()
        val w = contentBounds.maxX - contentBounds.minX
        val h = contentBounds.maxY - contentBounds.minY
        if (w <= 1f || h <= 1f) return CanvasTransform()
        val scale = minOf(viewWidth / w, viewHeight / h, MAX_SCALE).coerceAtLeast(MIN_SCALE)
        val offsetX = (viewWidth - w * scale) / 2f - contentBounds.minX * scale
        val offsetY = (viewHeight - h * scale) / 2f - contentBounds.minY * scale
        return CanvasTransform(scale, offsetX, offsetY)
    }
}
