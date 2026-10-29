package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

class SiblingsTest {
    private fun childFamily(id: String, father: String, mother: String, childId: String) =
        FamilyEntity(id = id, partnerAId = father, partnerBId = mother) to FamilyChildEntity(id, childId)

    private fun graph(people: List<PersonEntity>, pairs: List<Pair<FamilyEntity, FamilyChildEntity>>) =
        GenealogyGraphMapper.fromFamilies(
            people,
            pairs.map { it.first },
            pairs.map { it.second },
        )

    @Test
    fun fullAndHalfSiblings() {
        val father = PersonEntity(id = "f", gender = "m", firstName = "Отец")
        val mother1 = PersonEntity(id = "m1", gender = "f", firstName = "Мать1")
        val mother2 = PersonEntity(id = "m2", gender = "f", firstName = "Мать2")
        val full = PersonEntity(id = "c1", gender = "m", firstName = "Полный")
        val half = PersonEntity(id = "c2", gender = "f", firstName = "Сводная")
        val self = PersonEntity(id = "c0", gender = "m", firstName = "Я")
        val people = listOf(father, mother1, mother2, full, half, self)
        val g = graph(
            people,
            listOf(
                childFamily("u0", "f", "m1", "c0"),
                childFamily("u1", "f", "m1", "c1"),
                childFamily("u2", "f", "m2", "c2"),
            ),
        )
        val groups = siblingGroupsOf(self, people, g)
        assertEquals(listOf("c1"), groups.full.map { it.id })
        assertEquals(listOf("c2"), groups.half.map { it.id })
        assertEquals("общий отец", halfSiblingCaption(self, half, g))
    }

    @Test
    fun newFatherIsNotCopiedOntoPeopleWithoutParents() {
        val anchor = PersonEntity(id = "galina", gender = "f")
        val stranger = PersonEntity(id = "vladimir", gender = "m")
        val sister = PersonEntity(id = "olga", gender = "f")
        val withMother = PersonEntity(id = "galina2", gender = "f")
        val maria = PersonEntity(id = "maria", gender = "f")
        val people = listOf(anchor, stranger, sister, withMother, maria)
        val g = graph(
            people,
            listOf(
                FamilyEntity("u1", null, "maria") to FamilyChildEntity("u1", "olga"),
                FamilyEntity("u2", null, "maria") to FamilyChildEntity("u2", "galina2"),
            ),
        )
        assertTrue(siblingsMissingParent(people, anchor, "pavlov", asFather = true, g).isEmpty())
        assertEquals(
            listOf("olga"),
            siblingsMissingParent(people, withMother, "pavlov", asFather = true, g).map { it.id },
        )
    }

    @Test
    fun noSiblingWhenNoSharedParent() {
        val a = PersonEntity(id = "a")
        val b = PersonEntity(id = "b")
        val g = graph(
            listOf(a, b),
            listOf(
                childFamily("u1", "f1", "m1", "a"),
                childFamily("u2", "f2", "m2", "b"),
            ),
        )
        val groups = siblingGroupsOf(a, listOf(a, b), g)
        assertTrue(groups.full.isEmpty())
        assertTrue(groups.half.isEmpty())
    }
}
