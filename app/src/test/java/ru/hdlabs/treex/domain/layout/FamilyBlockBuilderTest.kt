package ru.hdlabs.treex.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person

class FamilyBlockBuilderTest {

    private fun p(id: String) = Person(id, id, Gender.MALE)

    @Test
    fun oneMarriageOneBlock() {
        val g = GenealogyGraph(
            mapOf("a" to p("a"), "b" to Person("b", "b", Gender.FEMALE)),
            linkedMapOf("f" to Family("f", "a", "b")),
        )
        val levels = mapOf("a" to 0, "b" to 0)
        val blocks = FamilyBlockBuilder.build(g, levels)
        assertEquals(1, blocks.size)
        assertEquals("a", blocks[0].partnerAId)
        assertEquals("b", blocks[0].partnerBId)
    }

    @Test
    fun openLinkSinglePartner() {
        val g = GenealogyGraph(
            mapOf("b" to Person("b", "b", Gender.FEMALE)),
            linkedMapOf("f" to Family("f", null, "b", listOf("x"))),
        )
        val blocks = FamilyBlockBuilder.build(g, mapOf("b" to 0))
        assertEquals(null, blocks[0].partnerAId)
        assertEquals("b", blocks[0].partnerBId)
    }

    @Test
    fun multipleMarriagesMultipleBlocks() {
        val g = GenealogyGraph(
            mapOf("a" to p("a"), "b" to Person("b", "b", Gender.FEMALE), "d" to Person("d", "d", Gender.FEMALE)),
            linkedMapOf(
                "f1" to Family("f1", "a", "b"),
                "f2" to Family("f2", "a", "d"),
            ),
        )
        val blocks = FamilyBlockBuilder.build(g, mapOf("a" to 0, "b" to 0, "d" to 0))
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.partnerAId == "a" || it.partnerBId == "a" })
    }
}
