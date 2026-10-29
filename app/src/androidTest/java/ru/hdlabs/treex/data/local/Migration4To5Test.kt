package ru.hdlabs.treex.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper
import ru.hdlabs.treex.domain.graph.GenealogyValidator
import ru.hdlabs.treex.domain.graph.ValidationMode

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java,
    )

    @Test
    fun migrateEmptyV4ToV5() {
        createVersion4Database().close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).use { db ->
            assertTableEmpty(db, "families")
            assertTableEmpty(db, "family_children")
        }
    }

    @Test
    fun migrateUnionWithThreeChildrenPreservesPosition() {
        createVersion4Database().use { db ->
            insertPerson(db, "h", "m")
            insertPerson(db, "w", "f")
            insertPerson(db, "c1", "m")
            insertPerson(db, "c2", "m")
            insertPerson(db, "c3", "f")
            insertUnion(db, "u1", "h", "w", "c1,c2,c3")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).use { db ->
            assertEquals(1, count(db, "families"))
            val positions = queryIntColumn(db, "SELECT position FROM family_children WHERE familyId='u1' ORDER BY position")
            assertEquals(listOf(0, 1, 2), positions)
        }
    }

    @Test
    fun migrateLegacyParentsWithoutUnion() {
        createVersion4Database().use { db ->
            insertPerson(db, "f", "m", fatherId = null, motherId = null)
            insertPerson(db, "m", "f", fatherId = null, motherId = null)
            insertPerson(db, "c", "m", fatherId = "f", motherId = "m")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).use { db ->
            assertTrue(exists(db, "SELECT 1 FROM families WHERE id='legacy-c'"))
            assertTrue(exists(db, "SELECT 1 FROM family_children WHERE familyId='legacy-c' AND childId='c' AND position=0"))
        }
    }

    @Test
    fun migrateDuplicateChildIdKeepsFirstPosition() {
        createVersion4Database().use { db ->
            insertPerson(db, "p", "m")
            insertPerson(db, "c", "m")
            insertUnion(db, "u", "p", null, "c,c,c")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).use { db ->
            assertEquals(1, count(db, "family_children WHERE familyId='u'"))
            assertEquals(0, queryIntColumn(db, "SELECT position FROM family_children WHERE familyId='u'").single())
        }
    }

    @Test
    fun migrateSkipsDanglingChildId() {
        createVersion4Database().use { db ->
            insertPerson(db, "p", "m")
            insertUnion(db, "u", "p", null, "missing,c")
            insertPerson(db, "c", "m")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).use { db ->
            assertEquals(1, count(db, "family_children WHERE familyId='u'"))
            assertTrue(exists(db, "SELECT 1 FROM family_children WHERE childId='c'"))
        }
    }

    @Test
    fun migrateNullsDanglingPartner() {
        createVersion4Database().use { db ->
            insertPerson(db, "w", "f")
            insertUnion(db, "u", "ghost", "w", "")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5).use { db ->
            assertTrue(exists(db, "SELECT 1 FROM families WHERE id='u' AND partnerAId IS NULL AND partnerBId='w'"))
        }
    }

    @Test
    fun emptyV5GraphFromFamiliesValidatesRead() {
        createVersion4Database().close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5)
        val graph = GenealogyGraphMapper.fromFamilies(emptyList(), emptyList(), emptyList())
        val result = GenealogyValidator().validate(graph, ValidationMode.Read)
        assertTrue(result.errors.isEmpty())
    }

    private fun createVersion4Database(): SupportSQLiteDatabase {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
        helper.createDatabase(TEST_DB, 1).close()
        return helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            false,
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_3_4,
        )
    }

    private fun insertPerson(
        db: SupportSQLiteDatabase,
        id: String,
        gender: String,
        fatherId: String? = null,
        motherId: String? = null,
    ) {
        db.execSQL(
            """
            INSERT INTO people (id, gender, fatherId, motherId, vacant, lastName, birthLastName, firstName, middleName,
                birthDate, deathDate, birthPlace, email, phone, photoFile, notes, createdAtMillis, updatedAtMillis)
            VALUES (?, ?, ?, ?, 0, '', '', '', '', '', '', '', '', '', '', '', 0, 0)
            """.trimIndent(),
            arrayOf(id, gender, fatherId, motherId),
        )
    }

    private fun insertUnion(db: SupportSQLiteDatabase, id: String, a: String?, b: String?, childIds: String) {
        db.execSQL(
            "INSERT INTO unions (id, partnerAId, partnerBId, marriedOn, childIds, unionType) VALUES (?, ?, ?, '', ?, 'marriage')",
            arrayOf(id, a, b, childIds),
        )
    }

    private fun count(db: SupportSQLiteDatabase, where: String): Int {
        db.query("SELECT COUNT(*) FROM $where").use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    private fun exists(db: SupportSQLiteDatabase, sql: String): Boolean {
        db.query(sql).use { return it.moveToFirst() }
    }

    private fun assertTableEmpty(db: SupportSQLiteDatabase, table: String) {
        assertEquals(0, count(db, table))
    }

    private fun queryIntColumn(db: SupportSQLiteDatabase, sql: String): List<Int> {
        val out = mutableListOf<Int>()
        db.query(sql).use { c ->
            while (c.moveToNext()) out.add(c.getInt(0))
        }
        return out
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
