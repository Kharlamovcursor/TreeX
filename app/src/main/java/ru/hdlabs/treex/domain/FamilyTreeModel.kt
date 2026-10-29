package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Карточка человека (узел графа). Родство к детям — только через [FamilyUnion]. */
data class FamilyPerson(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val maidenName: String = "",
    val gender: FamilyGender = FamilyGender.Other,
    val birthDate: String = "",
    val deathDate: String = "",
    val isDead: Boolean = false,
)

enum class FamilyGender { Male, Female, Other }

enum class FamilyUnionType { Marriage, Divorce, Cohabitation }

/**
 * Союз (семья): единственный способ связать партнёров с детьми.
 * [partner1Id]/[partner2Id] могут быть null (второй родитель ещё не добавлен).
 */
data class FamilyUnion(
    val id: String,
    val partner1Id: String? = null,
    val partner2Id: String? = null,
    val childrenIds: List<String> = emptyList(),
    val type: FamilyUnionType = FamilyUnionType.Marriage,
    val marriedOn: String = "",
)

data class FamilyTreeState(
    val people: List<FamilyPerson> = emptyList(),
    val unions: List<FamilyUnion> = emptyList(),
) {
    fun person(id: String): FamilyPerson? = people.find { it.id == id }
    fun union(id: String): FamilyUnion? = unions.find { it.id == id }
}

fun PersonEntity.toFamilyPerson(): FamilyPerson {
    val dead = deathDate.isNotBlank()
    return FamilyPerson(
        id = id,
        firstName = firstName,
        lastName = lastName,
        maidenName = birthLastName,
        gender = when (gender) {
            "m" -> FamilyGender.Male
            "f" -> FamilyGender.Female
            else -> FamilyGender.Other
        },
        birthDate = birthDate,
        deathDate = deathDate,
        isDead = dead,
    )
}

fun FamilyPerson.toPersonEntity(
    previous: PersonEntity? = null,
): PersonEntity = PersonEntity(
    id = id,
    lastName = lastName,
    birthLastName = if (gender == FamilyGender.Female) maidenName else "",
    firstName = firstName,
    middleName = previous?.middleName.orEmpty(),
    gender = when (gender) {
        FamilyGender.Male -> "m"
        FamilyGender.Female -> "f"
        FamilyGender.Other -> "u"
    },
    birthDate = birthDate,
    deathDate = deathDate,
    birthPlace = previous?.birthPlace.orEmpty(),
    email = previous?.email.orEmpty(),
    phone = previous?.phone.orEmpty(),
    photoFile = previous?.photoFile.orEmpty(),
    notes = previous?.notes.orEmpty(),
    vacant = previous?.vacant ?: false,
    createdAtMillis = previous?.createdAtMillis ?: 0L,
    updatedAtMillis = previous?.updatedAtMillis ?: 0L,
)

fun UnionEntity.toFamilyUnion(): FamilyUnion = FamilyUnion(
    id = id,
    partner1Id = partnerAId,
    partner2Id = partnerBId,
    childrenIds = childIdsList(),
    type = when (unionType) {
        "divorce" -> FamilyUnionType.Divorce
        "cohabitation" -> FamilyUnionType.Cohabitation
        else -> FamilyUnionType.Marriage
    },
    marriedOn = marriedOn,
)

fun FamilyUnion.toUnionEntity(): UnionEntity = UnionEntity(
    id = id,
    partnerAId = partner1Id,
    partnerBId = partner2Id,
    marriedOn = marriedOn,
    childIds = childrenIds.joinToString(","),
    unionType = when (type) {
        FamilyUnionType.Divorce -> "divorce"
        FamilyUnionType.Cohabitation -> "cohabitation"
        FamilyUnionType.Marriage -> "marriage"
    },
)

fun familyTreeStateFrom(
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
): FamilyTreeState = FamilyTreeState(
    people = people.filter { !it.vacant }.map { it.toFamilyPerson() },
    unions = unions.map { it.toFamilyUnion() },
)
