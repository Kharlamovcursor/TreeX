package ru.hdlabs.treex.domain.layout

import ru.hdlabs.treex.domain.graph.GenealogyGraph

/** Structural family block before coordinates are assigned. */
data class FamilyBlockDraft(
    val familyId: String,
    val partnerAId: String?,
    val partnerBId: String?,
    val generationLevel: Int,
)

/**
 * Builds one [FamilyBlockDraft] per [ru.hdlabs.treex.domain.graph.Family], sorted by family id.
 */
object FamilyBlockBuilder {

    fun build(graph: GenealogyGraph, levels: Map<String, Int>): List<FamilyBlockDraft> =
        graph.families.values
            .sortedBy { it.id }
            .map { fam ->
                val partnerLevels = listOfNotNull(fam.partnerAId, fam.partnerBId)
                    .mapNotNull { levels[it] }
                val level = when {
                    partnerLevels.isNotEmpty() -> partnerLevels.max()
                    fam.childrenIds.isNotEmpty() -> {
                        fam.childrenIds.mapNotNull { levels[it] }.minOrNull()?.minus(1) ?: 0
                    }
                    else -> 0
                }
                FamilyBlockDraft(
                    familyId = fam.id,
                    partnerAId = fam.partnerAId,
                    partnerBId = fam.partnerBId,
                    generationLevel = level,
                )
            }
}
