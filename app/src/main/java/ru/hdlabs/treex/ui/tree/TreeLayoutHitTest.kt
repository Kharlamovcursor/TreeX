package ru.hdlabs.treex.ui.tree

import androidx.compose.ui.geometry.Offset
import ru.hdlabs.treex.domain.layout.LayoutNode

object TreeLayoutHitTest {

    fun layoutPointFromViewport(viewportPoint: Offset, transform: CanvasTransform): Offset =
        Offset(
            (viewportPoint.x - transform.offsetX) / transform.scale,
            (viewportPoint.y - transform.offsetY) / transform.scale,
        )

    fun hitPersonId(
        viewportPoint: Offset,
        transform: CanvasTransform,
        nodes: List<LayoutNode>,
    ): String? {
        val p = layoutPointFromViewport(viewportPoint, transform)
        return nodes.firstOrNull { node ->
            p.x in node.x..(node.x + node.width) && p.y in node.y..(node.y + node.height)
        }?.personId
    }

    fun hitOpenLinkSlot(
        viewportPoint: Offset,
        transform: CanvasTransform,
        slots: List<OpenLinkSlotState>,
    ): OpenLinkSlotState? {
        val p = layoutPointFromViewport(viewportPoint, transform)
        return slots.firstOrNull { slot ->
            p.x in slot.x..(slot.x + slot.width) && p.y in slot.y..(slot.y + slot.height)
        }
    }
}
