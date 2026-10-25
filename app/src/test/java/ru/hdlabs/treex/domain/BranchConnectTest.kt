package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity

class BranchConnectTest {
    @Test
    fun mainPersonBecomesFatherOfBranchMember() {
        val branch = PersonEntity(id = "b", gender = "m", firstName = "Борис")
        val main = PersonEntity(id = "a", gender = "m", firstName = "Антон")
        val plan = planBranchConnect(branch, main, BranchConnectMode.TARGET_AS_FATHER, listOf(branch, main), emptyList(), 1L)
        assertEquals("a", plan.peopleUpdates.single { it.id == "b" }.fatherId)
    }

    @Test
    fun spouseUnionIsPlanned() {
        val woman = PersonEntity(id = "w", gender = "f")
        val man = PersonEntity(id = "m", gender = "m")
        val plan = planBranchConnect(woman, man, BranchConnectMode.SPOUSE, listOf(woman, man), emptyList(), 1L)
        assertEquals("w" to "m", plan.unionPartners)
    }
}
