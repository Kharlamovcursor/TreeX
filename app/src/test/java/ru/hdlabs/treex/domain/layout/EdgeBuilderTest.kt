package ru.hdlabs.treex.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person

class EdgeBuilderTest {

    @Test
    fun spouseEdgeOnlyWhenTwoPartners() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf("a" to Person("a", "a", Gender.MALE), "b" to Person("b", "b", Gender.FEMALE)),
                linkedMapOf("f" to Family("f", "a", "b")),
            ),
        )
        val spouses = layout.edges.filter { it.type == EdgeType.SPOUSE }
        assertEquals(1, spouses.size)
    }

    @Test
    fun noSpouseEdgeForSinglePartner() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf("b" to Person("b", "b", Gender.FEMALE), "c" to Person("c", "c", Gender.MALE)),
                linkedMapOf("f" to Family("f", null, "b", listOf("c"))),
            ),
        )
        assertTrue(layout.edges.none { it.type == EdgeType.SPOUSE })
    }

    @Test
    fun oneParentChildEdgePerChildNotPerParent() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf(
                    "a" to Person("a", "a", Gender.MALE),
                    "b" to Person("b", "b", Gender.FEMALE),
                    "c" to Person("c", "c", Gender.MALE),
                ),
                linkedMapOf("f" to Family("f", "a", "b", listOf("c"))),
            ),
        )
        val childEdges = layout.edges.filter {
            it.type == EdgeType.PARENT_CHILD && it.toPersonId == "c"
        }
        assertEquals(1, childEdges.size)
    }

    @Test
    fun parentChildPointsOrthogonal() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf(
                    "a" to Person("a", "a", Gender.MALE),
                    "b" to Person("b", "b", Gender.FEMALE),
                    "c" to Person("c", "c", Gender.MALE),
                ),
                linkedMapOf("f" to Family("f", "a", "b", listOf("c"))),
            ),
        )
        val edge = layout.edges.first { it.type == EdgeType.PARENT_CHILD }
        assertTrue(edge.points.size >= 3)
        for (i in 0 until edge.points.size - 1) {
            val p1 = edge.points[i]
            val p2 = edge.points[i + 1]
            assertTrue(p1.x == p2.x || p1.y == p2.y)
        }
    }

    @Test
    fun legacyFamilyUsesLegacyEdgeType() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf("p" to Person("p", "p", Gender.MALE), "c" to Person("c", "c", Gender.MALE)),
                linkedMapOf("legacy-c" to Family("legacy-c", "p", null, listOf("c"))),
            ),
        )
        assertTrue(layout.edges.any { it.type == EdgeType.LEGACY_PARENT_CHILD })
    }
}
