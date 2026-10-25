package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class TreeGraphTest {
    @Test
    fun detachedBranchIsSeparatedFromMainTree() {
        val main = PersonEntity(id = "a", firstName = "Анна")
        val branchRoot = PersonEntity(id = "b", firstName = "Борис")
        val branchChild = PersonEntity(id = "c", firstName = "Вера", fatherId = "b")
        val detached = detachedFromMainPersonIds(
            listOf(main, branchRoot, branchChild),
            emptyList(),
            homePersonId = "a",
        )
        assertEquals(setOf("b", "c"), detached)
    }

    @Test
    fun spouseUnionJoinsComponents() {
        val a = PersonEntity(id = "a")
        val b = PersonEntity(id = "b")
        val components = treeComponents(listOf(a, b), listOf(UnionEntity("u", "a", "b")))
        assertEquals(1, components.size)
        assertTrue(detachedFromMainPersonIds(listOf(a, b), listOf(UnionEntity("u", "a", "b")), "a").isEmpty())
    }
}
