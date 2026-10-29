package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

data class SiblingGroups(
    val full: List<PersonEntity>,
    val half: List<PersonEntity>,
)

fun siblingGroupsOf(
    person: PersonEntity,
    people: List<PersonEntity>,
    unions: List<UnionEntity> = emptyList(),
): SiblingGroups {
    val parentMap = UnionEr.parentPairsMap(people.filter { !it.vacant }, unions)
    val full = mutableListOf<PersonEntity>()
    val half = mutableListOf<PersonEntity>()
    people.forEach { other ->
        if (other.id == person.id || other.vacant || person.vacant) return@forEach
        when (siblingKind(person, other, parentMap)) {
            SiblingKind.Full -> full += other
            SiblingKind.Half -> half += other
            SiblingKind.None -> Unit
        }
    }
    val byBirth = compareBy<PersonEntity>({ it.birthDate }, { it.lastName }, { it.firstName }, { it.id })
    return SiblingGroups(full.sortedWith(byBirth), half.sortedWith(byBirth))
}

private enum class SiblingKind { Full, Half, None }

private fun siblingKind(
    person: PersonEntity,
    other: PersonEntity,
    parentMap: Map<String, Pair<String?, String?>>,
): SiblingKind {
    val (pf, pm) = parentMap[person.id] ?: (null to null)
    val (of, om) = parentMap[other.id] ?: (null to null)
    val sharesFather = pf != null && pf == of
    val sharesMother = pm != null && pm == om
    return when {
        sharesFather && sharesMother -> SiblingKind.Full
        sharesFather || sharesMother -> SiblingKind.Half
        else -> SiblingKind.None
    }
}

/**
 * Кому ещё проставить нового родителя: только братьям и сёстрам с тем же уже известным вторым родителем.
 */
private fun childPartners(unions: List<UnionEntity>, childId: String): Pair<String?, String?> {
    val union = unions.find { childId in it.childIdsList() } ?: return null to null
    return union.partnerAId to union.partnerBId
}

fun siblingsMissingParent(
    people: List<PersonEntity>,
    anchor: PersonEntity,
    newParentId: String,
    asFather: Boolean,
    unions: List<UnionEntity> = emptyList(),
): List<PersonEntity> {
    val byId = people.associateBy { it.id }
    fun fatherSlot(childId: String): String? {
        val (a, b) = childPartners(unions, childId)
        return when {
            a != null && byId[a]?.gender == "m" -> a
            b != null && byId[b]?.gender == "m" -> b
            else -> null
        }
    }
    fun motherSlot(childId: String): String? {
        val (a, b) = childPartners(unions, childId)
        return when {
            a != null && byId[a]?.gender == "f" -> a
            b != null && byId[b]?.gender == "f" -> b
            else -> null
        }
    }
    val sharedOther = if (asFather) motherSlot(anchor.id) else fatherSlot(anchor.id)
    if (sharedOther.isNullOrBlank()) return emptyList()
    return people.filter { person ->
        person.id != anchor.id &&
            person.id != newParentId &&
            !person.vacant &&
            run {
                if (asFather) fatherSlot(person.id) == null && motherSlot(person.id) == sharedOther
                else motherSlot(person.id) == null && fatherSlot(person.id) == sharedOther
            }
    }
}

fun halfSiblingCaption(
    person: PersonEntity,
    other: PersonEntity,
    unions: List<UnionEntity> = emptyList(),
    people: List<PersonEntity> = emptyList(),
): String {
    val byId = (if (people.isEmpty()) listOf(person, other) else people).associateBy { it.id }
    fun fatherId(childId: String): String? {
        val (a, b) = childPartners(unions, childId)
        return when {
            a != null && byId[a]?.gender == "m" -> a
            b != null && byId[b]?.gender == "m" -> b
            else -> a
        }
    }
    fun motherId(childId: String): String? {
        val (a, b) = childPartners(unions, childId)
        return when {
            a != null && byId[a]?.gender == "f" -> a
            b != null && byId[b]?.gender == "f" -> b
            else -> b
        }
    }
    val pf = fatherId(person.id)
    val pm = motherId(person.id)
    val of = fatherId(other.id)
    val om = motherId(other.id)
    val sharesFather = pf != null && pf == of
    val sharesMother = pm != null && pm == om
    return when {
        sharesFather && !sharesMother -> "общий отец"
        sharesMother && !sharesFather -> "общая мать"
        else -> "сводный родственник"
    }
}
