package ru.hdlabs.treex.domain

import ru.hdlabs.treex.backup.UnionRecord
import ru.hdlabs.treex.data.local.PersonEntity

enum class BranchConnectMode {
    SPOUSE,
    SUBJECT_AS_FATHER,
    SUBJECT_AS_MOTHER,
    TARGET_AS_FATHER,
    TARGET_AS_MOTHER,
}

fun branchConnectPrompt(mode: BranchConnectMode): String = when (mode) {
    BranchConnectMode.SPOUSE -> "Сделать супругами"
    BranchConnectMode.SUBJECT_AS_FATHER -> "Выбранный человек — отец этой карточки"
    BranchConnectMode.SUBJECT_AS_MOTHER -> "Выбранный человек — мать этой карточки"
    BranchConnectMode.TARGET_AS_FATHER -> "Эта карточка — отец выбранного"
    BranchConnectMode.TARGET_AS_MOTHER -> "Эта карточка — мать выбранного"
}

data class BranchConnectPlan(
    val peopleUpdates: List<PersonEntity>,
    val unionPartners: Pair<String, String>?,
)

private fun parentPair(unions: List<UnionRecord>, childId: String): Pair<String?, String?> {
    val union = unions.find { childId in it.childIds } ?: return null to null
    return union.partnerAId to union.partnerBId
}

fun planBranchConnect(
    subject: PersonEntity,
    target: PersonEntity,
    mode: BranchConnectMode,
    people: List<PersonEntity>,
    unions: List<UnionRecord>,
    nowMillis: Long = System.currentTimeMillis(),
): BranchConnectPlan {
    require(subject.id != target.id) { "Нельзя связать карточку с самой собой" }
    require(!subject.vacant && !target.vacant) { "Пустые места нельзя связывать с древом" }
    var union: Pair<String, String>? = null
    when (mode) {
        BranchConnectMode.SPOUSE -> {
            if (!acceptsAsSpouse(subject.gender, target.gender)) {
                throw IllegalArgumentException("По полу эти карточки нельзя сделать супругами")
            }
            val exists = unions.any { setOf(it.partnerAId, it.partnerBId) == setOf(subject.id, target.id) }
            if (!exists) union = subject.id to target.id
        }
        BranchConnectMode.SUBJECT_AS_FATHER -> {
            if (!acceptsAsParent("father", subject)) {
                throw IllegalArgumentException("Для отца нужна мужская карточка")
            }
            if (parentPair(unions, target.id).first != null) {
                throw IllegalArgumentException("У выбранного человека уже указан отец")
            }
        }
        BranchConnectMode.SUBJECT_AS_MOTHER -> {
            if (!acceptsAsParent("mother", subject)) {
                throw IllegalArgumentException("Для матери нужна женская карточка")
            }
            if (parentPair(unions, target.id).second != null) {
                throw IllegalArgumentException("У выбранного человека уже указана мать")
            }
        }
        BranchConnectMode.TARGET_AS_FATHER -> {
            if (!acceptsAsParent("father", target)) {
                throw IllegalArgumentException("Для отца нужна мужская карточка")
            }
            if (parentPair(unions, subject.id).first != null) {
                throw IllegalArgumentException("У этой карточки уже указан отец")
            }
        }
        BranchConnectMode.TARGET_AS_MOTHER -> {
            if (!acceptsAsParent("mother", target)) {
                throw IllegalArgumentException("Для матери нужна женская карточка")
            }
            if (parentPair(unions, subject.id).second != null) {
                throw IllegalArgumentException("У этой карточки уже указана мать")
            }
        }
    }
    return BranchConnectPlan(emptyList(), union)
}
