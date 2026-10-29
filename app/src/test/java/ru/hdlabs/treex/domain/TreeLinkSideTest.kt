package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class TreeLinkSideTest {
    @Test
    fun spouseOnLeftGetsLeftLinkSide() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "w", gender = "f", birthDate = "1991-01-01"),
        )
        val placed = TreeLayout.place(people, listOf(UnionEntity("u", "h", "w")), homePersonId = "h")
        val card = placed.cards.associateBy { it.id }
        assertEquals(LinkSide.Left, card.getValue("h").linkSide)
        assertEquals(LinkSide.Right, card.getValue("w").linkSide)
    }

    @Test
    fun singleCardUsesCenterAnchor() {
        val people = listOf(PersonEntity(id = "me", gender = "m", birthDate = "1990-01-01"))
        val placed = TreeLayout.place(people, emptyList())
        assertEquals(LinkSide.Center, placed.cards.single().linkSide)
    }
}
