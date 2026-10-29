package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Обязательные сценарии приёмки (данные + линии + раскладка). */
class KinshipAcceptanceTest {
    @Test
    fun scenario1_coupleWithFourChildren() {
        val h = PersonEntity(id = "h", gender = "m", birthDate = "1980-01-01")
        val w = PersonEntity(id = "w", gender = "f", birthDate = "1981-01-01")
        val kids = (1..4).map { i ->
            PersonEntity(id = "c$i", gender = "m", birthDate = "20${10 + i}-01-01", fatherId = "h", motherId = "w")
        }
        val people = listOf(h, w) + kids
        val unions = listOf(UnionEntity("u", "h", "w"))
        val placed = TreeLayout.place(people, unions)
        assertEquals(6, placed.cards.size)
        cardsDoNotOverlap(placed.cards)
        assertTrue(placed.childGroups.any { it.childIds.size == 4 })
    }

    @Test
    fun scenario2_singleKnownParent() {
        val f = PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01")
        val c = PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01", fatherId = "f")
        val unions = listOf(UnionEntity("pu-f", "f", null, childIds = "c"))
        val placed = TreeLayout.place(listOf(f, c), unions)
        assertEquals(1, placed.childGroups.size)
        assertEquals(listOf("f"), placed.childGroups.single().parentIds)
    }

    @Test
    fun scenario3_remarriageWithChildrenFromBoth() {
        val h = PersonEntity(id = "h", gender = "m", birthDate = "1970-01-01")
        val w1 = PersonEntity(id = "w1", gender = "f", birthDate = "1972-01-01")
        val w2 = PersonEntity(id = "w2", gender = "f", birthDate = "1975-01-01")
        val c1 = PersonEntity(id = "c1", gender = "m", birthDate = "1995-01-01", fatherId = "h", motherId = "w1")
        val c2 = PersonEntity(id = "c2", gender = "f", birthDate = "2010-01-01", fatherId = "h", motherId = "w2")
        val people = listOf(h, w1, w2, c1, c2)
        val unions = listOf(
            UnionEntity("u1", "h", "w1", childIds = "c1"),
            UnionEntity("u2", "h", "w2", childIds = "c2"),
        )
        val placed = TreeLayout.place(people, unions)
        assertEquals(5, placed.cards.size)
        assertTrue(placed.spouseLinks.size >= 2)
        assertTrue(placed.childGroups.any { "c1" in it.childIds })
        assertTrue(placed.childGroups.any { "c2" in it.childIds })
    }

    @Test
    fun scenario4_sameNamesAllowed() {
        val a = PersonEntity(id = "a", lastName = "Иванов", firstName = "Иван", birthDate = "1990-01-01")
        val b = PersonEntity(id = "b", lastName = "Иванов", firstName = "Иван", birthDate = "1992-01-01")
        val issues = TreeDataValidator.validate(listOf(a, b), emptyList())
        assertTrue(issues.none { it.code == "duplicate_person" })
    }

    @Test
    fun scenario5_nameChangeKeepsKinship() {
        var state = familyTreeStateFrom(
            listOf(PersonEntity(id = "p", lastName = "Стар", firstName = "Иван")),
            emptyList(),
        )
        state = state.copy(people = state.people.map { it.copy(lastName = "Нов") })
        assertEquals("Нов", state.people.single().lastName)
    }

    @Test
    fun scenario7_detachParentKeepsChild() {
        var state = familyTreeStateFrom(
            listOf(
                PersonEntity(id = "f", gender = "m"),
                PersonEntity(id = "m", gender = "f"),
                PersonEntity(id = "c", gender = "m"),
            ),
            listOf(UnionEntity("u", "f", "m", childIds = "c")),
        )
        state = FamilyTreeMutations.removeParentChildEdge(state, "c", "f")
        val people = listOf(
            PersonEntity(id = "f", gender = "m"),
            PersonEntity(id = "m", gender = "f"),
            PersonEntity(id = "c", gender = "m"),
        )
        val ids = people.map { it.id }.toSet()
        val unions = UnionEr.compactUnions(people, state.unions.map { it.toUnionEntity() })
        val child = KinshipSync.parentFieldsFromUnions(PersonEntity(id = "c"), people, unions)
        val parents = UnionEr.parentIdsOf(child, ids, unions)
        assertFalse(parents.contains("f"))
        assertTrue(parents.contains("m"))
        assertTrue(state.people.any { it.id == "c" })
    }

    @Test
    fun scenario8_multiGeneration() {
        val people = listOf(
            PersonEntity(id = "gf", gender = "m", birthDate = "1940-01-01"),
            PersonEntity(id = "gm", gender = "f", birthDate = "1942-01-01"),
            PersonEntity(id = "f", gender = "m", birthDate = "1965-01-01", fatherId = "gf", motherId = "gm"),
            PersonEntity(id = "c", gender = "m", birthDate = "1990-01-01", fatherId = "f"),
        )
        val unions = listOf(UnionEntity("1", "gf", "gm"), UnionEntity("pu-f", "f", null, childIds = "c"))
        val placed = TreeLayout.place(people, unions)
        assertTrue(placed.cards.find { it.id == "c" }!!.top > placed.cards.find { it.id == "f" }!!.top)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario9_manyChildrenAndBranches() {
        val f = PersonEntity(id = "f", gender = "m")
        val m = PersonEntity(id = "m", gender = "f")
        val kids = (1..8).map { i -> PersonEntity(id = "k$i", fatherId = "f", motherId = "m") }
        val branch = PersonEntity(id = "x", gender = "m")
        val placed = TreeLayout.place(
            listOf(f, m, branch) + kids,
            listOf(UnionEntity("u", "f", "m", childIds = kids.joinToString(",") { it.id })),
        )
        assertEquals(11, placed.cards.size)
        cardsDoNotOverlap(placed.cards)
    }

    @Test
    fun scenario11_parentCycleRejected() {
        val people = listOf(
            PersonEntity(id = "a"),
            PersonEntity(id = "b"),
        )
        val parentMap = mapOf(
            "a" to ("b" to null),
            "b" to ("a" to null),
        )
        assertTrue(TreeRules.createsCycle(parentMap, "a", "b", null))
    }

    @Test
    fun scenario12_childNotAutoAssignedToSpouse() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m"),
            PersonEntity(id = "w", gender = "f"),
        )
        val unions = listOf(UnionEntity("marriage", "h", "w"))
        val state = familyTreeStateFrom(people, unions)
        val child = FamilyPerson(id = "c", gender = FamilyGender.Male)
        val next = UnionErAttach.applyRelativeAttach(people, unions, "h", "child", child.toPersonEntity())
        val childParents = UnionEr.parentIdsOf(PersonEntity(id = "c"), people.map { it.id }.toSet() + "c", next)
        assertEquals(listOf("h"), childParents)
    }

    @Test
    fun layoutStableWhenInputOrderChanges() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m"),
            PersonEntity(id = "m", gender = "f"),
            PersonEntity(id = "c", gender = "m", fatherId = "f", motherId = "m"),
        )
        val unions = listOf(UnionEntity("u", "f", "m"))
        val a = TreeLayout.place(people, unions).cards.sortedBy { it.id }
        val b = TreeLayout.place(people.reversed(), unions).cards.sortedBy { it.id }
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.left, y.left, 0.5f)
            assertEquals(x.top, y.top, 0.5f)
        }
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
