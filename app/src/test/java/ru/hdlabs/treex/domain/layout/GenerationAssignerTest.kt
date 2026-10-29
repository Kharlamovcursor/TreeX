package ru.hdlabs.treex.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person

class GenerationAssignerTest {

    private fun p(id: String) = Person(id, id, Gender.MALE)

    private fun graph(persons: List<Person>, vararg families: Family): GenealogyGraph {
        val famMap = linkedMapOf<String, Family>()
        families.forEach { famMap[it.id] = it }
        return GenealogyGraph(persons.associateBy { it.id }, famMap)
    }

    @Test
    fun simpleTreeLevels() {
        val g = graph(
            listOf(p("a"), Person("b", "b", Gender.FEMALE), p("c")),
            Family("f", "a", "b", listOf("c")),
        )
        val levels = GenerationAssigner.assign(g)
        assertEquals(0, levels["a"])
        assertEquals(0, levels["b"])
        assertEquals(1, levels["c"])
    }

    @Test
    fun partnersAlignedWhenOneAssignedLater() {
        val g = graph(
            listOf(p("a"), Person("b", "b", Gender.FEMALE), p("c")),
            Family("f", "a", "b", listOf("c")),
        )
        val levels = GenerationAssigner.assign(g)
        assertEquals(levels["a"], levels["b"])
    }

    @Test
    fun multipleMarriagesSameLevelForSpouses() {
        val g = graph(
            listOf(p("a"), Person("b", "b", Gender.FEMALE), Person("d", "d", Gender.FEMALE), p("c"), p("e")),
            Family("f1", "a", "b", listOf("c")),
            Family("f2", "a", "d", listOf("e")),
        )
        val levels = GenerationAssigner.assign(g)
        assertEquals(levels["a"], levels["b"])
        assertEquals(levels["a"], levels["d"])
        assertEquals(levels["a"]!! + 1, levels["c"])
        assertEquals(levels["a"]!! + 1, levels["e"])
    }

    @Test
    fun deterministicAcrossCalls() {
        val g = graph(
            listOf(p("a"), p("b"), p("c")),
            Family("f", "a", "b", listOf("c")),
        )
        assertEquals(GenerationAssigner.assign(g), GenerationAssigner.assign(g))
    }
}
