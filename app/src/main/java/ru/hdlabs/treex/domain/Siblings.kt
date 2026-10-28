package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity

data class SiblingGroups(
    val full: List<PersonEntity>,
    val half: List<PersonEntity>,
)

fun siblingGroupsOf(person: PersonEntity, people: List<PersonEntity>): SiblingGroups {
    val full = mutableListOf<PersonEntity>()
    val half = mutableListOf<PersonEntity>()
    people.forEach { other ->
        if (other.id == person.id || other.vacant || person.vacant) return@forEach
        when (siblingKind(person, other)) {
            SiblingKind.Full -> full += other
            SiblingKind.Half -> half += other
            SiblingKind.None -> Unit
        }
    }
    val byBirth = compareBy<PersonEntity>({ it.birthDate }, { it.lastName }, { it.firstName }, { it.id })
    return SiblingGroups(full.sortedWith(byBirth), half.sortedWith(byBirth))
}

private enum class SiblingKind { Full, Half, None }

private fun siblingKind(person: PersonEntity, other: PersonEntity): SiblingKind {
    val sharesFather = person.fatherId != null && person.fatherId == other.fatherId
    val sharesMother = person.motherId != null && person.motherId == other.motherId
    return when {
        sharesFather && sharesMother -> SiblingKind.Full
        sharesFather || sharesMother -> SiblingKind.Half
        else -> SiblingKind.None
    }
}

/**
 * Кому ещё проставить нового родителя: только братьям и сёстрам с тем же уже известным вторым родителем.
 * Если второго родителя нет, никого больше не трогаем — иначе отец запишется всем людям без родителей.
 */
fun siblingsMissingParent(
    people: List<PersonEntity>,
    anchor: PersonEntity,
    newParentId: String,
    asFather: Boolean,
): List<PersonEntity> {
    val sharedOther = if (asFather) anchor.motherId else anchor.fatherId
    if (sharedOther.isNullOrBlank()) return emptyList()
    return people.filter { person ->
        person.id != anchor.id &&
            person.id != newParentId &&
            !person.vacant &&
            if (asFather) {
                person.fatherId == null && person.motherId == sharedOther
            } else {
                person.motherId == null && person.fatherId == sharedOther
            }
    }
}

fun halfSiblingCaption(person: PersonEntity, other: PersonEntity): String {
    val sharesFather = person.fatherId != null && person.fatherId == other.fatherId
    val sharesMother = person.motherId != null && person.motherId == other.motherId
    return when {
        sharesFather && !sharesMother -> "общий отец"
        sharesMother && !sharesFather -> "общая мать"
        else -> "сводный родственник"
    }
}
