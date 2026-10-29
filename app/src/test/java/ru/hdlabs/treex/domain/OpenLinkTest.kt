package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

class OpenLinkTest {
    @Test
    fun spouseAndOtherParentStayWhenOneParentIsRemoved() {
        val father = PersonEntity(id = "f", gender = "m")
        val mother = PersonEntity(id = "m", gender = "f")
        val child = PersonEntity(id = "c")
        val people = listOf(father, mother, child)
        val graph = GenealogyGraphMapper.fromFamilies(
            people,
            listOf(FamilyEntity("u", "f", "m")),
            listOf(FamilyChildEntity("u", "c")),
        )
        val links = linksAfterRemoval(father, people, graph)
        assertTrue(links.any { it.slot == "spouse" && it.ownerId == "m" })
        assertTrue(links.any { it.slot == "father" && it.ownerId == "c" && it.anchorId == "m" })
        assertTrue(links.none { it.slot == "bridge" })
    }

    @Test
    fun intermediateParentLeavesABridgeUnderTheGrandparent() {
        val grand = PersonEntity(id = "g", gender = "m")
        val middle = PersonEntity(id = "f", gender = "m")
        val child = PersonEntity(id = "c")
        val people = listOf(grand, middle, child)
        val graph = GenealogyGraphMapper.fromFamilies(
            people,
            listOf(
                FamilyEntity("u1", "g", null),
                FamilyEntity("u2", "f", null),
            ),
            listOf(
                FamilyChildEntity("u1", "f"),
                FamilyChildEntity("u2", "c"),
            ),
        )
        val bridge = linksAfterRemoval(middle, people, graph).single { it.slot == "bridge" }
        assertEquals("g", bridge.anchorId)
        assertEquals(listOf("c"), bridge.childIds)
        assertTrue(bridge.anchorIsParent)
    }

    @Test
    fun reattachPeopleIsHandledByKinshipInRepository() {
        val grand = PersonEntity(id = "g", gender = "m")
        val aunt = PersonEntity(id = "a", gender = "f")
        val child = PersonEntity(id = "c")
        val link = OpenLink("k", "bridge", "g", "g", listOf("c"), anchorIsParent = true)
        assertTrue(reattachPeople(link, aunt, listOf(grand, aunt, child)).isEmpty())
    }
}
