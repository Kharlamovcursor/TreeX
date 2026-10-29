package ru.hdlabs.treex.domain.graph

/**
 * Person node in the genealogy read-model. Kinship edges live only in [Family] records
 * inside [GenealogyGraph], not on this type.
 */
data class Person(
    val id: String,
    val fullName: String,
    val gender: Gender,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val avatarUrl: String? = null,
)

/** Display gender; parent roles («отец/мать») are derived at UI time from this enum. */
enum class Gender {
    MALE,
    FEMALE,
    UNKNOWN,
}
