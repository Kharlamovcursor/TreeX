package ru.hdlabs.treex.ui.tree

/**
 * UI placeholder for a missing partner/parent slot on the tree canvas (not part of [GenealogyGraph]).
 */
data class OpenLinkSlotState(
    val slotId: String,
    val familyId: String,
    val anchorPersonId: String,
    val kind: RelativeKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
