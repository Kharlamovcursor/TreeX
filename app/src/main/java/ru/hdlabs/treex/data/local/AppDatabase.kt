package ru.hdlabs.treex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Версия схемы Room растёт только вместе с миграцией.
 * Разрушительной миграции нет: обновление APK поверх старого не очищает древо.
 */
@Database(
    entities = [PersonEntity::class, UnionEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun unionDao(): UnionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE people ADD COLUMN birthLastName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE unions ADD COLUMN marriedOn TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE people ADD COLUMN vacant INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
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

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "treex.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
