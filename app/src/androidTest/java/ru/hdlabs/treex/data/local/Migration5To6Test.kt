package ru.hdlabs.treex.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java,
    )

    @Test
    fun migrateEmptyV5ToV6() {
        createVersion5Database().close()
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            assertTableEmpty(db, "families")
            assertTableEmpty(db, "family_children")
            assertSchemaV6(db)
        }
    }

    @Test
    fun migrateFamiliesAndChildrenPreserved() {
        createVersion5Database().use { db ->
            insertPerson(db, "h", "m")
            insertPerson(db, "w", "f")
            insertPerson(db, "c", "m")
            insertFamily(db, "u1", "h", "w", marriedOn = null, unionType = null)
            insertFamilyChild(db, "u1", "c", 0)
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            assertEquals(1, count(db, "families"))
            assertEquals(1, count(db, "family_children"))
            val graph = graphFromDb(db)
            val fam = graph.familyWhereChild("c")
            assertEquals("u1", fam?.id)
            assertEquals(setOf("h", "w"), graph.parentsOf("c").map { it.id }.toSet())
            assertNull(queryString(db, "SELECT marriedOn FROM families WHERE id='u1'"))
            assertNull(queryString(db, "SELECT unionType FROM families WHERE id='u1'"))
        }
    }

    @Test
    fun marriedOnColumnExistsNullForExistingRows() {
        createVersion5Database().use { db ->
            insertPerson(db, "a", "m")
            insertPerson(db, "b", "f")
            insertFamily(db, "f1", "a", "b")
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            assertTrue(columnExists(db, "families", "marriedOn"))
            assertTrue(columnExists(db, "families", "unionType"))
            assertNull(queryString(db, "SELECT marriedOn FROM families WHERE id='f1'"))
        }
    }

    @Test
    fun legacyUnionsTableDroppedFamiliesKept() {
        createVersion5Database().use { db ->
            insertPerson(db, "h", "m")
            insertPerson(db, "w", "f")
            insertPerson(db, "c", "m")
            insertFamily(db, "u1", "h", "w")
            insertFamilyChild(db, "u1", "c", 0)
            insertUnion(db, "legacy-u", "h", "w", "c")
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            assertFalse(tableExists(db, "unions"))
            assertEquals(1, count(db, "families WHERE id='u1'"))
            assertEquals(1, count(db, "family_children WHERE familyId='u1' AND childId='c'"))
        }
    }

    @Test
    fun danglingPartnerSurvivesMigration() {
        createVersion5Database().use { db ->
            insertPerson(db, "w", "f")
            insertFamily(db, "u", null, "w")
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            assertTrue(exists(db, "SELECT 1 FROM families WHERE id='u' AND partnerAId IS NULL AND partnerBId='w'"))
        }
    }

    @Test
    fun pragmaV6Schema() {
        createVersion5Database().use { db ->
            insertPerson(db, "p", "m", fatherId = "f", motherId = "m")
            insertUnion(db, "u", "f", "m", "")
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).use { db ->
            assertSchemaV6(db)
        }
    }

    private fun assertSchemaV6(db: SupportSQLiteDatabase) {
        assertFalse(tableExists(db, "unions"))
        assertFalse(columnExists(db, "people", "fatherId"))
        assertFalse(columnExists(db, "people", "motherId"))
        assertTrue(columnExists(db, "families", "marriedOn"))
        assertTrue(columnExists(db, "families", "unionType"))
    }

    private fun graphFromDb(db: SupportSQLiteDatabase): ru.hdlabs.treex.domain.graph.GenealogyGraph {
        val people = mutableListOf<PersonEntity>()
        db.query("SELECT id, gender, vacant, lastName, birthLastName, firstName, middleName, birthDate, deathDate, birthPlace, email, phone, photoFile, notes, createdAtMillis, updatedAtMillis FROM people").use { c ->
            while (c.moveToNext()) {
                people += PersonEntity(
                    id = c.getString(0),
                    gender = c.getString(1),
                    vacant = c.getInt(2) != 0,
                    lastName = c.getString(3),
                    birthLastName = c.getString(4),
                    firstName = c.getString(5),
                    middleName = c.getString(6),
                    birthDate = c.getString(7),
                    deathDate = c.getString(8),
                    birthPlace = c.getString(9),
                    email = c.getString(10),
                    phone = c.getString(11),
                    photoFile = c.getString(12),
                    notes = c.getString(13),
                    createdAtMillis = c.getLong(14),
                    updatedAtMillis = c.getLong(15),
                )
            }
        }
        val families = mutableListOf<FamilyEntity>()
        db.query("SELECT id, partnerAId, partnerBId, marriedOn, unionType FROM families").use { c ->
            while (c.moveToNext()) {
                families += FamilyEntity(
                    id = c.getString(0),
                    partnerAId = if (c.isNull(1)) null else c.getString(1),
                    partnerBId = if (c.isNull(2)) null else c.getString(2),
                    marriedOn = if (c.isNull(3)) null else c.getString(3),
                    unionType = if (c.isNull(4)) null else c.getString(4),
                )
            }
        }
        val children = mutableListOf<FamilyChildEntity>()
        db.query("SELECT familyId, childId, position FROM family_children ORDER BY familyId, position").use { c ->
            while (c.moveToNext()) {
                children += FamilyChildEntity(c.getString(0), c.getString(1), c.getInt(2))
            }
        }
        return GenealogyGraphMapper.fromFamilies(people, families, children)
    }

    private fun createVersion5Database(): SupportSQLiteDatabase {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
        helper.createDatabase(TEST_DB, 1).close()
        return helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            false,
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_3_4,
            DatabaseMigrations.MIGRATION_4_5,
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

    private fun insertFamily(
        db: SupportSQLiteDatabase,
        id: String,
        partnerA: String?,
        partnerB: String?,
        marriedOn: String? = null,
        unionType: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO families (id, partnerAId, partnerBId) VALUES (?, ?, ?)",
            arrayOf(id, partnerA, partnerB),
        )
    }

    private fun insertFamilyChild(db: SupportSQLiteDatabase, familyId: String, childId: String, position: Int) {
        db.execSQL(
            "INSERT INTO family_children (familyId, childId, position) VALUES (?, ?, ?)",
            arrayOf(familyId, childId, position),
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

    private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean {
        db.query(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(name),
        ).use { return it.moveToFirst() }
    }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info($table)").use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == column) return true
            }
        }
        return false
    }

    private fun queryString(db: SupportSQLiteDatabase, sql: String): String? {
        db.query(sql).use { c ->
            if (!c.moveToFirst() || c.isNull(0)) return null
            return c.getString(0)
        }
    }

    companion object {
        private const val TEST_DB = "migration-5-6-test"
    }
}
