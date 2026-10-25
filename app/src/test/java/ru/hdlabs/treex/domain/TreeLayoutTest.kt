package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.OpenLink
import kotlin.math.abs
import kotlin.math.min

class TreeLayoutTest {
    @Test
    fun childSitsUnderTheCoupleAndSpousesShareARow() {
        val people = listOf(
            PersonEntity(id = "g", gender = "m", birthDate = "1940-01-01", lastName = "Дед"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01", lastName = "Бабушка"),
            PersonEntity(id = "f", gender = "m", birthDate = "1965-01-01", fatherId = "g", motherId = "gm"),
            PersonEntity(id = "m", gender = "f", birthDate = "1967-01-01"),
            PersonEntity(id = "c", gender = "m", birthDate = "1990-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "u", gender = "m", birthDate = "1968-01-01", fatherId = "g", motherId = "gm"),
        )
        val unions = listOf(
            UnionEntity("1", "g", "gm"),
            UnionEntity("2", "f", "m"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(people.size, card.size)
        assertEquals(card.getValue("g").top, card.getValue("gm").top, 0.5f)
        assertEquals(card.getValue("f").top, card.getValue("m").top, 0.5f)
        assertEquals(card.getValue("u").top, card.getValue("f").top, 0.5f)
        assertTrue(card.getValue("c").top > card.getValue("f").top)
        assertTrue(card.getValue("f").top > card.getValue("g").top)

        val coupleLeft = min(card.getValue("f").left, card.getValue("m").left)
        val coupleRight = maxOf(card.getValue("f").left, card.getValue("m").left) + TreeLayout.CARD_W
        val childCenter = card.getValue("c").left + TreeLayout.CARD_W / 2f
        assertTrue(childCenter in coupleLeft..coupleRight)

        val spouseGap = abs(card.getValue("gm").left - card.getValue("g").left) - TreeLayout.CARD_W
        assertEquals(28f, spouseGap, 0.5f)
        assertTrue(placed.spouseLinks.any { it.first == "g" || it.second == "g" })
        assertTrue(placed.childGroups.any { "c" in it.childIds && "f" in it.parentIds && "m" in it.parentIds })
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun longNameGetsATallerCard() {
        val short = TreeLayout.cardHeight(PersonEntity(id = "a", firstName = "Иван"))
        val long = TreeLayout.cardHeight(
            PersonEntity(
                id = "b",
                lastName = "Александров",
                birthLastName = "Петров",
                firstName = "Александр",
                middleName = "Александрович",
            ),
        )
        assertTrue(long > short)
    }

    @Test
    fun bridgeKeepsDescendantBelowTheSurvivingPerson() {
        val placed = TreeLayout.place(
            listOf(
                PersonEntity(id = "g", gender = "m", lastName = "Дед"),
                PersonEntity(id = "c", gender = "f", lastName = "Внучка"),
            ),
            emptyList(),
            listOf(OpenLink("k", "bridge", "g", "g", listOf("c"), anchorIsParent = true)),
        )
        val card = placed.cards.associateBy { it.id }
        assertTrue(card.getValue("c").top > card.getValue("g").top)
        assertEquals(1, placed.knots.size)
        assertTrue(placed.childGroups.any { it.open && "c" in it.childIds })
    }

    private fun cardsDoNotOverlap(cards: List<TreeCard>) {
        cards.forEach { first ->
            cards.filter { it.id != first.id }.forEach { second ->
                val separated = first.left + TreeLayout.CARD_W <= second.left + 0.1f ||
                    second.left + TreeLayout.CARD_W <= first.left + 0.1f ||
                    first.top + first.height <= second.top + 0.1f ||
                    second.top + second.height <= first.top + 0.1f
                assertTrue("${first.id} overlaps ${second.id}", separated)
            }
        }
    }
}
