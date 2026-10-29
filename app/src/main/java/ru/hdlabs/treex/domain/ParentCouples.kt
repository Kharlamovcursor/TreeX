package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Фамилия для новых карточек родителей: у женщины — девичья (или текущая), у мужчины — его фамилия. */
fun suggestedParentFamilyName(host: PersonEntity): String = when (host.gender) {
    "f" -> host.birthLastName.trim().ifBlank { host.lastName.trim() }
    else -> host.lastName.trim()
}

fun parentCouplePairs(people: List<PersonEntity>): List<Pair<String, String>> =
    people.mapNotNull { person ->
        val fatherId = person.fatherId ?: return@mapNotNull null
        val motherId = person.motherId ?: return@mapNotNull null
        if (fatherId == motherId) return@mapNotNull null
        minOf(fatherId, motherId) to maxOf(fatherId, motherId)
    }.distinct()

fun mergeInferredParentUnions(
    existing: List<UnionEntity>,
    people: List<PersonEntity>,
): List<UnionEntity> {
    val known = existing.mapNotNull { union ->
        val a = union.partnerAId ?: return@mapNotNull null
        val b = union.partnerBId ?: return@mapNotNull null
        minOf(a, b) to maxOf(a, b)
    }.toSet()
    val ids = people.map { it.id }.toSet()
    val added = parentCouplePairs(people).mapNotNull { (a, b) ->
        if (a !in ids || b !in ids) return@mapNotNull null
        if (a to b in known) return@mapNotNull null
        UnionEntity(stableUnionId(a, b), a, b)
    }
    val merged = if (added.isEmpty()) existing else (existing + added).distinctBy { it.id }
    return UnionEr.reconcileChildMembership(people, merged)
}
