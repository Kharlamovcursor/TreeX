package ru.hdlabs.treex.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class KinshipPersistenceTest {
    @Test
    fun attachFatherThenMotherProducesLines() {
        val self = PersonEntity(id = "s", gender = "m", firstName = "Son")
        val father = PersonEntity(id = "f", gender = "m", firstName = "Dad")
        var unions = KinshipPersistence.rebuildUnions(
            people = listOf(self, father),
            existingUnions = emptyList(),
            focusPersonId = "f",
            spouseDrafts = emptyList(),
            attach = RelativeAttach("s", "father"),
            focusPerson = father,
        )
        val mother = PersonEntity(id = "m", gender = "f", firstName = "Mom")
        unions = KinshipPersistence.rebuildUnions(
            people = listOf(self, father, mother),
            existingUnions = unions,
            focusPersonId = "m",
            spouseDrafts = emptyList(),
            attach = RelativeAttach("s", "mother"),
            focusPerson = mother,
        )
        val all = listOf(self, father, mother)
        val placed = TreeLayout.place(all, unions, homePersonId = "s")
        assertTrue(placed.childGroups.any { "s" in it.childIds && "f" in it.parentIds })
        assertTrue(placed.childGroups.any { "s" in it.childIds && "m" in it.parentIds || it.parentIds.size == 2 })
    }

    @Test
    fun editorParentPickOnChild() {
        val self = PersonEntity(id = "s", gender = "m", firstName = "Son", fatherId = "f")
        val father = PersonEntity(id = "f", gender = "m", firstName = "Dad")
        val unions = KinshipPersistence.rebuildUnions(
            people = listOf(self, father),
            existingUnions = emptyList(),
            focusPersonId = "s",
            spouseDrafts = emptyList(),
            attach = null,
            focusPerson = self,
        )
        assertTrue(unions.any { "s" in it.childIdsList() && "f" in it.partnerIds() })
    }
}
