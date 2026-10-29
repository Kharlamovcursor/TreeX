package ru.hdlabs.treex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val partnerAId: String? = null,
    val partnerBId: String? = null,
    val marriedOn: String? = null,
    val unionType: String? = null,
)
