package ru.hdlabs.treex.domain.layout

import ru.hdlabs.treex.domain.graph.GenealogyGraph

/** Builds orthogonal spouse and parent-child edges (one hub edge per child per family). */
object EdgeBuilder {

    fun build(
        graph: GenealogyGraph,
        nodes: List<LayoutNode>,
        familyBlocks: List<FamilyBlock>,
    ): List<LayoutEdge> {
        val nodeById = nodes.associateBy { it.personId }
        val blockById = familyBlocks.associateBy { it.familyId }
        val edges = mutableListOf<LayoutEdge>()

        familyBlocks.sortedBy { it.familyId }.forEach { block ->
            val a = block.partnerAId
            val b = block.partnerBId
            if (a != null && b != null) {
                val na = nodeById[a]
                val nb = nodeById[b]
                if (na != null && nb != null) {
                    val y = na.y + na.height / 2f
                    edges += LayoutEdge(
                        type = EdgeType.SPOUSE,
                        fromFamilyId = block.familyId,
                        fromPersonId = a,
                        toPersonId = b,
                        points = listOf(
                            PointF(na.x + na.width, y),
                            PointF(nb.x, y),
                        ),
                    )
                }
            }
        }

        graph.families.values.sortedBy { it.id }.forEach { fam ->
            val block = blockById[fam.id] ?: return@forEach
            val hasParent = fam.partnerAId != null || fam.partnerBId != null
            if (!hasParent) return@forEach
            val edgeType = if (fam.id.startsWith("legacy-")) {
                EdgeType.LEGACY_PARENT_CHILD
            } else {
                EdgeType.PARENT_CHILD
            }
            fam.childrenIds.forEach { childId ->
                val child = nodeById[childId] ?: return@forEach
                val startX = block.centerX
                val startY = block.bottomY
                val endX = child.x + child.width / 2f
                val endY = child.y
                val midY = (startY + endY) / 2f
                edges += LayoutEdge(
                    type = edgeType,
                    fromFamilyId = fam.id,
                    fromPersonId = null,
                    toPersonId = childId,
                    points = listOf(
                        PointF(startX, startY),
                        PointF(startX, midY),
                        PointF(endX, midY),
                        PointF(endX, endY),
                    ),
                )
            }
        }

        return edges.sortedWith(
            compareBy<LayoutEdge> { it.type.name }
                .thenBy { it.fromFamilyId.orEmpty() }
                .thenBy { it.toPersonId.orEmpty() }
                .thenBy { it.fromPersonId.orEmpty() },
        )
    }
}
