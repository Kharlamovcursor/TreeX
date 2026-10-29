package ru.hdlabs.treex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unions")
data class UnionEntity(
    @PrimaryKey val id: String,
    val partnerAId: String? = null,
    val partnerBId: String? = null,
    val marriedOn: String = "",
    /** Comma-separated child person ids (ER: children belong to the union). */
    val childIds: String = "",
    val unionType: String = "marriage",
)
