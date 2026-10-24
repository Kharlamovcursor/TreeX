package ru.hdlabs.treex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnionDao {
    @Query("SELECT * FROM unions")
    fun observeAll(): Flow<List<UnionEntity>>

    @Query("SELECT * FROM unions")
    suspend fun all(): List<UnionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(unions: List<UnionEntity>)

    @Query("DELETE FROM unions")
    suspend fun deleteAll()

    @Query("DELETE FROM unions WHERE partnerAId = :id OR partnerBId = :id")
    suspend fun deleteInvolving(id: String)
}
