package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class UnionErCompactTest {
    @Test
    fun compactRemovesDuplicateSingleParentWhenCoupleExists() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m"),
            PersonEntity(id = "m", gender = "f"),
            PersonEntity(id = "c", gender = "m"),
        )
        val messy = listOf(
            UnionEntity(id = "u-f-m", partnerAId = "f", partnerBId = "m", childIds = "c"),
            UnionEntity(id = "pu-f", partnerAId = "f", childIds = "c"),
            UnionEntity(id = "pu-m", partnerAId = "m", childIds = "c"),
        )
        val compact = UnionEr.compactUnions(people, messy)
        assertEquals(1, compact.count { "c" in it.childIdsList() })
        val groups = UnionEr.childGroupsByParentPairs(people, people.map { it.id }.toSet(), compact)
        assertEquals(1, groups.size)
        assertEquals(listOf("c"), groups.single().childIds)
    }

    @Test
    fun parentIdsFromUnionChildMembership() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m"),
            PersonEntity(id = "m", gender = "f"),
            PersonEntity(id = "c", gender = "m"),
        )
        val wrongUnion = listOf(
            UnionEntity(id = "pu-x", partnerAId = "m", childIds = "c"),
        )
        val parents = UnionEr.parentIdsOf(people[2], people.map { it.id }.toSet(), wrongUnion)
        assertEquals(listOf("m"), parents)
    }

    @Test
    fun siblingsShareOneUnion() {
        val people = listOf(
            PersonEntity(id = "f", gender = "m", birthDate = "1970-01-01"),
            PersonEntity(id = "m", gender = "f", birthDate = "1972-01-01"),
            PersonEntity(id = "a", gender = "m", birthDate = "1995-01-01"),
            PersonEntity(id = "b", gender = "f", birthDate = "1998-01-01"),
        )
        val unions = listOf(UnionEntity("u", "f", "m", childIds = "a,b"))
        val compact = UnionEr.compactUnions(people, unions)
        assertEquals(1, compact.size)
        assertTrue(compact.single().childIdsList().containsAll(listOf("a", "b")))
    }
}
