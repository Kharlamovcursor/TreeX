package ru.hdlabs.treex.relatives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class RelativesTreeConverterTest {
    @Test
    fun spousesAreBidirectionalInGraph() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m", firstName = "Ivan"),
            PersonEntity(id = "w", gender = "f", firstName = "Anna"),
        )
        val unions = listOf(UnionEntity("u", "h", "w"))
        val nodes = RelativesTreeConverter.toGraphNodes(people, unions)
        val husband = nodes.first { it.id == "h" }
        val wife = nodes.first { it.id == "w" }
        assertEquals(listOf("w"), husband.spouses.map { it.id })
        assertEquals(listOf("h"), wife.spouses.map { it.id })
    }

    @Test
    fun childLinksBothParentsInGraph() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m", firstName = "Dad"),
            PersonEntity(id = "m", gender = "f", firstName = "Mom"),
            PersonEntity(id = "c", gender = "m", firstName = "Son", fatherId = "f", motherId = "m"),
        )
        val unions = listOf(UnionEntity("u", "f", "m", childIds = "c"))
        val child = RelativesTreeConverter.toGraphNodes(people, unions).first { it.id == "c" }
        assertEquals(setOf("f", "m"), child.parents.map { it.id }.toSet())
        assertTrue(child.parents.all { it.type == "blood" })
    }
}
