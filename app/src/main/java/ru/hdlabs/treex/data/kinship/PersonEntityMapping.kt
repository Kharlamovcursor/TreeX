package ru.hdlabs.treex.data.kinship

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.capitalizePersonName
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person
import java.util.UUID

internal fun newPersonId(): String = UUID.randomUUID().toString()

internal fun PersonEntity.toGraphPerson(): Person {
    val g = when (gender) {
        "m" -> Gender.MALE
        "f" -> Gender.FEMALE
        else -> Gender.UNKNOWN
    }
    return Person(
        id = id,
        fullName = fullName(),
        gender = g,
        birthDate = birthDate.takeIf { it.isNotBlank() },
        deathDate = deathDate.takeIf { it.isNotBlank() },
        avatarUrl = photoFile.takeIf { it.isNotBlank() },
    )
}

internal fun Person.toEntity(existing: PersonEntity? = null): PersonEntity {
    val parts = fullName.trim().split(" ").filter { it.isNotBlank() }
    val last = parts.dropLast(2).joinToString(" ").ifBlank { parts.firstOrNull().orEmpty() }
    val first = when {
        parts.size >= 2 -> parts[parts.size - 2]
        parts.size == 1 -> parts[0]
        else -> ""
    }
    val middle = parts.lastOrNull().takeIf { parts.size >= 3 }.orEmpty()
    return PersonEntity(
        id = id,
        lastName = capitalizePersonName(existing?.lastName?.takeIf { last.isBlank() } ?: last),
        birthLastName = existing?.birthLastName.orEmpty(),
        firstName = capitalizePersonName(existing?.firstName?.takeIf { first.isBlank() } ?: first),
        middleName = capitalizePersonName(existing?.middleName?.takeIf { middle.isBlank() } ?: middle),
        gender = when (gender) {
            Gender.MALE -> "m"
            Gender.FEMALE -> "f"
            Gender.UNKNOWN -> existing?.gender ?: "u"
        },
        birthDate = birthDate.orEmpty(),
        deathDate = deathDate.orEmpty(),
        birthPlace = existing?.birthPlace.orEmpty(),
        email = existing?.email.orEmpty(),
        phone = existing?.phone.orEmpty(),
        photoFile = avatarUrl ?: existing?.photoFile.orEmpty(),
        notes = existing?.notes.orEmpty(),
        fatherId = existing?.fatherId,
        motherId = existing?.motherId,
        vacant = existing?.vacant ?: false,
        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        updatedAtMillis = System.currentTimeMillis(),
    )
}

internal fun PersonEntity.toPerson(): Person = toGraphPerson()

internal fun draftToPerson(entity: PersonEntity): Person = entity.toGraphPerson()
