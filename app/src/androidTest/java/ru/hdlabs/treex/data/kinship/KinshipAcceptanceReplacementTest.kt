package ru.hdlabs.treex.data.kinship

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.hdlabs.treex.data.local.AppDatabase
import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper
import ru.hdlabs.treex.domain.graph.Person

/**
 * Scenario coverage replacing legacy [KinshipAcceptanceTest] (removed in 4c-2a).
 * Parent cycle: see [KinshipRepositoryTest.linkExistingAsParentCycleBlocked].
 */
@RunWith(AndroidJUnit4::class)
class KinshipAcceptanceReplacementTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: KinshipRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = KinshipRepository(db) {}
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun coupleWithFourChildren() = runBlocking {
        seedPerson("a", "Alex", "Main", "m")
        requireSuccess(repo.addSpouse("a", person("b", "Blake", Gender.FEMALE)))
        repeat(4) { i ->
            requireSuccess(repo.addChild("a", person("c$i", "Child$i"), coParentId = "b"))
        }
        val graph = graphSnapshot()
        val coupleFamilies = graph.familiesOf("a").filter { it.childrenIds.isNotEmpty() }
        assertEquals(1, coupleFamilies.size)
        assertEquals(4, coupleFamilies.single().childrenIds.size)
        val positions = db.familyChildDao().all()
            .filter { it.familyId == coupleFamilies.single().id }
            .map { it.position }
            .sorted()
        assertEquals(listOf(0, 1, 2, 3), positions)
    }

    @Test
    fun singleKnownParent() = runBlocking {
        seedPerson("child", "Kid", "One")
        val result = repo.addParents(
            "child",
            father = null,
            mother = person("mom", "Maria", Gender.FEMALE),
        )
        requireSuccess(result)
        val fam = graphSnapshot().familyWhereChild("child")!!
        assertEquals(null, fam.partnerAId)
        assertEquals("mom", fam.partnerBId)
        assertEquals(listOf("child"), fam.childrenIds)
    }

    @Test
    fun remarriageWithChildrenFromBoth() = runBlocking {
        seedPerson("a", "Alex", "Main", "m")
        requireSuccess(repo.addSpouse("a", person("b", "Blake", Gender.FEMALE)))
        requireSuccess(repo.addChild("a", person("c", "Child1"), coParentId = "b"))
        requireSuccess(repo.addSpouse("a", person("d", "Dana", Gender.FEMALE)))
        requireSuccess(repo.addChild("a", person("e", "Child2"), coParentId = "d"))
        val graph = graphSnapshot()
        assertEquals(2, graph.familiesOf("a").size)
        val withC = graph.familiesOf("a").single { "c" in it.childrenIds }
        val withE = graph.familiesOf("a").single { "e" in it.childrenIds }
        assertEquals(setOf("a", "b"), setOfNotNull(withC.partnerAId, withC.partnerBId))
        assertEquals(setOf("a", "d"), setOfNotNull(withE.partnerAId, withE.partnerBId))
    }

    @Test
    fun sameNamesAllowed() = runBlocking {
        seedPerson("a", "Иван", "Иванов", "m")
        requireSuccess(repo.addSpouseFromEditor("a", entity("b", "Иван", "Иванов", "m")))
        requireSuccess(repo.addChildFromEditor("a", entity("c", "Иван", "Иванов", "m"), coParentId = "b"))
        assertEquals(setOf("a", "b", "c"), graphSnapshot().persons.keys)
    }

    @Test
    fun nameChangeKeepsKinship() = runBlocking {
        seedPerson("a", "Old", "Name")
        requireSuccess(repo.addSpouse("a", person("b", "Spouse", Gender.FEMALE)))
        requireSuccess(repo.addChild("a", person("c", "Kid"), coParentId = "b"))
        val beforeFamilies = db.familyDao().all().map { it.id to it.copy() }.toMap()
        val beforeChildren = db.familyChildDao().all().map { it.familyId to it.childId to it.copy() }.toSet()
        val updated = db.personDao().get("a")!!.copy(firstName = "New")
        requireSuccess(repo.updatePersonFields(updated))
        assertEquals(beforeFamilies.keys, db.familyDao().all().map { it.id }.toSet())
        db.familyDao().all().forEach { fam ->
            assertEquals(beforeFamilies[fam.id], fam)
        }
        val afterChildren = db.familyChildDao().all().map { it.familyId to it.childId to it.copy() }.toSet()
        assertEquals(beforeChildren, afterChildren)
        assertEquals("New", db.personDao().get("a")!!.firstName)
    }

    @Test
    fun detachParentKeepsChild() = runBlocking {
        seedFamily(
            familyId = "f1",
            partnerA = "dad",
            partnerB = "mom",
            children = listOf("child"),
        )
        val famId = db.familyDao().all().single().id
        requireSuccess(repo.unlinkSpouse(famId, "mom"))
        assertTrue(db.familyChildDao().all().any { it.childId == "child" && it.familyId == famId })
        assertTrue(db.familyDao().all().any { it.id == famId })
    }

    @Test
    fun multiGeneration() = runBlocking {
        seedPerson("a", "G1A", "Gen", "m")
        seedPerson("b", "G1B", "Gen", "f")
        requireSuccess(repo.linkExistingAsSpouse("a", "b"))
        requireSuccess(repo.addChild("a", person("c", "G2"), coParentId = "b"))
        seedPerson("d", "G2D", "Gen", "m")
        requireSuccess(repo.linkExistingAsSpouse("c", "d"))
        requireSuccess(repo.addChild("c", person("e", "G3"), coParentId = "d"))
        seedPerson("f", "G3F", "Gen", "f")
        requireSuccess(repo.linkExistingAsSpouse("e", "f"))
        requireSuccess(repo.addChild("e", person("g", "G4"), coParentId = "f"))
        val graph = graphSnapshot()
        assertEquals(listOf("c"), graph.childrenOf("a").map { it.id })
        assertEquals(listOf("e"), graph.childrenOf("c").map { it.id })
        assertEquals(listOf("g"), graph.childrenOf("e").map { it.id })
        assertEquals(setOf("a", "b"), graph.parentsOf("c").map { it.id }.toSet())
        assertEquals(setOf("c", "d"), graph.parentsOf("e").map { it.id }.toSet())
    }

    @Test
    fun manyChildrenBranches() = runBlocking {
        seedPerson("a", "Root", "Main", "m")
        requireSuccess(repo.addSpouse("a", person("b", "Partner", Gender.FEMALE)))
        val childIds = (1..8).map { "ch$it" }
        childIds.forEach { id ->
            requireSuccess(repo.addChild("a", person(id, "Child$id"), coParentId = "b"))
        }
        childIds.forEach { id ->
            requireSuccess(repo.addSpouse(id, person("sp$id", "Sp$id", Gender.UNKNOWN)))
        }
        val graph = graphSnapshot()
        assertEquals(8, graph.childrenOf("a").size)
        childIds.forEach { id ->
            assertEquals(1, graph.familiesOf(id).size)
        }
    }

    @Test
    fun childNotAutoAssignedToSpouse_ambiguousWhenTwoFamilies() = runBlocking {
        seedPerson("a", "Alex", "Main", "m")
        requireSuccess(repo.addSpouse("a", person("b", "Blake", Gender.FEMALE)))
        requireSuccess(repo.addSpouse("a", person("d", "Dana", Gender.FEMALE)))
        val blocked = repo.addChild("a", person("c", "Kid"), coParentId = null)
        assertEquals(KinshipWriteResult.BlockedReason(KinshipBlockCode.AMBIGUOUS_FAMILY), blocked)
    }

    @Test
    fun childNotAutoAssignedToSpouse_singleFamilyUsesThatHub() = runBlocking {
        seedPerson("a", "Alex", "Main", "m")
        requireSuccess(repo.addSpouse("a", person("b", "Blake", Gender.FEMALE)))
        requireSuccess(repo.addChild("a", person("c", "Kid"), coParentId = null))
        val graph = graphSnapshot()
        val fam = graph.familyWhereChild("c")!!
        assertEquals(setOf("a", "b"), setOfNotNull(fam.partnerAId, fam.partnerBId))
        assertEquals(1, graph.familiesOf("a").count { "c" in it.childrenIds })
    }

    private suspend fun graphSnapshot(): GenealogyGraph =
        GenealogyGraphMapper.fromFamilies(
            db.personDao().all(),
            db.familyDao().all(),
            db.familyChildDao().all(),
        )

    private suspend fun seedPerson(id: String, first: String, last: String, gender: String = "u") {
        db.personDao().upsert(
            PersonEntity(id = id, firstName = first, lastName = last, gender = gender),
        )
    }

    private suspend fun seedFamily(
        familyId: String,
        partnerA: String,
        partnerB: String,
        children: List<String>,
    ) {
        seedPerson(partnerA, "A", partnerA, "m")
        seedPerson(partnerB, "B", partnerB, "f")
        children.forEach { seedPerson(it, "C", it) }
        db.familyDao().upsert(FamilyEntity(familyId, partnerA, partnerB))
        db.familyChildDao().insertAll(
            children.mapIndexed { index, childId ->
                FamilyChildEntity(familyId, childId, index)
            },
        )
    }

    private fun person(id: String, first: String, gender: Gender = Gender.UNKNOWN): Person =
        Person(id = id, fullName = first, gender = gender)

    private fun entity(id: String, first: String, last: String, gender: String) =
        PersonEntity(id = id, firstName = first, lastName = last, gender = gender)

    private fun requireSuccess(result: KinshipWriteResult): KinshipWriteResult.Success {
        assertTrue("Expected success, got $result", result is KinshipWriteResult.Success)
        return result as KinshipWriteResult.Success
    }
}
