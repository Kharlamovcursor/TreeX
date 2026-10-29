package ru.hdlabs.treex.domain

/** Shared kin-line anchor math for Compose and snapshot renderer. */
object TreeLinkGeometry {
    /** Distance from card edge to vertical anchor (fraction of card width). */
    const val EDGE_INSET = 0.22f

    fun anchorX(cardLeft: Float, cardWidth: Float, side: LinkSide): Float = when (side) {
        LinkSide.Left -> cardLeft + cardWidth * EDGE_INSET
        LinkSide.Right -> cardLeft + cardWidth * (1f - EDGE_INSET)
        LinkSide.Center -> cardLeft + cardWidth / 2f
    }

    /** Hub above children — centered on the child row (not on parents). */
    fun childrenHubX(childCenters: List<Float>, childLeft: Float, childRight: Float): Float =
        if (childCenters.size == 1) {
            childCenters.single()
        } else {
            (childLeft + childRight) / 2f
        }
}

fun assignLinkSides(cards: List<TreeCard>, spouseLinks: List<Pair<String, String>>): List<TreeCard> {
    val byId = cards.associateBy { it.id }
    val sides = HashMap<String, LinkSide>()
    spouseLinks.forEach { (a, b) ->
        val ca = byId[a] ?: return@forEach
        val cb = byId[b] ?: return@forEach
        if (ca.left <= cb.left) {
            sides[a] = LinkSide.Left
            sides[b] = LinkSide.Right
        } else {
            sides[a] = LinkSide.Right
            sides[b] = LinkSide.Left
        }
    }
    return cards.map { card -> card.copy(linkSide = sides[card.id] ?: LinkSide.Center) }
}
