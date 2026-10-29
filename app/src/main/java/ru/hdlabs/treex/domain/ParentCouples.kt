package ru.hdlabs.treex.domain

import kotlin.DeprecationLevel
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Фамилия для новых карточек родителей: у женщины — девичья (или текущая), у мужчины — его фамилия. */
fun suggestedParentFamilyName(host: PersonEntity): String = when (host.gender) {
    "f" -> host.birthLastName.trim().ifBlank { host.lastName.trim() }
    else -> host.lastName.trim()
}

@Deprecated("Use families graph", level = DeprecationLevel.WARNING)
fun parentCouplePairs(people: List<PersonEntity>): List<Pair<String, String>> = emptyList()

fun mergeInferredParentUnions(
    existing: List<UnionEntity>,
    people: List<PersonEntity>,
): List<UnionEntity> = UnionEr.reconcileChildMembership(people, existing)
