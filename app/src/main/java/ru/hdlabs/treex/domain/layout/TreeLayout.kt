package ru.hdlabs.treex.domain.layout

/** Result of genealogy layout: node positions, family hubs, and orthogonal edges. */
enum class EdgeType {
    SPOUSE,
    PARENT_CHILD,
    LEGACY_PARENT_CHILD,
}

data class LayoutNode(
    val personId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val generationLevel: Int,
)

data class FamilyBlock(
    val familyId: String,
    val partnerAId: String?,
    val partnerBId: String?,
    val centerX: Float,
    val topY: Float,
    val bottomY: Float,
    val generationLevel: Int,
)

data class LayoutEdge(
    val type: EdgeType,
    val fromFamilyId: String?,
    val fromPersonId: String?,
    val toPersonId: String?,
    val points: List<PointF>,
)

data class PointF(val x: Float, val y: Float)

data class TreeLayout(
    val nodes: List<LayoutNode>,
    val familyBlocks: List<FamilyBlock>,
    val edges: List<LayoutEdge>,
    val bounds: Bounds,
)

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)
