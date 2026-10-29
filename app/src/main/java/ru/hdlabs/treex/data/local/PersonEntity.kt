package ru.hdlabs.treex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val lastName: String = "",
    val birthLastName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val gender: String = "u",
    val birthDate: String = "",
    val deathDate: String = "",
    val birthPlace: String = "",
    val email: String = "",
    val phone: String = "",
    val photoFile: String = "",
    val notes: String = "",
    val vacant: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)
