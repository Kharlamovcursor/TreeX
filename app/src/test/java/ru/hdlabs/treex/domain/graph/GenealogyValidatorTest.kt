package ru.hdlabs.treex.domain.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenealogyValidatorTest {

    private val validator = GenealogyValidator()

    private fun p(id: String) = Person(id, id, Gender.MALE)

    private fun graph(persons: List<Person>, vararg families: Family): GenealogyGraph {
        val famMap = linkedMapOf<String, Family>()
        families.forEach { famMap[it.id] = it }
        return GenealogyGraph(persons.associateBy { it.id }, famMap)
    }

    @Test
    fun cycleDetectedIsErrorInReadAndSave() {
        val a = p("a")
        val b = p("b")
        val c = p("c")
        val g = graph(
            listOf(a, b, c),
            Family("f1", "a", "b", listOf("c")),
            Family("f2", "c", null, listOf("a")),
        )
        val read = validator.validate(g, ValidationMode.Read)
        assertTrue(read.errors.any { it is ValidationIssue.CycleDetected })
        assertTrue(read.warnings.isEmpty())
    }

    @Test
    fun selfAsParentIsError() {
        val a = p("a")
        val g = graph(listOf(a), Family("f1", "a", null, listOf("a")))
        val result = validator.validate(g, ValidationMode.Read)
        assertTrue(result.errors.any { it is ValidationIssue.SelfAsParent })
    }

    @Test
    fun childInMultipleFamiliesWarningOnReadErrorOnSave() {
        val c = p("c")
        val g = graph(
            listOf(c, p("a"), p("b"), p("x")),
            Family("f1", "a", null, listOf("c")),
            Family("f2", "x", null, listOf("c")),
        )
        val read = validator.validate(g, ValidationMode.Read)
        assertTrue(read.errors.isEmpty())
        assertTrue(read.warnings.any { it is ValidationIssue.ChildInMultipleFamilies })
        val save = validator.validate(g, ValidationMode.Save)
        assertTrue(save.errors.any { it is ValidationIssue.ChildInMultipleFamilies })
    }

    @Test
    fun danglingRefsWarningOnReadErrorOnSave() {
        val g = graph(
            listOf(p("p")),
            Family("f1", "p", "ghost", listOf("missing")),
        )
        val read = validator.validate(g, ValidationMode.Read)
        assertTrue(read.errors.isEmpty())
        assertTrue(read.warnings.size >= 2)
        val save = validator.validate(g, ValidationMode.Save)
        assertTrue(save.errors.size >= 2)
    }

    @Test
    fun assertSaveableThrowsOnErrors() {
        val g = graph(listOf(p("p")), Family("f1", "p", "ghost"))
        try {
            validator.assertSaveable(g)
            error("expected SaveBlockedException")
        } catch (e: SaveBlockedException) {
            assertTrue(e.issues.isNotEmpty())
        }
    }

    @Test
    fun validThreeGenerationsTwoMarriagesFiveChildren() {
        val people = mutableListOf<Person>()
        fun add(id: String, gender: Gender = Gender.MALE) = Person(id, id, gender).also { people += it }
        add("gp1")
        add("gp2", Gender.FEMALE)
        add("p1")
        add("p1s", Gender.FEMALE)
        add("p2")
        add("p2s", Gender.FEMALE)
        (1..5).forEach { add("c$it") }
        val g = graph(
            people,
            Family("gpf", "gp1", "gp2", listOf("p1", "p2")),
            Family("f1", "p1", "p1s", listOf("c1", "c2", "c3")),
            Family("f2", "p2", "p2s", listOf("c4", "c5")),
        )
        val result = validator.validate(g, ValidationMode.Save)
        assertEquals(emptyList<ValidationIssue>(), result.errors)
        assertEquals(emptyList<ValidationIssue>(), result.warnings)
    }

    @Test
    fun duplicateChildInFamilyIsError() {
        val g = graph(listOf(p("p"), p("c")), Family("f1", "p", null, listOf("c", "c")))
        val result = validator.validate(g, ValidationMode.Read)
        assertTrue(result.errors.any { it is ValidationIssue.DuplicateChildInFamily })
    }
}
