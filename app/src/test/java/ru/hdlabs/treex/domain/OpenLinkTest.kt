package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class OpenLinkTest {
    @Test
    fun spouseAndOtherParentStayWhenOneParentIsRemoved() {
        val father = PersonEntity(id = "f", gender = "m")
        val mother = PersonEntity(id = "m", gender = "f")
        val child = PersonEntity(id = "c")
        val unions = listOf(UnionEntity("u", "f", "m", childIds = "c"))
        val links = linksAfterRemoval(father, listOf(father, mother, child), unions)
        assertTrue(links.any { it.slot == "spouse" && it.ownerId == "m" })
        assertTrue(links.any { it.slot == "father" && it.ownerId == "c" && it.anchorId == "m" })
        assertTrue(links.none { it.slot == "bridge" })
    }

    @Test
    fun intermediateParentLeavesABridgeUnderTheGrandparent() {
        val grand = PersonEntity(id = "g", gender = "m")
        val middle = PersonEntity(id = "f", gender = "m")
        val child = PersonEntity(id = "c")
        val unions = listOf(
            UnionEntity("u1", "g", null, childIds = "f"),
            UnionEntity("u2", "f", null, childIds = "c"),
        )
        val bridge = linksAfterRemoval(middle, listOf(grand, middle, child), unions).single { it.slot == "bridge" }
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
