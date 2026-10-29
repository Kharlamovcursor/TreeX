package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraph

data class SpouseDraft(
    val personId: String,
    val marriedOn: String = "",
)

data class RelativeAttach(
    val anchorId: String,
    val role: String,
)

fun parseSpouseDrafts(raw: String): List<SpouseDraft> =
    raw.split(',').mapNotNull { part ->
        val token = part.trim()
        if (token.isEmpty()) return@mapNotNull null
        if ('=' in token) {
            SpouseDraft(token.substringBefore('='), token.substringAfter('='))
        } else {
            SpouseDraft(token, "")
        }
    }.distinctBy { it.personId }

fun formatSpouseDrafts(drafts: List<SpouseDraft>): String =
    drafts.distinctBy { it.personId }.joinToString(",") { "${it.personId}=${it.marriedOn}" }

fun mergePerson(previous: PersonEntity, incoming: PersonEntity, touched: Set<String>): PersonEntity {
    fun <T> field(name: String, incomingValue: T, previousValue: T): T =
        if (name in touched) incomingValue else previousValue
    return incoming.copy(
        lastName = field("lastName", incoming.lastName, previous.lastName),
        birthLastName = field("birthLastName", incoming.birthLastName, previous.birthLastName),
        firstName = field("firstName", incoming.firstName, previous.firstName),
        middleName = field("middleName", incoming.middleName, previous.middleName),
        gender = field("gender", incoming.gender, previous.gender),
        birthDate = field("birthDate", incoming.birthDate, previous.birthDate),
        deathDate = field("deathDate", incoming.deathDate, previous.deathDate),
        birthPlace = field("birthPlace", incoming.birthPlace, previous.birthPlace),
        email = field("email", incoming.email, previous.email),
        phone = field("phone", incoming.phone, previous.phone),
        photoFile = field("photo", incoming.photoFile, previous.photoFile),
        notes = field("notes", incoming.notes, previous.notes),
        vacant = field("vacant", incoming.vacant, previous.vacant),
        createdAtMillis = previous.createdAtMillis.takeIf { it > 0 } ?: incoming.createdAtMillis,
    )
}

fun mergeSpouseDrafts(
    previous: List<SpouseDraft>,
    incoming: List<SpouseDraft>,
    replace: Boolean,
): List<SpouseDraft> {
    if (replace) return incoming.distinctBy { it.personId }
    val known = previous.map { it.personId }.toSet()
    return (previous + incoming.filter { it.personId !in known }).distinctBy { it.personId }
}

/** Фамилия для новых карточек родителей: у женщины — девичья (или текущая), у мужчины — его фамилия. */
fun suggestedParentFamilyName(host: PersonEntity): String = when (host.gender) {
    "f" -> host.birthLastName.trim().ifBlank { host.lastName.trim() }
    else -> host.lastName.trim()
}

fun spouseDraftsOf(
    graph: GenealogyGraph,
    personId: String,
    families: List<FamilyEntity> = emptyList(),
): List<SpouseDraft> {
    val meta = families.associateBy { it.id }
    return graph.familiesOf(personId).mapNotNull { fam ->
        val otherId = when (personId) {
            fam.partnerAId -> fam.partnerBId
            fam.partnerBId -> fam.partnerAId
            else -> null
        } ?: return@mapNotNull null
        SpouseDraft(otherId, meta[fam.id]?.marriedOn.orEmpty())
    }.distinctBy { it.personId }
}
