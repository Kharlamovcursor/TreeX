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
        val child = PersonEntity(id = "c", fatherId = "f", motherId = "m")
        val links = linksAfterRemoval(father, listOf(father, mother, child), listOf(UnionEntity("u", "f", "m")))
        assertTrue(links.any { it.slot == "spouse" && it.ownerId == "m" })
        assertTrue(links.any { it.slot == "father" && it.ownerId == "c" && it.anchorId == "m" })
        assertTrue(links.none { it.slot == "bridge" })
    }

    @Test
    fun intermediateParentLeavesABridgeUnderTheGrandparent() {
        val grand = PersonEntity(id = "g", gender = "m")
        val middle = PersonEntity(id = "f", gender = "m", fatherId = "g")
        val child = PersonEntity(id = "c", fatherId = "f")
        val bridge = linksAfterRemoval(middle, listOf(grand, middle, child), emptyList()).single { it.slot == "bridge" }
        assertEquals("g", bridge.anchorId)
        assertEquals(listOf("c"), bridge.childIds)
        assertTrue(bridge.anchorIsParent)
    }

    @Test
    fun bridgeAttachesTheChosenPersonAsTheNewParent() {
        val grand = PersonEntity(id = "g", gender = "m")
        val aunt = PersonEntity(id = "a", gender = "f")
        val child = PersonEntity(id = "c")
        val link = OpenLink("k", "bridge", "g", "g", listOf("c"), anchorIsParent = true)
        val updates = reattachPeople(link, aunt, listOf(grand, aunt, child)).associateBy { it.id }
        assertEquals("g", updates.getValue("a").fatherId)
        assertEquals("a", updates.getValue("c").motherId)
    }
}
