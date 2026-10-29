package ru.hdlabs.treex.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "family_children",
    primaryKeys = ["familyId", "childId"],
    foreignKeys = [
        ForeignKey(
            entity = FamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("familyId"),
        Index("childId"),
    ],
)
data class FamilyChildEntity(
    val familyId: String,
    val childId: String,
    val position: Int = 0,
)
