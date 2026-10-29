package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.backup.UnionRecord
import ru.hdlabs.treex.data.local.PersonEntity

class SiblingsTest {
    private fun childUnion(id: String, father: String, mother: String, childId: String) =
        UnionRecord(id = id, partnerAId = father, partnerBId = mother, childIds = listOf(childId))

    @Test
    fun fullAndHalfSiblings() {
        val father = PersonEntity(id = "f", gender = "m", firstName = "Отец")
        val mother1 = PersonEntity(id = "m1", gender = "f", firstName = "Мать1")
        val mother2 = PersonEntity(id = "m2", gender = "f", firstName = "Мать2")
        val full = PersonEntity(id = "c1", gender = "m", firstName = "Полный")
        val half = PersonEntity(id = "c2", gender = "f", firstName = "Сводная")
        val self = PersonEntity(id = "c0", gender = "m", firstName = "Я")
        val unions = listOf(
            childUnion("u0", "f", "m1", "c0"),
            childUnion("u1", "f", "m1", "c1"),
            childUnion("u2", "f", "m2", "c2"),
        )
        val people = listOf(father, mother1, mother2, full, half, self)
        val groups = siblingGroupsOf(self, people, unions)
        assertEquals(listOf("c1"), groups.full.map { it.id })
        assertEquals(listOf("c2"), groups.half.map { it.id })
        assertEquals("общий отец", halfSiblingCaption(self, half, unions, people))
    }

    @Test
    fun newFatherIsNotCopiedOntoPeopleWithoutParents() {
        val anchor = PersonEntity(id = "galina", gender = "f")
        val stranger = PersonEntity(id = "vladimir", gender = "m")
        val sister = PersonEntity(id = "olga", gender = "f")
        val withMother = PersonEntity(id = "galina2", gender = "f")
        val maria = PersonEntity(id = "maria", gender = "f")
        val unions = listOf(
            UnionRecord(id = "u1", partnerAId = null, partnerBId = "maria", childIds = listOf("olga")),
            UnionRecord(id = "u2", partnerAId = null, partnerBId = "maria", childIds = listOf("galina2")),
        )
        val people = listOf(anchor, stranger, sister, withMother, maria)
        assertTrue(siblingsMissingParent(people, anchor, "pavlov", asFather = true, unions).isEmpty())
        assertEquals(
            listOf("olga"),
            siblingsMissingParent(people, withMother, "pavlov", asFather = true, unions).map { it.id },
        )
    }

    @Test
    fun noSiblingWhenNoSharedParent() {
        val a = PersonEntity(id = "a")
        val b = PersonEntity(id = "b")
        val unions = listOf(
            childUnion("u1", "f1", "m1", "a"),
            childUnion("u2", "f2", "m2", "b"),
        )
        val groups = siblingGroupsOf(a, listOf(a, b), unions)
        assertTrue(groups.full.isEmpty())
        assertTrue(groups.half.isEmpty())
    }
}
