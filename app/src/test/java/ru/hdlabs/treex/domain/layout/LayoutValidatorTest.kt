package ru.hdlabs.treex.domain.layout

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person

class LayoutValidatorTest {

    @Test
    fun validLayoutHasNoErrors() {
        val graph = GenealogyGraph(
            mapOf(
                "a" to Person("a", "a", Gender.MALE),
                "b" to Person("b", "b", Gender.FEMALE),
                "c" to Person("c", "c", Gender.MALE),
            ),
            linkedMapOf("f" to Family("f", "a", "b", listOf("c"))),
        )
        val layout = SugiyamaLayout.layout(graph)
        val result = LayoutValidator.validate(layout, graph)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun overlapDetectedAsError() {
        val graph = GenealogyGraph(
            mapOf("a" to Person("a", "a", Gender.MALE), "b" to Person("b", "b", Gender.FEMALE)),
            linkedMapOf("f" to Family("f", "a", "b")),
        )
        val base = SugiyamaLayout.layout(graph)
        val broken = base.copy(
            nodes = base.nodes.map {
                if (it.personId == "b") it.copy(x = it.x - 200f) else it
            },
        )
        val result = LayoutValidator.validate(broken, graph)
        assertTrue(result.errors.any { it is LayoutIssue.NodeOverlap })
    }

    @Test
    fun childAboveParentIsError() {
        val graph = GenealogyGraph(
            mapOf(
                "a" to Person("a", "a", Gender.MALE),
                "c" to Person("c", "c", Gender.MALE),
            ),
            linkedMapOf("f" to Family("f", "a", null, listOf("c"))),
        )
        val layout = TreeLayout(
            nodes = listOf(
                LayoutNode("a", 0f, 200f, LayoutConfig.nodeWidth, LayoutConfig.nodeHeight, 1),
                LayoutNode("c", 0f, 0f, LayoutConfig.nodeWidth, LayoutConfig.nodeHeight, 0),
            ),
            familyBlocks = listOf(
                FamilyBlock("f", "a", null, 90f, 200f, 280f, 1),
            ),
            edges = listOf(
                LayoutEdge(
                    EdgeType.PARENT_CHILD,
                    "f",
                    null,
                    "c",
                    listOf(PointF(90f, 280f), PointF(90f, 140f), PointF(90f, 0f)),
                ),
            ),
            bounds = Bounds(0f, 0f, 200f, 300f),
        )
        val result = LayoutValidator.validate(layout, graph)
        assertTrue(result.errors.any { it is LayoutIssue.ChildAboveParent })
    }
}
