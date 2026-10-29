package ru.hdlabs.treex.domain.layout

import ru.hdlabs.treex.domain.graph.GenealogyGraph

sealed class LayoutIssue {
    data class DuplicatePersonNode(val personId: String) : LayoutIssue()
    data class NodeOverlap(val a: String, val b: String) : LayoutIssue()
    data class ChildAboveParent(val childId: String, val familyId: String) : LayoutIssue()
    data class InvalidSpouseEdge(val familyId: String) : LayoutIssue()
    data class MissingFamilyBlock(val familyId: String) : LayoutIssue()
    data class InvalidBounds(val message: String) : LayoutIssue()
    data class OrphanFamilyNoParentEdge(val familyId: String) : LayoutIssue()
}

data class LayoutValidationResult(
    val errors: List<LayoutIssue>,
    val warnings: List<LayoutIssue>,
)

object LayoutValidator {

    fun validate(layout: TreeLayout, graph: GenealogyGraph): LayoutValidationResult {
        val errors = mutableListOf<LayoutIssue>()
        val warnings = mutableListOf<LayoutIssue>()

        val nodeIds = layout.nodes.map { it.personId }
        nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.forEach { id ->
            errors += LayoutIssue.DuplicatePersonNode(id)
        }

        val nodes = layout.nodes
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                if (overlaps(nodes[i], nodes[j])) {
                    errors += LayoutIssue.NodeOverlap(nodes[i].personId, nodes[j].personId)
                }
            }
        }

        val nodeById = layout.nodes.associateBy { it.personId }
        val blockById = layout.familyBlocks.associateBy { it.familyId }

        layout.edges.forEach { edge ->
            when (edge.type) {
                EdgeType.SPOUSE -> {
                    val block = blockById[edge.fromFamilyId ?: return@forEach] ?: return@forEach
                    val a = nodeById[edge.fromPersonId ?: return@forEach]
                    val b = nodeById[edge.toPersonId ?: return@forEach]
                    if (a == null || b == null || a.generationLevel != b.generationLevel) {
                        errors += LayoutIssue.InvalidSpouseEdge(block.familyId)
                    }
                }
                EdgeType.PARENT_CHILD, EdgeType.LEGACY_PARENT_CHILD -> {
                    val famId = edge.fromFamilyId ?: return@forEach
                    val block = blockById[famId] ?: run {
                        errors += LayoutIssue.MissingFamilyBlock(famId)
                        return@forEach
                    }
                    val child = nodeById[edge.toPersonId ?: return@forEach] ?: return@forEach
                    if (child.generationLevel != block.generationLevel + 1) {
                        errors += LayoutIssue.ChildAboveParent(child.personId, famId)
                    }
                }
            }
        }

        graph.families.values.forEach { fam ->
            val hasParent = fam.partnerAId != null || fam.partnerBId != null
            if (!hasParent && fam.childrenIds.isNotEmpty()) {
                warnings += LayoutIssue.OrphanFamilyNoParentEdge(fam.id)
            }
        }

        val b = layout.bounds
        if (b.minX > b.maxX || b.minY > b.maxY) {
            errors += LayoutIssue.InvalidBounds("min > max")
        }

        return LayoutValidationResult(errors = errors, warnings = warnings)
    }

    private fun overlaps(a: LayoutNode, b: LayoutNode): Boolean {
        val gap = LayoutConfig.blockGap / 4f
        return a.x < b.x + b.width - gap &&
            a.x + a.width - gap > b.x &&
            a.y < b.y + b.height - gap &&
            a.y + a.height - gap > b.y
    }
}
