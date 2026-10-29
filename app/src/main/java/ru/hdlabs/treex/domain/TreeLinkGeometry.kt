package ru.hdlabs.treex.domain

/** Kin-line anchor math: union hub between spouses, then down to children. */
object TreeLinkGeometry {
    /** Distance from card edge to side anchor (spouse bonds, remarriage rays). */
    const val EDGE_INSET = 0.22f

    fun anchorX(cardLeft: Float, cardWidth: Float, side: LinkSide): Float = when (side) {
        LinkSide.Left -> cardLeft + cardWidth * EDGE_INSET
        LinkSide.Right -> cardLeft + cardWidth * (1f - EDGE_INSET)
        LinkSide.Center -> cardLeft + cardWidth / 2f
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /**
     * Union point (точка союза): center between spouses on the bottom of the couple row.
     * One parent → center bottom of that card.
     */
    fun unionHub(parentBoxes: List<Box>): Pair<Float, Float> {
        require(parentBoxes.isNotEmpty())
        val sorted = parentBoxes.sortedBy { it.left }
        val bottom = sorted.maxOf { it.bottom }
        val hubX = when (sorted.size) {
            1 -> sorted.single().centerX
            else -> (sorted.first().centerX + sorted.last().centerX) / 2f
        }
        return hubX to bottom
    }

    /** Vertical bus Y between union point and child row (orthogonal stem). */
    fun unionStemY(unionY: Float, childTop: Float, minDrop: Float): Float {
        if (childTop <= unionY + minDrop) return unionY + minDrop
        return unionY + (childTop - unionY) * 0.45f
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
