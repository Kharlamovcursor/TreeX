package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Обязательные сценарии приёмки (данные и связи; визуализация — Topola GEDCOM). */
class KinshipAcceptanceTest {
    @Test
    fun scenario1_coupleWithFourChildren() {
        val h = PersonEntity(id = "h", gender = "m", birthDate = "1980-01-01")
        val w = PersonEntity(id = "w", gender = "f", birthDate = "1981-01-01")
        val kids = (1..4).map { i ->
            PersonEntity(id = "c$i", gender = "m", birthDate = "20${10 + i}-01-01", fatherId = "h", motherId = "w")
        }
        val people = listOf(h, w) + kids
        val unions = listOf(UnionEntity("u", "h", "w", childIds = kids.joinToString(",") { it.id }))
        val compact = UnionEr.compactUnions(people, unions)
        assertEquals(1, compact.count { it.childIdsList().size == 4 })
    }

    @Test
    fun scenario2_singleKnownParent() {
        val f = PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01")
        val c = PersonEntity(id = "c", gender = "m", birthDate = "2000-01-01", fatherId = "f")
        val unions = listOf(UnionEntity("pu-f", "f", null, childIds = "c"))
        val parents = UnionEr.parentIdsOf(c, setOf("f", "c"), UnionEr.compactUnions(listOf(f, c), unions))
        assertEquals(listOf("f"), parents)
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
        val compact = UnionEr.compactUnions(people, unions)
        assertTrue(compact.any { "c1" in it.childIdsList() })
        assertTrue(compact.any { "c2" in it.childIdsList() })
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
        val issues = TreeDataValidator.validate(people, unions)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun scenario9_manyChildrenAndBranches() {
        val f = PersonEntity(id = "f", gender = "m")
        val m = PersonEntity(id = "m", gender = "f")
        val kids = (1..8).map { i -> PersonEntity(id = "k$i", fatherId = "f", motherId = "m") }
        val unions = listOf(UnionEntity("u", "f", "m", childIds = kids.joinToString(",") { it.id }))
        val compact = UnionEr.compactUnions(listOf(f, m) + kids, unions)
        assertEquals(8, compact.single().childIdsList().size)
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
}
