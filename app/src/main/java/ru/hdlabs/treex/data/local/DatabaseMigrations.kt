package ru.hdlabs.treex.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.hdlabs.treex.diagnostic.AppEventLog

object DatabaseMigrations {
    private const val LOG_TAG = "migration-4-5"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE people ADD COLUMN birthLastName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE unions ADD COLUMN marriedOn TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE people ADD COLUMN vacant INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS unions_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    partnerAId TEXT,
                    partnerBId TEXT,
                    marriedOn TEXT NOT NULL DEFAULT '',
                    childIds TEXT NOT NULL DEFAULT '',
                    unionType TEXT NOT NULL DEFAULT 'marriage'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO unions_new (id, partnerAId, partnerBId, marriedOn, childIds, unionType)
                SELECT id, partnerAId, partnerBId, marriedOn, '', 'marriage' FROM unions
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE unions")
            db.execSQL("ALTER TABLE unions_new RENAME TO unions")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrate4To5(db)
        }
    }

    internal fun migrate4To5(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS families (
                id TEXT NOT NULL PRIMARY KEY,
                partnerAId TEXT,
                partnerBId TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS family_children (
                familyId TEXT NOT NULL,
                childId TEXT NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(familyId, childId),
                FOREIGN KEY(familyId) REFERENCES families(id) ON DELETE CASCADE,
                FOREIGN KEY(childId) REFERENCES people(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_family_children_familyId ON family_children(familyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_family_children_childId ON family_children(childId)")

        val personIds = mutableSetOf<String>()
        db.query("SELECT id FROM people").use { cursor ->
            while (cursor.moveToNext()) {
                personIds.add(cursor.getString(0))
            }
        }

        var skippedDanglingChild = 0
        var nulledDanglingPartner = 0
        var unionFamilies = 0
        var childRows = 0

        db.query("SELECT id, partnerAId, partnerBId, childIds FROM unions").use { cursor ->
            while (cursor.moveToNext()) {
                val unionId = cursor.getString(0)
                var partnerA = if (cursor.isNull(1)) null else cursor.getString(1)
                var partnerB = if (cursor.isNull(2)) null else cursor.getString(2)
                val childIdsRaw = if (cursor.isNull(3)) "" else cursor.getString(3)

                if (partnerA != null && partnerA !in personIds) {
                    AppEventLog.warn(LOG_TAG, "dangling partnerA $partnerA in union $unionId, set null")
                    partnerA = null
                    nulledDanglingPartner++
                }
                if (partnerB != null && partnerB !in personIds) {
                    AppEventLog.warn(LOG_TAG, "dangling partnerB $partnerB in union $unionId, set null")
                    partnerB = null
                    nulledDanglingPartner++
                }

                db.execSQL(
                    "INSERT OR REPLACE INTO families (id, partnerAId, partnerBId) VALUES (?, ?, ?)",
                    arrayOf(unionId, partnerA, partnerB),
                )
                unionFamilies++

                val childIds = parseChildIdsCsv(childIdsRaw)
                childIds.forEachIndexed { index, childId ->
                    if (childId !in personIds) {
                        AppEventLog.warn(LOG_TAG, "dangling child $childId in union $unionId, skipped")
                        skippedDanglingChild++
                        return@forEachIndexed
                    }
                    db.execSQL(
                        "INSERT OR IGNORE INTO family_children (familyId, childId, position) VALUES (?, ?, ?)",
                        arrayOf(unionId, childId, index),
                    )
                    childRows++
                }
            }
        }

        var legacyFamilies = 0
        db.query("SELECT id, fatherId, motherId FROM people").use { cursor ->
            while (cursor.moveToNext()) {
                val personId = cursor.getString(0)
                val fatherId = if (cursor.isNull(1)) null else cursor.getString(1)
                val motherId = if (cursor.isNull(2)) null else cursor.getString(2)
                if (fatherId.isNullOrBlank() && motherId.isNullOrBlank()) continue

                val covered = db.query(
                    "SELECT 1 FROM family_children WHERE childId = ? LIMIT 1",
                    arrayOf(personId),
                ).use { it.moveToFirst() }
                if (covered) continue

                var partnerA = fatherId?.takeIf { it.isNotBlank() }
                var partnerB = motherId?.takeIf { it.isNotBlank() }
                if (partnerA != null && partnerA !in personIds) {
                    AppEventLog.warn(LOG_TAG, "dangling legacy father $partnerA for person $personId, set null")
                    partnerA = null
                    nulledDanglingPartner++
                }
                if (partnerB != null && partnerB !in personIds) {
                    AppEventLog.warn(LOG_TAG, "dangling legacy mother $partnerB for person $personId, set null")
                    partnerB = null
                    nulledDanglingPartner++
                }
                if (partnerA == null && partnerB == null) continue

                val legacyId = "legacy-$personId"
                db.execSQL(
                    "INSERT OR IGNORE INTO families (id, partnerAId, partnerBId) VALUES (?, ?, ?)",
                    arrayOf(legacyId, partnerA, partnerB),
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO family_children (familyId, childId, position) VALUES (?, ?, 0)",
                    arrayOf(legacyId, personId),
                )
                legacyFamilies++
                childRows++
            }
        }

        AppEventLog.info(
            LOG_TAG,
            "done: unionFamilies=$unionFamilies legacyFamilies=$legacyFamilies childRows=$childRows " +
                "skipped_dangling_child=$skippedDanglingChild nulled_dangling_partner=$nulledDanglingPartner",
        )
    }

    /** Same rules as [ru.hdlabs.treex.domain.UnionEntity.childIdsList] + dedupe keeping first order. */
    internal fun parseChildIdsCsv(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val distinct = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { id ->
            if (seen.add(id)) distinct.add(id)
        }
        return distinct
    }
}
