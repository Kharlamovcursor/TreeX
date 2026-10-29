package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class TreeIntegrityTest {
    @Test
    fun vacantParentIsExcludedFromLayoutLinks() {
        val people = listOf(
            PersonEntity(id = "m", gender = "f", birthDate = "1960-01-01", firstName = "Мать"),
            PersonEntity(id = "v", gender = "m", vacant = true),
            PersonEntity(id = "c", gender = "m", birthDate = "1990-01-01", fatherId = "v", motherId = "m"),
        )
        val unions = listOf(UnionEntity("u", "v", "m"))
        val placed = TreeLayout.place(people, unions)
        assertEquals(2, placed.cards.size)
        assertTrue(placed.cards.none { it.id == "v" })
        assertTrue(
            placed.childGroups.all { group ->
                group.parentIds.none { it == "v" } && group.childIds.contains("c")
            },
        )
        assertTrue(placed.spouseLinks.isEmpty())
    }

    @Test
    fun repairClearsPointerToVacantParent() {
        val people = listOf(
            PersonEntity(id = "m", gender = "f"),
            PersonEntity(id = "v", gender = "m", vacant = true),
            PersonEntity(id = "c", gender = "m", fatherId = "v", motherId = "m"),
        )
        val fixed = TreeIntegrity.repairParentPointers(people).single()
        assertEquals("c", fixed.id)
        assertEquals(null, fixed.fatherId)
        assertEquals("m", fixed.motherId)
    }
}
