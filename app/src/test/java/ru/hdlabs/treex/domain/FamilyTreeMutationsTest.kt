package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class FamilyTreeMutationsTest {
    @Test
    fun addChildReusesExistingCoupleUnion() {
        var state = FamilyTreeState(
            people = listOf(
                FamilyPerson("h", gender = FamilyGender.Male),
                FamilyPerson("w", gender = FamilyGender.Female),
            ),
            unions = listOf(FamilyUnion("u", "h", "w")),
        )
        state = FamilyTreeMutations.addChildToCouple(state, "h", "w", FamilyPerson("c1"))
        state = FamilyTreeMutations.addChildToCouple(state, "h", "w", FamilyPerson("c2"))
        assertEquals(1, state.unions.size)
        assertEquals(listOf("c1", "c2"), state.unions.single().childrenIds)
    }

    @Test
    fun addParentCreatesSingleSlotUnion() {
        val state = FamilyTreeState(people = listOf(FamilyPerson("child")))
        val next = FamilyTreeMutations.addParentToPerson(state, "child", FamilyPerson("f", gender = FamilyGender.Male))
        val union = next.unions.single()
        assertEquals("f", union.partner1Id)
        assertEquals(null, union.partner2Id)
        assertTrue("child" in union.childrenIds)
    }

    @Test
    fun secondParentFillsNullSlot() {
        var state = FamilyTreeState(people = listOf(FamilyPerson("child"), FamilyPerson("f", gender = FamilyGender.Male)))
        state = FamilyTreeMutations.addParentToPerson(state, "child", FamilyPerson("f", gender = FamilyGender.Male))
        state = FamilyTreeMutations.addParentToPerson(state, "child", FamilyPerson("m", gender = FamilyGender.Female))
        val union = UnionEr.findParentUnionForChild(state.unions.map { it.toUnionEntity() }, "child")
        assertNotNull(union)
        assertEquals(setOf("f", "m"), union!!.partnerIds().toSet())
    }

    @Test
    fun layoutUsesUnionChildrenIds() {
        val people = listOf(
            PersonEntity(id = "h", gender = "m", birthDate = "1990-01-01"),
            PersonEntity(id = "w", gender = "f", birthDate = "1991-01-01"),
            PersonEntity(id = "c", gender = "m", birthDate = "2015-01-01"),
        )
        val unions = listOf(
            UnionEntity(id = "u", partnerAId = "h", partnerBId = "w", childIds = "c"),
        )
        val placed = TreeLayout.place(people, unions)
        assertEquals(1, placed.childGroups.size)
        assertEquals(listOf("c"), placed.childGroups.single().childIds.sorted())
    }
}
