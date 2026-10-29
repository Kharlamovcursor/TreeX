package ru.hdlabs.treex.data.kinship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Person

class FamilyMutationValidatorTest {

    @Test
    fun siblingUsesParentFamilyWhenAnchorIsChild() {
        val graph = graphOf(
            families = mapOf(
                "f1" to Family("f1", "dad", "mom", listOf("anchor", "other")),
            ),
        )
        val result = FamilyMutationValidator.parentFamilyForSibling(graph, "anchor")
        assertEquals(
            FamilyMutationValidator.ParentFamilyResult.UseFamily("f1"),
            result,
        )
    }

    @Test
    fun siblingNoParentFamilyWhenAnchorOrphan() {
        val graph = graphOf()
        assertEquals(
            FamilyMutationValidator.ParentFamilyResult.NoParentFamily,
            FamilyMutationValidator.parentFamilyForSibling(graph, "anchor"),
        )
    }

    @Test
    fun siblingAmbiguousWhenMultipleParentFamilies() {
        val graph = GenealogyGraph(
            persons = mapOf(
                "anchor" to person("anchor"),
                "p1" to person("p1"),
                "p2" to person("p2"),
            ),
            families = mapOf(
                "f1" to Family("f1", "p1", null, listOf("c1")),
                "f2" to Family("f2", "p2", null, listOf("c2")),
            ),
        )
        val result = FamilyMutationValidator.parentFamilyForSibling(graph, "anchor")
        assertEquals(FamilyMutationValidator.ParentFamilyResult.NoParentFamily, result)
    }

    @Test
    fun duplicateSpouseFamilyDetected() {
        val graph = graphOf(
            families = mapOf("f1" to Family("f1", "a", "b", emptyList())),
        )
        assertTrue(FamilyMutationValidator.hasDuplicateSpouseFamily(graph, "a", "b"))
    }

    private fun graphOf(
        people: Map<String, Person> = mapOf(
            "anchor" to person("anchor"),
            "other" to person("other"),
            "dad" to person("dad", Gender.MALE),
            "mom" to person("mom", Gender.FEMALE),
            "p1" to person("p1"),
            "p2" to person("p2"),
            "c1" to person("c1"),
            "c2" to person("c2"),
        ),
        families: Map<String, Family> = emptyMap(),
    ): GenealogyGraph = GenealogyGraph(people, families)

    private fun person(id: String, gender: Gender = Gender.UNKNOWN) = Person(
        id = id,
        fullName = id,
        gender = gender,
        birthDate = null,
        deathDate = null,
        avatarUrl = null,
    )
}
