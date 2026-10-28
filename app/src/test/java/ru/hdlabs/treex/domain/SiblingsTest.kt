package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity

class SiblingsTest {
    @Test
    fun fullAndHalfSiblings() {
        val father = PersonEntity(id = "f", gender = "m", firstName = "Отец")
        val mother1 = PersonEntity(id = "m1", gender = "f", firstName = "Мать1")
        val mother2 = PersonEntity(id = "m2", gender = "f", firstName = "Мать2")
        val full = PersonEntity(id = "c1", gender = "m", firstName = "Полный", fatherId = "f", motherId = "m1")
        val half = PersonEntity(id = "c2", gender = "f", firstName = "Сводная", fatherId = "f", motherId = "m2")
        val self = PersonEntity(id = "c0", gender = "m", firstName = "Я", fatherId = "f", motherId = "m1")
        val groups = siblingGroupsOf(self, listOf(father, mother1, mother2, full, half, self))
        assertEquals(listOf("c1"), groups.full.map { it.id })
        assertEquals(listOf("c2"), groups.half.map { it.id })
        assertEquals("общий отец", halfSiblingCaption(self, half))
    }

    @Test
    fun newFatherIsNotCopiedOntoPeopleWithoutParents() {
        val anchor = PersonEntity(id = "galina", gender = "f", motherId = null)
        val stranger = PersonEntity(id = "vladimir", gender = "m")
        val sister = PersonEntity(id = "olga", gender = "f", motherId = "maria")
        val withMother = PersonEntity(id = "galina2", gender = "f", motherId = "maria")
        val people = listOf(anchor, stranger, sister, withMother, PersonEntity(id = "maria", gender = "f"))
        assertTrue(siblingsMissingParent(people, anchor, "pavlov", asFather = true).isEmpty())
        assertEquals(
            listOf("olga"),
            siblingsMissingParent(people, withMother, "pavlov", asFather = true).map { it.id },
        )
    }

    @Test
    fun noSiblingWhenNoSharedParent() {
        val a = PersonEntity(id = "a", fatherId = "f1", motherId = "m1")
        val b = PersonEntity(id = "b", fatherId = "f2", motherId = "m2")
        val groups = siblingGroupsOf(a, listOf(a, b))
        assertTrue(groups.full.isEmpty())
        assertTrue(groups.half.isEmpty())
    }
}
