package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.UnionEntity

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

fun spouseDraftsOf(unions: List<UnionEntity>, personId: String): List<SpouseDraft> =
    unions.mapNotNull { union ->
        val other = when (personId) {
            union.partnerAId -> union.partnerBId
            union.partnerBId -> union.partnerAId
            else -> null
        }
        other?.let { SpouseDraft(it, union.marriedOn) }
    }.distinctBy { it.personId }
