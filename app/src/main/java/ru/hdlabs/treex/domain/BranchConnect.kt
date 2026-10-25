package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

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

fun planBranchConnect(
    subject: PersonEntity,
    target: PersonEntity,
    mode: BranchConnectMode,
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
    nowMillis: Long = System.currentTimeMillis(),
): BranchConnectPlan {
    require(subject.id != target.id) { "Нельзя связать карточку с самой собой" }
    require(!subject.vacant && !target.vacant) { "Пустые места нельзя связывать с древом" }
    val parents = people.associate { it.id to (it.fatherId to it.motherId) }.toMutableMap()
    val updates = mutableListOf<PersonEntity>()
    fun write(person: PersonEntity, fatherId: String?, motherId: String?) {
        if (TreeRules.createsCycle(parents, person.id, fatherId, motherId)) {
            throw IllegalArgumentException("Такая связь замыкает круг в древе")
        }
        val next = person.copy(fatherId = fatherId, motherId = motherId, updatedAtMillis = nowMillis)
        parents[person.id] = fatherId to motherId
        updates += next
    }
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
            if (target.fatherId != null) throw IllegalArgumentException("У выбранного человека уже указан отец")
            write(target, subject.id, target.motherId)
        }
        BranchConnectMode.SUBJECT_AS_MOTHER -> {
            if (!acceptsAsParent("mother", subject)) {
                throw IllegalArgumentException("Для матери нужна женская карточка")
            }
            if (target.motherId != null) throw IllegalArgumentException("У выбранного человека уже указана мать")
            write(target, target.fatherId, subject.id)
        }
        BranchConnectMode.TARGET_AS_FATHER -> {
            if (!acceptsAsParent("father", target)) {
                throw IllegalArgumentException("Для отца нужна мужская карточка")
            }
            if (subject.fatherId != null) throw IllegalArgumentException("У этой карточки уже указан отец")
            write(subject, target.id, subject.motherId)
        }
        BranchConnectMode.TARGET_AS_MOTHER -> {
            if (!acceptsAsParent("mother", target)) {
                throw IllegalArgumentException("Для матери нужна женская карточка")
            }
            if (subject.motherId != null) throw IllegalArgumentException("У этой карточки уже указана мать")
            write(subject, subject.fatherId, target.id)
        }
    }
    return BranchConnectPlan(updates.distinctBy { it.id }, union)
}
