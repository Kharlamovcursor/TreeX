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
        assertEquals(card.getValue("f").height, card.getValue("m").height, 0.5f)
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
        val placed = TreeLayout.place(
            listOf(
                PersonEntity(id = "h", gender = "m", firstName = "Иван"),
                PersonEntity(
                    id = "w",
                    gender = "f",
                    lastName = "Александрова",
                    firstName = "Александра",
                    middleName = "Александровна",
                ),
            ),
            listOf(UnionEntity("u", "h", "w")),
        )
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("h").height, card.getValue("w").height, 0.5f)
    }

    @Test
    fun parentsSitAboveChildWhenChildWasAddedFirst() {
        val people = listOf(
            PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01", lastName = "Сын"),
            PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01", lastName = "Отец"),
            PersonEntity(id = "m", gender = "f", birthDate = "1972-01-01", lastName = "Мать"),
        )
        val unions = listOf(UnionEntity("u", "f", "m"))
        val placed = TreeLayout.place(
            people.map {
                when (it.id) {
                    "c" -> it.copy(fatherId = "f", motherId = "m")
                    else -> it
                }
            },
            unions,
        )
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("f").top, card.getValue("m").top, 0.5f)
        assertTrue(card.getValue("c").top > card.getValue("f").top)
        assertTrue(card.getValue("f").top < card.getValue("c").top)
    }

    @Test
    fun spouseStaysOnSameRowWhenInLawParentAdded() {
        val people = listOf(
            PersonEntity(id = "gf", gender = "m", birthDate = "1940-01-01", lastName = "Дед"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01", lastName = "Бабушка"),
            PersonEntity(id = "f", gender = "m", birthDate = "1965-01-01", fatherId = "gf", motherId = "gm"),
            PersonEntity(id = "m", gender = "f", birthDate = "1967-01-01"),
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01", motherId = "wm"),
            PersonEntity(id = "wm", gender = "f", birthDate = "1968-01-01", lastName = "Тёща"),
        )
        val unions = listOf(
            UnionEntity("1", "gf", "gm"),
            UnionEntity("2", "f", "m"),
            UnionEntity("3", "self", "wife"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(people.size, card.size)
        assertEquals(card.getValue("self").top, card.getValue("wife").top, 0.5f)
        assertTrue(card.getValue("wm").top < card.getValue("wife").top)
        assertTrue(card.getValue("f").top < card.getValue("self").top)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun inLawParentsLinkToDaughterWithoutSharedChildHouse() {
        val people = listOf(
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01", fatherId = "wf", motherId = "wm"),
            PersonEntity(id = "wf", gender = "m", birthDate = "1966-01-01", lastName = "Тесть"),
            PersonEntity(id = "wm", gender = "f", birthDate = "1968-01-01", lastName = "Тёща"),
        )
        val unions = listOf(
            UnionEntity("1", "self", "wife"),
            UnionEntity("2", "wf", "wm"),
        )
        val placed = TreeLayout.place(people, unions)
        assertTrue(
            placed.childGroups.any { group ->
                "wife" in group.childIds && "wf" in group.parentIds && "wm" in group.parentIds
            },
        )
        assertTrue(placed.spouseLinks.any { it.first == "wf" && it.second == "wm" || it.first == "wm" && it.second == "wf" })
    }

    @Test
    fun bothSpousesParentsSplitAboveEachColumn() {
        val people = listOf(
            PersonEntity(id = "gf", gender = "m", birthDate = "1940-01-01"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01"),
            PersonEntity(id = "f", gender = "m", birthDate = "1965-01-01", fatherId = "gf", motherId = "gm"),
            PersonEntity(id = "m", gender = "f", birthDate = "1967-01-01"),
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01", fatherId = "wf", motherId = "wm"),
            PersonEntity(id = "wf", gender = "m", birthDate = "1966-01-01", lastName = "Тесть"),
            PersonEntity(id = "wm", gender = "f", birthDate = "1968-01-01", lastName = "Тёща"),
        )
        val unions = listOf(
            UnionEntity("1", "gf", "gm"),
            UnionEntity("2", "f", "m"),
            UnionEntity("3", "self", "wife"),
            UnionEntity("4", "wf", "wm"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(people.size, card.size)
        assertEquals(card.getValue("self").top, card.getValue("wife").top, 0.5f)
        val selfCenter = card.getValue("self").left + TreeLayout.CARD_W / 2f
        val wifeCenter = card.getValue("wife").left + TreeLayout.CARD_W / 2f
        val fatherCenter = card.getValue("f").left + TreeLayout.CARD_W / 2f
        val wmCenter = card.getValue("wm").left + TreeLayout.CARD_W / 2f
        assertTrue(abs(fatherCenter - selfCenter) < abs(fatherCenter - wifeCenter))
        assertTrue(abs(wmCenter - wifeCenter) < abs(wmCenter - selfCenter))
        assertTrue(card.getValue("f").top < card.getValue("self").top)
        assertTrue(card.getValue("wm").top < card.getValue("wife").top)
        assertEquals(card.getValue("f").top, card.getValue("wm").top, 0.5f)
        assertEquals(card.getValue("m").top, card.getValue("wf").top, 0.5f)
        cardsDoNotOverlap(placed.cards)
        assertTrue(placed.childGroups.none { "f" in it.parentIds && "wm" in it.parentIds })
    }

    @Test
    fun familyWithSisterAndInLawParentsStaysStable() {
        val people = listOf(
            PersonEntity(id = "gf", gender = "m", birthDate = "1940-01-01"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01"),
            PersonEntity(id = "f", gender = "m", birthDate = "1965-01-01", fatherId = "gf", motherId = "gm"),
            PersonEntity(id = "m", gender = "f", birthDate = "1967-01-01"),
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01", fatherId = "wf", motherId = "wm"),
            PersonEntity(id = "kid", gender = "m", birthDate = "2015-01-01", fatherId = "self", motherId = "wife"),
            PersonEntity(id = "sis", gender = "f", birthDate = "1992-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "sil", gender = "m", birthDate = "1991-06-01"),
            PersonEntity(id = "niece", gender = "f", birthDate = "2016-01-01", fatherId = "sil", motherId = "sis"),
            PersonEntity(id = "wf", gender = "m", birthDate = "1966-01-01", lastName = "Тесть"),
            PersonEntity(id = "wm", gender = "f", birthDate = "1968-01-01", lastName = "Тёща"),
        )
        val unions = listOf(
            UnionEntity("1", "gf", "gm"),
            UnionEntity("2", "f", "m"),
            UnionEntity("3", "self", "wife"),
            UnionEntity("4", "sis", "sil"),
            UnionEntity("5", "wf", "wm"),
        )
        val placed = TreeLayout.place(people, unions, homePersonId = "self")
        val card = placed.cards.associateBy { it.id }
        assertEquals(people.size, card.size)
        assertEquals(card.getValue("self").top, card.getValue("wife").top, 0.5f)
        assertEquals(card.getValue("sis").top, card.getValue("sil").top, 0.5f)
        assertTrue(card.getValue("f").top < card.getValue("self").top)
        assertTrue(card.getValue("wm").top < card.getValue("wife").top)
        assertEquals(card.getValue("f").top, card.getValue("wm").top, 0.5f)
        assertTrue(card.getValue("kid").top > card.getValue("self").top)
        assertTrue(card.getValue("niece").top > card.getValue("sis").top)
        cardsDoNotOverlap(placed.cards)
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
