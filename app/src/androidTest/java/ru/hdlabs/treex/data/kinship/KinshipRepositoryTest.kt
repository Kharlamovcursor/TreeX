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
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.ValidationIssue

@RunWith(AndroidJUnit4::class)
class KinshipRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: KinshipRepository
    private var changeCount = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        changeCount = 0
        repo = KinshipRepository(db) { changeCount++ }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addSiblingSuccess() = runBlocking {
        seedParentFamily("f1", "dad", "mom", listOf("anchor"))
        seedPerson("sib", "New", "Sibling")
        val result = repo.addSiblingFromEditor("anchor", entity("sib", "New", "Sibling"))
        assertTrue(result is KinshipWriteResult.Success)
        val rows = db.familyChildDao().all().filter { it.familyId == "f1" }
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.childId == "sib" })
    }

    @Test
    fun addSiblingNoParentFamily() = runBlocking {
        seedPerson("anchor", "A", "Anchor")
        seedPerson("sib", "S", "Sibling")
        val result = repo.addSiblingFromEditor("anchor", entity("sib", "S", "Sibling"))
        assertEquals(KinshipWriteResult.BlockedReason(KinshipBlockCode.NO_PARENT_FAMILY), result)
    }

    @Test
    fun addSiblingAmbiguousParentFamily() = runBlocking {
        val graph = ru.hdlabs.treex.domain.graph.GenealogyGraph(
            persons = mapOf(
                "anchor" to ru.hdlabs.treex.domain.graph.Person("anchor", "anchor", ru.hdlabs.treex.domain.graph.Gender.UNKNOWN, null, null, null),
                "p1" to ru.hdlabs.treex.domain.graph.Person("p1", "p1", ru.hdlabs.treex.domain.graph.Gender.UNKNOWN, null, null, null),
                "p2" to ru.hdlabs.treex.domain.graph.Person("p2", "p2", ru.hdlabs.treex.domain.graph.Gender.UNKNOWN, null, null, null),
            ),
            families = mapOf(
                "f1" to ru.hdlabs.treex.domain.graph.Family("f1", "p1", null, listOf("c1")),
                "f2" to ru.hdlabs.treex.domain.graph.Family("f2", "p2", null, listOf("c2")),
            ),
        )
        val result = FamilyMutationValidator.parentFamilyForSibling(graph, "anchor")
        assertEquals(FamilyMutationValidator.ParentFamilyResult.NoParentFamily, result)
    }

    @Test
    fun removePersonNotFound() = runBlocking {
        val result = repo.removePerson("missing")
        assertEquals(KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND), result)
    }

    @Test
    fun removePersonDeletesRecord() = runBlocking {
        seedPerson("x", "X", "Person")
        val result = repo.removePerson("x")
        assertTrue(result is KinshipWriteResult.Success)
        assertEquals(null, db.personDao().get("x"))
        assertEquals(1, changeCount)
    }

    @Test
    fun removePersonClearsPartnerSlot() = runBlocking {
        seedPerson("a", "A", "One")
        seedPerson("b", "B", "Two")
        repo.linkExistingAsSpouse("a", "b")
        repo.removePerson("b")
        val fam = db.familyDao().all().single()
        assertEquals("a", fam.partnerAId)
        assertEquals(null, fam.partnerBId)
    }

    @Test
    fun removePersonDeletesEmptyFamilyKeepsFamilyWithChildren() = runBlocking {
        seedParentFamily("legacy", "dad", "mom", listOf("child"))
        seedPerson("dad", "D", "Ad")
        repo.removePerson("dad")
        assertTrue(db.familyDao().all().any { it.id == "legacy" })
        assertEquals(1, db.familyChildDao().all().count { it.familyId == "legacy" })
    }

    @Test
    fun editSpousesDuplicateBlocked() = runBlocking {
        seedPerson("p", "P", "Main")
        seedPerson("s1", "S", "One")
        repo.linkExistingAsSpouse("p", "s1")
        val dup = repo.linkExistingAsSpouse("p", "s1")
        assertEquals(KinshipWriteResult.BlockedReason(KinshipBlockCode.DUPLICATE_FAMILY), dup)
    }

    @Test
    fun editSpousesUnlinkAndLink() = runBlocking {
        seedPerson("p", "P", "Main")
        seedPerson("s1", "S", "One")
        seedPerson("s2", "T", "Two")
        repo.linkExistingAsSpouse("p", "s1")
        repo.unlinkSpouse(db.familyDao().all().single().id, "s1")
        val link = repo.linkExistingAsSpouse("p", "s2")
        assertTrue(link is KinshipWriteResult.Success)
        val fam = db.familyDao().all().single()
        assertEquals(setOf("p", "s2"), setOfNotNull(fam.partnerAId, fam.partnerBId))
    }

    @Test
    fun linkExistingAsSibling() = runBlocking {
        seedParentFamily("f1", "dad", "mom", listOf("anchor"))
        seedPerson("sib", "S", "Sibling")
        val result = repo.linkExistingAsSibling("anchor", "sib")
        assertTrue(result is KinshipWriteResult.Success)
        assertTrue(db.familyChildDao().all().any { it.childId == "sib" })
    }

    @Test
    fun linkExistingAsParentCycleBlocked() = runBlocking {
        seedPerson("a", "A", "A")
        seedPerson("b", "B", "B")
        repo.linkExistingAsParent("a", "b", ParentRole.FATHER)
        val cycle = repo.linkExistingAsParent("b", "a", ParentRole.FATHER)
        assertTrue(cycle is KinshipWriteResult.Blocked)
        assertTrue((cycle as KinshipWriteResult.Blocked).errors.any { it is ValidationIssue.CycleDetected })
    }

    private suspend fun seedPerson(id: String, first: String, last: String) {
        db.personDao().upsert(entity(id, first, last))
    }

    private suspend fun seedParentFamily(familyId: String, father: String, mother: String, children: List<String>) {
        seedPerson(father, "F", father)
        seedPerson(mother, "M", mother)
        children.forEach { seedPerson(it, "C", it) }
        db.familyDao().upsert(ru.hdlabs.treex.data.local.FamilyEntity(familyId, father, mother))
        db.familyChildDao().insertAll(
            children.mapIndexed { index, cid ->
                ru.hdlabs.treex.data.local.FamilyChildEntity(familyId, cid, index)
            },
        )
    }

    private fun entity(id: String, first: String, last: String) = PersonEntity(
        id = id,
        lastName = last,
        firstName = first,
    )
}
