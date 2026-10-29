package ru.hdlabs.treex.domain.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class GenealogyGraphMapperTest {

    private val validator = GenealogyValidator()

    @Test
    fun mapsEntitiesPreservingStringIds() {
        val people = listOf(
            PersonEntity(id = "uuid-1", firstName = "Ivan", lastName = "Ivanov", gender = "m"),
            PersonEntity(id = "uuid-2", firstName = "Anna", lastName = "Ivanova", gender = "f"),
        )
        val unions = listOf(UnionEntity("u-1", "uuid-1", "uuid-2", childIds = ""))
        val graph = GenealogyGraphMapper.fromEntities(people, unions)
        assertEquals(setOf("uuid-1", "uuid-2"), graph.persons.keys)
        assertEquals("u-1", graph.families.keys.single())
    }

    @Test
    fun legacyParentsWithoutUnionCreatesSyntheticFamily() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m", firstName = "Dad"),
            PersonEntity(id = "m", gender = "f", firstName = "Mom"),
            PersonEntity(id = "c", gender = "m", firstName = "Son", fatherId = "f", motherId = "m"),
        )
        val graph = GenealogyGraphMapper.fromEntities(people, emptyList())
        val legacyId = GenealogyGraphMapper.legacyFamilyId("c")
        assertEquals(legacyId, graph.familyWhereChild("c")?.id)
        assertEquals(setOf("f", "m"), graph.parentsOf("c").map { it.id }.toSet())
    }

    @Test
    fun emptyProjectYieldsEmptyGraph() {
        val graph = GenealogyGraphMapper.fromEntities(emptyList(), emptyList())
        assertTrue(graph.persons.isEmpty())
        assertTrue(graph.families.isEmpty())
    }

    @Test
    fun childIdsParsingTrimsAndDropsBlanksAndDedupes() {
        val parsed = GenealogyGraphMapper.parseChildIds("u", " , a , , b , a ")
        assertEquals(listOf("a", "b"), parsed)
        val people = listOf(
            PersonEntity(id = "p", gender = "m", firstName = "P"),
            PersonEntity(id = "a", gender = "m", firstName = "A"),
            PersonEntity(id = "b", gender = "f", firstName = "B"),
        )
        val unions = listOf(
            UnionEntity("u", "p", null, childIds = " , a , , b , a "),
        )
        val graph = GenealogyGraphMapper.fromEntities(people, unions)
        assertEquals(listOf("a", "b"), graph.family("u")?.childrenIds)
        val read = validator.validate(graph, ValidationMode.Read)
        assertTrue(read.errors.isEmpty())
    }
}
