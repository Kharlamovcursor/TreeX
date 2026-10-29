package ru.hdlabs.treex.domain.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenealogyGraphTest {

    private fun p(id: String) = Person(id, id, Gender.MALE)

    private fun graph(persons: List<Person>, vararg families: Family): GenealogyGraph {
        val famMap = linkedMapOf<String, Family>()
        families.forEach { famMap[it.id] = it }
        return GenealogyGraph(persons.associateBy { it.id }, famMap)
    }

    @Test
    fun parentsOfReturnsBothPartners() {
        val a = p("a")
        val b = Person("b", "b", Gender.FEMALE)
        val c = p("c")
        val g = graph(
            listOf(a, b, c),
            Family("f1", "a", "b", listOf("c")),
        )
        assertEquals(setOf("a", "b"), g.parentsOf("c").map { it.id }.toSet())
    }

    @Test
    fun parentsOfSinglePartnerWhenOtherNull() {
        val b = Person("b", "b", Gender.FEMALE)
        val c = p("c")
        val g = graph(listOf(b, c), Family("f1", null, "b", listOf("c")))
        assertEquals(listOf("b"), g.parentsOf("c").map { it.id })
    }

    @Test
    fun childrenOfPreservesChildrenIdsOrder() {
        val parent = p("p")
        val c1 = p("c1")
        val c2 = p("c2")
        val g = graph(
            listOf(parent, c1, c2),
            Family("f1", "p", null, listOf("c1", "c2")),
        )
        assertEquals(listOf("c1", "c2"), g.childrenOf("p").map { it.id })
    }

    @Test
    fun spousesOfMultipleMarriages() {
        val hub = p("h")
        val w1 = Person("w1", "w1", Gender.FEMALE)
        val w2 = Person("w2", "w2", Gender.FEMALE)
        val g = graph(
            listOf(hub, w1, w2),
            Family("f1", "h", "w1"),
            Family("f2", "h", "w2"),
        )
        assertEquals(setOf("w1", "w2"), g.spousesOf("h").map { it.id }.toSet())
    }

    @Test
    fun siblingsExcludeSelf() {
        val c1 = p("c1")
        val c2 = p("c2")
        val c3 = p("c3")
        val g = graph(
            listOf(c1, c2, c3, p("a"), Person("b", "b", Gender.FEMALE)),
            Family("f1", "a", "b", listOf("c1", "c2", "c3")),
        )
        assertEquals(setOf("c2", "c3"), g.siblingsOf("c1").map { it.id }.toSet())
        assertTrue(g.siblingsOf("c3").isEmpty().not())
        assertEquals(0, g.siblingsOf("c3").count { it.id == "c3" })
    }

    @Test
    fun onlyChildHasNoSiblings() {
        val g = graph(
            listOf(p("only"), p("a"), Person("b", "b", Gender.FEMALE)),
            Family("f1", "a", "b", listOf("only")),
        )
        assertTrue(g.siblingsOf("only").isEmpty())
    }

    @Test
    fun orphanFamilyBothPartnersNull() {
        val c = p("c")
        val g = graph(listOf(c), Family("orphan", null, null, listOf("c")))
        assertTrue(g.parentsOf("c").isEmpty())
        assertEquals("orphan", g.familyWhereChild("c")?.id)
    }

    @Test
    fun danglingChildIdDoesNotCrashChildrenOf() {
        val g = graph(
            listOf(p("p")),
            Family("f1", "p", null, listOf("missing")),
        )
        assertTrue(g.childrenOf("p").isEmpty())
    }

    @Test
    fun duplicateChildrenIdsReturnDistinctPersons() {
        val c = p("c")
        val g = graph(
            listOf(p("p"), c),
            Family("f1", "p", null, listOf("c", "c")),
        )
        assertEquals(listOf("c"), g.childrenOf("p").map { it.id })
    }

    @Test
    fun familyWhereChildReturnsFirstFamilyInOrder() {
        val c = p("c")
        val g = graph(
            listOf(c, p("a"), p("b")),
            Family("first", "a", null, listOf("c")),
            Family("second", "b", null, listOf("c")),
        )
        assertEquals("first", g.familyWhereChild("c")?.id)
    }

    @Test
    fun personLookup() {
        val g = graph(listOf(p("x")), Family("f", "x", null))
        assertEquals("x", g.person("x")?.id)
        assertNull(g.person("nope"))
    }
}
