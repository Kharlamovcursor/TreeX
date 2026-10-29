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
        val bloodParentCenter = (card.getValue("f").left + card.getValue("m").left) / 2f + TreeLayout.CARD_W / 2f
        val inLawParentCenter = (card.getValue("wf").left + card.getValue("wm").left) / 2f + TreeLayout.CARD_W / 2f
        assertTrue(
            "blood $bloodParentCenter self $selfCenter wife $wifeCenter inlaw $inLawParentCenter",
            abs(bloodParentCenter - selfCenter) < abs(bloodParentCenter - wifeCenter),
        )
        assertTrue(abs(inLawParentCenter - wifeCenter) < abs(inLawParentCenter - selfCenter))
        assertTrue(card.getValue("f").top < card.getValue("self").top)
        assertTrue(card.getValue("wm").top < card.getValue("wife").top)
        assertEquals(card.getValue("f").top, card.getValue("wm").top, 0.5f)
        assertEquals(card.getValue("m").top, card.getValue("wf").top, 0.5f)
        cardsDoNotOverlap(placed.cards)
        assertTrue(placed.childGroups.none { "f" in it.parentIds && "wm" in it.parentIds })
        assertTrue(card.getValue("self").left < card.getValue("wife").left)
        assertTrue(card.getValue("f").left < card.getValue("wf").left)
    }

    @Test
    fun inLawParentsStayOverDaughterEvenIfOlderThanBloodParents() {
        val people = listOf(
            PersonEntity(id = "gf", gender = "m", birthDate = "1940-01-01"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01"),
            PersonEntity(id = "f", gender = "m", birthDate = "1968-01-01", fatherId = "gf", motherId = "gm"),
            PersonEntity(id = "m", gender = "f", birthDate = "1969-01-01"),
            PersonEntity(id = "self", gender = "m", birthDate = "1990-01-01", fatherId = "f", motherId = "m"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-01-01", fatherId = "wf", motherId = "wm"),
            PersonEntity(id = "wf", gender = "m", birthDate = "1960-01-01", lastName = "Тесть"),
            PersonEntity(id = "wm", gender = "f", birthDate = "1961-01-01", lastName = "Тёща"),
        )
        val unions = listOf(
            UnionEntity("1", "gf", "gm"),
            UnionEntity("2", "f", "m"),
            UnionEntity("3", "self", "wife"),
            UnionEntity("4", "wf", "wm"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        val selfCenter = card.getValue("self").left + TreeLayout.CARD_W / 2f
        val wifeCenter = card.getValue("wife").left + TreeLayout.CARD_W / 2f
        val bloodParentCenter = (card.getValue("f").left + card.getValue("m").left) / 2f + TreeLayout.CARD_W / 2f
        val inLawParentCenter = (card.getValue("wf").left + card.getValue("wm").left) / 2f + TreeLayout.CARD_W / 2f
        assertTrue(abs(bloodParentCenter - selfCenter) < abs(bloodParentCenter - wifeCenter))
        assertTrue(abs(inLawParentCenter - wifeCenter) < abs(inLawParentCenter - selfCenter))
        cardsDoNotOverlap(placed.cards)
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
        val coupleLeft = minOf(card.getValue("self").left, card.getValue("wife").left)
        val coupleRight = maxOf(card.getValue("self").left, card.getValue("wife").left) + TreeLayout.CARD_W
        assertTrue(
            card.getValue("sis").left + TreeLayout.CARD_W <= coupleLeft + 0.1f ||
                card.getValue("sis").left >= coupleRight - 0.1f,
        )
        val selfCenter = card.getValue("self").left + TreeLayout.CARD_W / 2f
        val wifeCenter = card.getValue("wife").left + TreeLayout.CARD_W / 2f
        val inLawParentCenter = (card.getValue("wf").left + card.getValue("wm").left) / 2f + TreeLayout.CARD_W / 2f
        assertTrue(abs(inLawParentCenter - wifeCenter) < abs(inLawParentCenter - selfCenter))
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

    @Test
    fun spousesStayTogetherAndFaceTheirOwnParents() {
        val people = listOf(
            PersonEntity(id = "le", gender = "m", birthDate = "1960-05-30", lastName = "Лымарь", firstName = "Евгений"),
            PersonEntity(id = "li", gender = "f", birthDate = "1960-05-30", lastName = "Лымарь", firstName = "Ирина"),
            PersonEntity(id = "hv", gender = "m", birthDate = "1957-02-11", lastName = "Харламов", firstName = "Владимир"),
            PersonEntity(id = "hg", gender = "f", birthDate = "1957-03-15", lastName = "Харламова", firstName = "Галина"),
            PersonEntity(id = "andrey", gender = "m", birthDate = "1965-01-01", fatherId = "le", motherId = "li"),
            PersonEntity(id = "yakov", gender = "m", birthDate = "1967-04-16", fatherId = "hv", motherId = "hg"),
            PersonEntity(id = "elizaveta", gender = "f", birthDate = "1968-10-17", fatherId = "le", motherId = "li"),
            PersonEntity(id = "dmitry", gender = "m", birthDate = "1976-05-14"),
            PersonEntity(id = "svetlana", gender = "f", birthDate = "1982-03-24", fatherId = "hv", motherId = "hg"),
            PersonEntity(id = "maxim", gender = "m", birthDate = "2024-10-03", fatherId = "yakov", motherId = "elizaveta"),
            PersonEntity(id = "ksenia", gender = "f", birthDate = "2017-09-20", fatherId = "yakov", motherId = "elizaveta"),
            PersonEntity(id = "alena", gender = "f", birthDate = "2010-05-03", fatherId = "dmitry", motherId = "svetlana"),
            PersonEntity(id = "maria", gender = "f", birthDate = "2013-02-19", fatherId = "dmitry", motherId = "svetlana"),
            PersonEntity(id = "arina", gender = "f", birthDate = "2015-09-21", fatherId = "dmitry", motherId = "svetlana"),
            PersonEntity(id = "ivan", gender = "m", birthDate = "2006-05-29", fatherId = "dmitry", motherId = "svetlana"),
        )
        val unions = listOf(
            UnionEntity("1", "le", "li"),
            UnionEntity("2", "hv", "hg"),
            UnionEntity("3", "yakov", "elizaveta"),
            UnionEntity("4", "dmitry", "svetlana"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        listOf("le" to "li", "hv" to "hg", "elizaveta" to "yakov", "svetlana" to "dmitry").forEach { (a, b) ->
            assertSpousesAdjacent(a, b, card)
        }
        val elizaveta = card.getValue("elizaveta").left + TreeLayout.CARD_W / 2f
        val yakov = card.getValue("yakov").left + TreeLayout.CARD_W / 2f
        val lymar = (card.getValue("le").left + card.getValue("li").left) / 2f + TreeLayout.CARD_W / 2f
        assertTrue(minOf(abs(lymar - elizaveta), abs(lymar - card.getValue("andrey").left - TreeLayout.CARD_W / 2f)) < abs(lymar - yakov))
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun wifesMotherSitsOnTheWifesSide() {
        val people = listOf(
            PersonEntity(id = "husband", gender = "m", birthDate = "1990-04-16"),
            PersonEntity(id = "wife", gender = "f", birthDate = "1991-10-17", motherId = "mother"),
            PersonEntity(id = "mother", gender = "f", birthDate = "1968-03-15"),
            PersonEntity(id = "child", gender = "m", birthDate = "2017-09-20", fatherId = "husband", motherId = "wife"),
        )
        val unions = listOf(UnionEntity("1", "husband", "wife"))
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertEquals(card.getValue("husband").top, card.getValue("wife").top, 0.5f)
        assertTrue(card.getValue("mother").top < card.getValue("wife").top)
        assertTrue(card.getValue("child").top > card.getValue("wife").top)
        val wifeCenter = card.getValue("wife").left + TreeLayout.CARD_W / 2f
        val husbandCenter = card.getValue("husband").left + TreeLayout.CARD_W / 2f
        val motherCenter = card.getValue("mother").left + TreeLayout.CARD_W / 2f
        assertTrue(abs(motherCenter - wifeCenter) < abs(motherCenter - husbandCenter))
        assertEquals(28f, abs(card.getValue("wife").left - card.getValue("husband").left) - TreeLayout.CARD_W, 0.5f)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun addedFatherSitsAboveDaughterWithoutMovingOtherBranches() {
        val people = listOf(
            PersonEntity(id = "pavlov", gender = "m", birthDate = "1932-01-01", lastName = "Павлов", firstName = "Николай"),
            PersonEntity(id = "le", gender = "m", birthDate = "1960-05-30"),
            PersonEntity(id = "li", gender = "f", birthDate = "1960-05-30"),
            PersonEntity(id = "hv", gender = "m", birthDate = "1957-02-11"),
            PersonEntity(id = "hg", gender = "f", birthDate = "1957-03-15", fatherId = "pavlov"),
            PersonEntity(id = "andrey", gender = "m", birthDate = "1965-01-01", fatherId = "le", motherId = "li"),
            PersonEntity(id = "yakov", gender = "m", birthDate = "1967-04-16", fatherId = "hv", motherId = "hg"),
            PersonEntity(id = "elizaveta", gender = "f", birthDate = "1968-10-17", fatherId = "le", motherId = "li"),
            PersonEntity(id = "dmitry", gender = "m", birthDate = "1976-05-14"),
            PersonEntity(id = "svetlana", gender = "f", birthDate = "1982-03-24", fatherId = "hv", motherId = "hg"),
            PersonEntity(id = "maxim", gender = "m", birthDate = "2024-10-03", fatherId = "yakov", motherId = "elizaveta"),
            PersonEntity(id = "ivan", gender = "m", birthDate = "2006-05-29", fatherId = "dmitry", motherId = "svetlana"),
        )
        val unions = listOf(
            UnionEntity("1", "le", "li"),
            UnionEntity("2", "hv", "hg"),
            UnionEntity("3", "yakov", "elizaveta"),
            UnionEntity("4", "dmitry", "svetlana"),
        )
        val placed = TreeLayout.place(people, unions)
        val card = placed.cards.associateBy { it.id }
        assertTrue(card.getValue("pavlov").top < card.getValue("hg").top)
        assertSpousesAdjacent("hv", "hg", card)
        assertSpousesAdjacent("elizaveta", "yakov", card)
        val galinaCenter = card.getValue("hg").left + TreeLayout.CARD_W / 2f
        val lymarCenter = (card.getValue("le").left + card.getValue("li").left) / 2f + TreeLayout.CARD_W / 2f
        val pavlovCenter = card.getValue("pavlov").left + TreeLayout.CARD_W / 2f
        assertTrue(abs(pavlovCenter - galinaCenter) < abs(pavlovCenter - lymarCenter))
        cardsDoNotOverlap(placed.cards)
    }

    private fun assertSpousesAdjacent(leftId: String, rightId: String, card: Map<String, TreeCard>) {
        val left = card.getValue(leftId)
        val right = card.getValue(rightId)
        assertEquals(left.top, right.top, 0.5f)
        val gap = abs(left.left - right.left) - TreeLayout.CARD_W
        assertEquals("$leftId and $rightId gap", 28f, gap, 0.5f)
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
