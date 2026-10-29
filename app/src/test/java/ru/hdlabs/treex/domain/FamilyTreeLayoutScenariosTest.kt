package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.abs

/**
 * Checklist scenarios 1–8 and 10 from the layout specification.
 * Pan/zoom (9) and pulse performance (11) are covered by UI, not unit tests.
 */
class FamilyTreeLayoutScenariosTest {
    @Test
    fun scenario1_initialCouple() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "w", gender = "f", birthDate = "1991-01-01"),
        )
        val unions = listOf(UnionEntity("u", "h", "w"))
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("h").top, card.getValue("w").top, 0.5f)
        assertEquals(28f, abs(card.getValue("w").left - card.getValue("h").left) - TreeLayout.CARD_W, 0.5f)
        assertEquals(1, placed.spouseLinks.size)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario2_oneChildCenteredUnderCouple() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "w", gender = "f", birthDate = "1991-01-01"),
            PersonEntity(id = "c", gender = "m", birthDate = "2015-01-01", fatherId = "h", motherId = "w"),
        )
        val unions = listOf(UnionEntity("u", "h", "w"))
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        val coupleLeft = minOf(card.getValue("h").left, card.getValue("w").left)
        val coupleRight = maxOf(card.getValue("h").left, card.getValue("w").left) + TreeLayout.CARD_W
        val childCenter = card.getValue("c").left + TreeLayout.CARD_W / 2f
        assertTrue(childCenter in coupleLeft..coupleRight)
        assertTrue(card.getValue("c").top > card.getValue("h").top)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario3_threeChildrenCenteredRow() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "w", gender = "f", birthDate = "1991-01-01"),
            PersonEntity(id = "c1", gender = "m", birthDate = "2010-01-01", fatherId = "h", motherId = "w"),
            PersonEntity(id = "c2", gender = "f", birthDate = "2012-01-01", fatherId = "h", motherId = "w"),
            PersonEntity(id = "c3", gender = "m", birthDate = "2014-01-01", fatherId = "h", motherId = "w"),
        )
        val unions = listOf(UnionEntity("u", "h", "w"))
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        val coupleCenter = (card.getValue("h").left + card.getValue("w").left + TreeLayout.CARD_W) / 2f
        val rowCenter = (card.getValue("c1").left + card.getValue("c3").left + TreeLayout.CARD_W) / 2f
        assertEquals(coupleCenter, rowCenter, 2f)
        assertEquals(card.getValue("c1").top, card.getValue("c2").top, 0.5f)
        assertEquals(card.getValue("c2").top, card.getValue("c3").top, 0.5f)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario4_parentAboveChildAddedFirst() {
        val people = listOf(
            PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01"),
            PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01"),
        )
        val placed = TreeLayout.place(
            listOf(
                PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01", fatherId = "f"),
                PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01"),
            ),
            emptyList(),
        )
        val card = placed.cards.associateBy { it.id }
        assertTrue(card.getValue("f").top < card.getValue("c").top)
        val parentCenter = card.getValue("f").left + TreeLayout.CARD_W / 2f
        val childCenter = card.getValue("c").left + TreeLayout.CARD_W / 2f
        assertEquals(parentCenter, childCenter, 2f)
    }

    @Test
    fun scenario5_bothParentsAboveChild() {
        val people = listOf(
            PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01"),
            PersonEntity(id = "m", gender = "f", birthDate = "1972-01-01"),
        )
        val unions = listOf(UnionEntity("u", "f", "m"))
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("f").top, card.getValue("m").top, 0.5f)
        assertTrue(card.getValue("c").top > card.getValue("f").top)
        val parentsCenter = (card.getValue("f").left + card.getValue("m").left + TreeLayout.CARD_W) / 2f
        val childCenter = card.getValue("c").left + TreeLayout.CARD_W / 2f
        assertEquals(parentsCenter, childCenter, 2f)
    }

    @Test
    fun scenario6_siblingsShareGenerationAndParents() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01"),
            PersonEntity(id = "m", gender = "f", birthDate = "1972-01-01"),
            PersonEntity(id = "a", gender = "m", birthDate = "1995-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "b", gender = "f", birthDate = "1998-01-01", fatherId = "f", motherId = "m"),
        )
        val unions = listOf(UnionEntity("u", "f", "m"))
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("a").top, card.getValue("b").top, 0.5f)
        assertTrue(card.getValue("a").top > card.getValue("f").top)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario7_childSpouseAndGrandchildren() {
        val people = listOf(
            PersonEntity(id = "gf", gender = "m", birthDate = "1940-01-01"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01"),
            PersonEntity(id = "f", gender = "m", birthDate = "1965-01-01", fatherId = "gf", motherId = "gm"),
            PersonEntity(id = "m", gender = "f", birthDate = "1967-01-01"),
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01"),
            PersonEntity(id = "kid", gender = "m", birthDate = "2015-01-01", fatherId = "self", motherId = "wife"),
        )
        val unions = listOf(
            UnionEntity("1", "gf", "gm"),
            UnionEntity("2", "f", "m"),
            UnionEntity("3", "self", "wife"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("self").top, card.getValue("wife").top, 0.5f)
        assertTrue(card.getValue("kid").top > card.getValue("self").top)
        val coupleLeft = minOf(card.getValue("self").left, card.getValue("wife").left)
        val coupleRight = maxOf(card.getValue("self").left, card.getValue("wife").left) + TreeLayout.CARD_W
        val kidCenter = card.getValue("kid").left + TreeLayout.CARD_W / 2f
        assertTrue(kidCenter in coupleLeft..coupleRight)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario8_orderOfInsertionDoesNotChangeGeometry() {
        val peopleA = listOf(
            PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01"),
            PersonEntity(id = "m", gender = "f", birthDate = "1972-01-01"),
            PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01", fatherId = "f", motherId = "m"),
        )
        val peopleB = peopleA.reversed()
        val unions = listOf(UnionEntity("u", "f", "m"))
        val layoutA = TreeLayout.place(peopleA, unions).cards.sortedBy { it.id }
        val layoutB = TreeLayout.place(peopleB, unions).cards.sortedBy { it.id }
        layoutA.zip(layoutB).forEach { (a, b) ->
            assertEquals(a.id, b.id)
            assertEquals(a.left, b.left, 0.5f)
            assertEquals(a.top, b.top, 0.5f)
        }
    }

    @Test
    fun scenario10_noDuplicateCardsOrSpuriousParentLinks() {
        val people = listOf(
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01", fatherId = "wf", motherId = "wm"),
            PersonEntity(id = "wf", gender = "m", birthDate = "1966-01-01"),
            PersonEntity(id = "wm", gender = "f", birthDate = "1968-01-01"),
        )
        val unions = listOf(
            UnionEntity("1", "self", "wife"),
            UnionEntity("2", "wf", "wm"),
        )
        val placed = TreeLayout.place(people, unions)
        assertEquals(people.size, placed.cards.size)
        assertEquals(people.size, placed.cards.map { it.id }.distinct().size)
        assertTrue(placed.childGroups.none { "self" in it.childIds && "wm" in it.parentIds })
        cardsDoNotOverlap(placed.cards)
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
