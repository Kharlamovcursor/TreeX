package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeLinkGeometryTest {
    @Test
    fun unionHubIsMidpointBetweenSpouses() {
        val hub = TreeLinkGeometry.unionHub(
            listOf(
                TreeLinkGeometry.Box(0f, 0f, 100f, 50f),
                TreeLinkGeometry.Box(128f, 0f, 228f, 50f),
            ),
        )
        assertEquals(114f, hub.first, 0.5f)
        assertEquals(50f, hub.second, 0.5f)
    }

    @Test
    fun unionHubForSingleParentIsCardCenter() {
        val hub = TreeLinkGeometry.unionHub(
            listOf(TreeLinkGeometry.Box(40f, 10f, 208f, 118f)),
        )
        assertEquals(124f, hub.first, 0.5f)
        assertEquals(118f, hub.second, 0.5f)
    }
}
