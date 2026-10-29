package ru.hdlabs.treex.relatives

/** Pixel scale from relatives-tree grid units (see library docs demo, GRID_PX = 40). */
object RelativesTreeLayout {
    const val GRID_PX = 40f
    const val NODE_UNITS = 2f
    /** Same role as CSS `card-gap` between units in a row (library internal spacing). */
    const val CARD_GAP_PX = 12f
    val cardSizePx: Float get() = NODE_UNITS * GRID_PX
}
