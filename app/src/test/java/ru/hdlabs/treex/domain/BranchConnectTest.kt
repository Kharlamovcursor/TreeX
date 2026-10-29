package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

class BranchConnectTest {
    private fun graph(people: List<PersonEntity>) =
        GenealogyGraphMapper.fromFamilies(people, emptyList(), emptyList())

    @Test
    fun mainPersonBecomesFatherOfBranchMember() {
        val branch = PersonEntity(id = "b", gender = "m", firstName = "Борис")
        val main = PersonEntity(id = "a", gender = "m", firstName = "Антон")
        val people = listOf(branch, main)
        val plan = planBranchConnect(branch, main, BranchConnectMode.TARGET_AS_FATHER, people, graph(people), 1L)
        assertTrue(plan.peopleUpdates.isEmpty())
        assertEquals(null, plan.unionPartners)
    }

    @Test
    fun spouseUnionIsPlanned() {
        val woman = PersonEntity(id = "w", gender = "f")
        val man = PersonEntity(id = "m", gender = "m")
        val people = listOf(woman, man)
        val plan = planBranchConnect(woman, man, BranchConnectMode.SPOUSE, people, graph(people), 1L)
        assertEquals("w" to "m", plan.unionPartners)
    }
}
