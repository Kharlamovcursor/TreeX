package ru.hdlabs.treex.data.kinship

import ru.hdlabs.treex.domain.graph.ValidationIssue

sealed interface KinshipWriteResult {
    data class Success(
        val id: String,
        val familyId: String? = null,
        val warnings: List<ValidationIssue> = emptyList(),
    ) : KinshipWriteResult

    data class Blocked(val errors: List<ValidationIssue>) : KinshipWriteResult

    data class BlockedReason(val code: KinshipBlockCode) : KinshipWriteResult
}
