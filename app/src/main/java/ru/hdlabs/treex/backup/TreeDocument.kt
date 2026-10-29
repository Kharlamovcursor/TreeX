package ru.hdlabs.treex.backup

import kotlinx.serialization.Serializable

@Serializable
data class TreeDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAtMillis: Long = 0L,
    val people: List<PersonRecord> = emptyList(),
    /** Wire-format projection of [families] for backward compatibility; not source of truth since Room v6. */
    val unions: List<UnionRecord> = emptyList(),
    /** Canonical kinship hub list when present in JSON; preferred over [unions] on import. */
    val families: List<FamilyRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = "hdlabs-tree"
        const val VERSION = 2
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
    val vacant: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class FamilyRecord(
    val id: String,
    val partnerAId: String? = null,
    val partnerBId: String? = null,
    val marriedOn: String? = null,
    val unionType: String? = null,
    val childIds: List<String> = emptyList(),
)

@Serializable
data class UnionRecord(
    val id: String,
    val partnerAId: String? = null,
    val partnerBId: String? = null,
    val marriedOn: String = "",
    val childIds: List<String> = emptyList(),
    val unionType: String = "marriage",
)
