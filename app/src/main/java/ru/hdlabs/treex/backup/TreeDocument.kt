package ru.hdlabs.treex.backup

import kotlinx.serialization.Serializable

@Serializable
data class TreeDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtMillis: Long = 0L,
    val people: List<PersonRecord> = emptyList(),
    val unions: List<UnionRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = "hdlabs-tree"
        const val VERSION = 1
    }
}

@Serializable
data class PersonRecord(
    val id: String,
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
    val fatherId: String? = null,
    val motherId: String? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class UnionRecord(
    val id: String,
    val partnerAId: String,
    val partnerBId: String,
    val marriedOn: String = "",
)
