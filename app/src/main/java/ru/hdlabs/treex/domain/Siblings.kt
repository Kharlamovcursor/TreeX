package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.Gender

data class SiblingGroups(
    val full: List<PersonEntity>,
    val half: List<PersonEntity>,
)

fun siblingGroupsOf(
    person: PersonEntity,
    people: List<PersonEntity>,
    graph: GenealogyGraph,
): SiblingGroups {
    val full = mutableListOf<PersonEntity>()
    val half = mutableListOf<PersonEntity>()
    people.forEach { other ->
        if (other.id == person.id || other.vacant || person.vacant) return@forEach
        when (siblingKind(person, other, graph)) {
            SiblingKind.Full -> full += other
            SiblingKind.Half -> half += other
            SiblingKind.None -> Unit
        }
    }
    val byBirth = compareBy<PersonEntity>({ it.birthDate }, { it.lastName }, { it.firstName }, { it.id })
    return SiblingGroups(full.sortedWith(byBirth), half.sortedWith(byBirth))
}

private enum class SiblingKind { Full, Half, None }

private fun siblingKind(person: PersonEntity, other: PersonEntity, graph: GenealogyGraph): SiblingKind {
    val pf = fatherSlot(graph, person.id)
    val pm = motherSlot(graph, person.id)
    val of = fatherSlot(graph, other.id)
    val om = motherSlot(graph, other.id)
    val sharesFather = pf != null && pf == of
    val sharesMother = pm != null && pm == om
    return when {
        sharesFather && sharesMother -> SiblingKind.Full
        sharesFather || sharesMother -> SiblingKind.Half
        else -> SiblingKind.None
    }
}

private fun fatherSlot(graph: GenealogyGraph, childId: String): String? {
    val fam = graph.familyWhereChild(childId) ?: return null
    return when {
        graph.person(fam.partnerAId.orEmpty())?.gender == Gender.MALE -> fam.partnerAId
        graph.person(fam.partnerBId.orEmpty())?.gender == Gender.MALE -> fam.partnerBId
        else -> fam.partnerAId
    }
}

private fun motherSlot(graph: GenealogyGraph, childId: String): String? {
    val fam = graph.familyWhereChild(childId) ?: return null
    return when {
        graph.person(fam.partnerAId.orEmpty())?.gender == Gender.FEMALE -> fam.partnerAId
        graph.person(fam.partnerBId.orEmpty())?.gender == Gender.FEMALE -> fam.partnerBId
        else -> fam.partnerBId
    }
}

fun siblingsMissingParent(
    people: List<PersonEntity>,
    anchor: PersonEntity,
    newParentId: String,
    asFather: Boolean,
    graph: GenealogyGraph,
): List<PersonEntity> {
    val sharedOther = if (asFather) motherSlot(graph, anchor.id) else fatherSlot(graph, anchor.id)
    if (sharedOther.isNullOrBlank()) return emptyList()
    return people.filter { person ->
        person.id != anchor.id &&
            person.id != newParentId &&
            !person.vacant &&
            run {
                if (asFather) fatherSlot(graph, person.id) == null && motherSlot(graph, person.id) == sharedOther
                else motherSlot(graph, person.id) == null && fatherSlot(graph, person.id) == sharedOther
            }
    }
}

fun halfSiblingCaption(
    person: PersonEntity,
    other: PersonEntity,
    graph: GenealogyGraph,
): String {
    val pf = fatherSlot(graph, person.id)
    val pm = motherSlot(graph, person.id)
    val of = fatherSlot(graph, other.id)
    val om = motherSlot(graph, other.id)
    val sharesFather = pf != null && pf == of
    val sharesMother = pm != null && pm == om
    return when {
        sharesFather && !sharesMother -> "общий отец"
        sharesMother && !sharesFather -> "общая мать"
        else -> "сводный родственник"
    }
}
