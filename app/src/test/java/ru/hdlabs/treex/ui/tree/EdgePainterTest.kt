package ru.hdlabs.treex.ui.tree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.layout.EdgeType
import ru.hdlabs.treex.domain.layout.FamilyBlock
import ru.hdlabs.treex.domain.layout.LayoutConfig
import ru.hdlabs.treex.domain.layout.LayoutNode

class EdgePainterTest {

    @Test
    fun spousePointsHorizontalMidline() {
        val a = LayoutNode("a", 0f, 10f, 180f, 80f, 0)
        val b = LayoutNode("b", 200f, 10f, 180f, 80f, 0)
        val pts = EdgePainter.spousePoints(a, b)
        assertEquals(2, pts.size)
        assertEquals(a.y + a.height / 2f, pts[0].y, 0.01f)
        assertEquals(pts[0].y, pts[1].y, 0.01f)
    }

    @Test
    fun parentChildPointsOrthogonalWithThreePlusPoints() {
        val fam = FamilyBlock("f", "a", "b", 100f, 0f, 80f, 0)
        val child = LayoutNode("c", 50f, 200f, LayoutConfig.nodeWidth, LayoutConfig.nodeHeight, 1)
        val pts = EdgePainter.parentChildPoints(fam, child)
        assertTrue(pts.size >= 3)
        for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            assertTrue(p1.x == p2.x || p1.y == p2.y)
        }
    }

    @Test
    fun legacyTypeDetected() {
        assertTrue(EdgePainter.isLegacy(EdgeType.LEGACY_PARENT_CHILD))
    }
}
