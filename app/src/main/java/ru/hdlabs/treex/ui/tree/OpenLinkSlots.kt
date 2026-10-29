package ru.hdlabs.treex.ui.tree

import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.layout.LayoutConfig
import ru.hdlabs.treex.domain.layout.TreeLayout

internal object OpenLinkSlots {
    fun fromLayout(graph: GenealogyGraph, layout: TreeLayout): List<OpenLinkSlotState> =
        layout.familyBlocks.mapNotNull { block ->
            val fam = graph.family(block.familyId) ?: return@mapNotNull null
            when {
                fam.partnerAId == null && fam.partnerBId != null -> slot(
                    block, fam.partnerBId!!, RelativeKind.SPOUSE, "slot-a-${block.familyId}",
                    block.centerX - LayoutConfig.nodeWidth - LayoutConfig.spouseGap,
                    block.topY,
                )
                fam.partnerBId == null && fam.partnerAId != null -> slot(
                    block, fam.partnerAId!!, RelativeKind.SPOUSE, "slot-b-${block.familyId}",
                    block.centerX + LayoutConfig.spouseGap,
                    block.topY,
                )
                fam.partnerAId == null && fam.partnerBId == null && fam.childrenIds.isNotEmpty() -> {
                    val childId = fam.childrenIds.first()
                    slot(
                        block, childId, RelativeKind.PARENT, "slot-p-${block.familyId}",
                        block.centerX - LayoutConfig.slotWidth / 2f,
                        block.topY,
                    )
                }
                else -> null
            }
        }

    private fun slot(
        block: ru.hdlabs.treex.domain.layout.FamilyBlock,
        anchor: String,
        kind: RelativeKind,
        id: String,
        x: Float,
        y: Float,
    ) = OpenLinkSlotState(
        slotId = id,
        familyId = block.familyId,
        anchorPersonId = anchor,
        kind = kind,
        x = x,
        y = y,
        width = LayoutConfig.slotWidth,
        height = LayoutConfig.nodeHeight,
    )
}
