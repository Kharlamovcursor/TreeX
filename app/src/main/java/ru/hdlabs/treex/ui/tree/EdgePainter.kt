package ru.hdlabs.treex.ui.tree

import ru.hdlabs.treex.domain.layout.EdgeType
import ru.hdlabs.treex.domain.layout.FamilyBlock
import ru.hdlabs.treex.domain.layout.LayoutEdge
import ru.hdlabs.treex.domain.layout.LayoutNode
import ru.hdlabs.treex.domain.layout.PointF

/** Pure geometry for tree edges (Compose path built in [TreeCanvas]). */
object EdgePainter {

    fun spousePoints(a: LayoutNode, b: LayoutNode): List<PointF> {
        val y = a.y + a.height / 2f
        return listOf(
            PointF(a.x + a.width, y),
            PointF(b.x, y),
        )
    }

    fun parentChildPoints(family: FamilyBlock, child: LayoutNode): List<PointF> {
        val startX = family.centerX
        val startY = family.bottomY
        val endX = child.x + child.width / 2f
        val endY = child.y
        val midY = (startY + endY) / 2f
        return listOf(
            PointF(startX, startY),
            PointF(startX, midY),
            PointF(endX, midY),
            PointF(endX, endY),
        )
    }

    fun pointsForEdge(edge: LayoutEdge, nodes: Map<String, LayoutNode>, blocks: Map<String, FamilyBlock>): List<PointF> {
        if (edge.points.size >= 2) return edge.points
        return when (edge.type) {
            EdgeType.SPOUSE -> {
                val a = edge.fromPersonId?.let { nodes[it] } ?: return emptyList()
                val b = edge.toPersonId?.let { nodes[it] } ?: return emptyList()
                spousePoints(a, b)
            }
            EdgeType.PARENT_CHILD, EdgeType.LEGACY_PARENT_CHILD -> {
                val fam = edge.fromFamilyId?.let { blocks[it] } ?: return emptyList()
                val child = edge.toPersonId?.let { nodes[it] } ?: return emptyList()
                parentChildPoints(fam, child)
            }
        }
    }

    fun isLegacy(type: EdgeType): Boolean = type == EdgeType.LEGACY_PARENT_CHILD
}
