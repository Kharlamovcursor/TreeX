package ru.hdlabs.treex.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person

class SugiyamaLayoutTest {

    @Test
    fun threeGenerationsChildrenBelowParents() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf(
                    "a" to Person("a", "a", Gender.MALE),
                    "b" to Person("b", "b", Gender.FEMALE),
                    "c" to Person("c", "c", Gender.MALE),
                    "d" to Person("d", "d", Gender.MALE),
                ),
                linkedMapOf(
                    "f1" to Family("f1", "a", "b", listOf("c", "d")),
                ),
            ),
        )
        val a = layout.nodes.first { it.personId == "a" }
        val c = layout.nodes.first { it.personId == "c" }
        assertTrue(c.y > a.y)
    }

    @Test
    fun spousesSameYAndExpectedXGap() {
        val layout = SugiyamaLayout.layout(
            GenealogyGraph(
                mapOf("a" to Person("a", "a", Gender.MALE), "b" to Person("b", "b", Gender.FEMALE)),
                linkedMapOf("f" to Family("f", "a", "b")),
            ),
        )
        val a = layout.nodes.first { it.personId == "a" }
        val b = layout.nodes.first { it.personId == "b" }
        assertEquals(a.y, b.y, 0.01f)
        assertEquals(LayoutConfig.nodeWidth + LayoutConfig.spouseGap, b.x - a.x, 0.01f)
    }

    @Test
    fun deterministicLayout() {
        val g = GenealogyGraph(
            mapOf(
                "a" to Person("a", "a", Gender.MALE),
                "b" to Person("b", "b", Gender.FEMALE),
                "c" to Person("c", "c", Gender.MALE),
            ),
            linkedMapOf("f" to Family("f", "a", "b", listOf("c"))),
        )
        assertEquals(SugiyamaLayout.layout(g), SugiyamaLayout.layout(g))
    }

    @Test
    fun integratedFixtureValidatesWithZeroErrors() {
        val graph = LayoutTestFixtures.integratedGraph()
        val layout = SugiyamaLayout.layout(graph)
        val result = LayoutValidator.validate(layout, graph)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
    }

    @Test
    fun parentChildEdgeCountMatchesFamiliesWithParents() {
        val graph = LayoutTestFixtures.integratedGraph()
        val layout = SugiyamaLayout.layout(graph)
        val expected = graph.families.values.count { fam ->
            (fam.partnerAId != null || fam.partnerBId != null) && fam.childrenIds.isNotEmpty()
        }.let { familiesWithParents ->
            graph.families.values
                .filter { fam -> fam.partnerAId != null || fam.partnerBId != null }
                .sumOf { it.childrenIds.size }
        }
        val actual = layout.edges.count {
            it.type == EdgeType.PARENT_CHILD || it.type == EdgeType.LEGACY_PARENT_CHILD
        }
        assertEquals(expected, actual)
    }
}
