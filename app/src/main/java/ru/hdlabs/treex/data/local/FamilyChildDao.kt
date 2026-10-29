package ru.hdlabs.treex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyChildDao {
    @Query("SELECT * FROM family_children ORDER BY familyId, position")
    fun observeAll(): Flow<List<FamilyChildEntity>>

    @Query("SELECT * FROM family_children ORDER BY familyId, position")
    suspend fun all(): List<FamilyChildEntity>

    @Query("SELECT * FROM family_children WHERE familyId = :familyId ORDER BY position")
    suspend fun childrenOf(familyId: String): List<FamilyChildEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<FamilyChildEntity>)

    @Query("DELETE FROM family_children WHERE familyId = :familyId")
    suspend fun deleteByFamily(familyId: String)

    @Query("DELETE FROM family_children")
    suspend fun deleteAll()
}
