package ru.hdlabs.treex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unions")
data class UnionEntity(
    @PrimaryKey val id: String,
    val partnerAId: String,
    val partnerBId: String,
    val marriedOn: String = "",
)
