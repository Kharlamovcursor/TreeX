package ru.hdlabs.treex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families")
    fun observeAll(): Flow<List<FamilyEntity>>

    @Query("SELECT * FROM families")
    suspend fun all(): List<FamilyEntity>

    @Query("SELECT * FROM families WHERE id = :id")
    suspend fun byId(id: String): FamilyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(family: FamilyEntity)

    @Query("DELETE FROM families WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM families")
    suspend fun deleteAll()
}
