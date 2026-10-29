package ru.hdlabs.treex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Версия схемы Room растёт только вместе с миграцией.
 * Разрушительной миграции нет: обновление APK поверх старого не очищает древо.
 */
@Database(
    entities = [
        PersonEntity::class,
        FamilyEntity::class,
        FamilyChildEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun familyDao(): FamilyDao
    abstract fun familyChildDao(): FamilyChildDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "treex.db",
                )
                    .addMigrations(
                        DatabaseMigrations.MIGRATION_1_2,
                        DatabaseMigrations.MIGRATION_2_3,
                        DatabaseMigrations.MIGRATION_3_4,
                        DatabaseMigrations.MIGRATION_4_5,
                        DatabaseMigrations.MIGRATION_5_6,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
