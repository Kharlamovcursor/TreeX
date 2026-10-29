package ru.hdlabs.treex.data

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper
import ru.hdlabs.treex.domain.graph.Gender

fun TreeState.genealogyGraph(): GenealogyGraph = GenealogyGraphMapper.fromFamilies(
    people,
    families,
    familyChildren,
)

fun TreeState.halfSiblingCaption(person: PersonEntity, other: PersonEntity): String =
    genealogyGraph().halfSiblingCaptionBetween(person, other)

fun GenealogyGraph.fatherIdOf(childId: String): String? = familyWhereChild(childId)?.partnerAId

fun GenealogyGraph.motherIdOf(childId: String): String? = familyWhereChild(childId)?.partnerBId

fun GenealogyGraph.spousePersonIds(personId: String): List<String> = spousesOf(personId).map { it.id }

fun GenealogyGraph.halfSiblingCaptionBetween(person: PersonEntity, other: PersonEntity): String {
    val pf = genderFatherSlot(person.id)
    val pm = genderMotherSlot(person.id)
    val of = genderFatherSlot(other.id)
    val om = genderMotherSlot(other.id)
    val sharesFather = pf != null && pf == of
    val sharesMother = pm != null && pm == om
    return when {
        sharesFather && !sharesMother -> "общий отец"
        sharesMother && !sharesFather -> "общая мать"
        else -> "сводный родственник"
    }
}

private fun GenealogyGraph.genderFatherSlot(childId: String): String? {
    val fam = familyWhereChild(childId) ?: return null
    return when {
        person(fam.partnerAId.orEmpty())?.gender == Gender.MALE -> fam.partnerAId
        person(fam.partnerBId.orEmpty())?.gender == Gender.MALE -> fam.partnerBId
        else -> fam.partnerAId
    }
}

private fun GenealogyGraph.genderMotherSlot(childId: String): String? {
    val fam = familyWhereChild(childId) ?: return null
    return when {
        person(fam.partnerAId.orEmpty())?.gender == Gender.FEMALE -> fam.partnerAId
        person(fam.partnerBId.orEmpty())?.gender == Gender.FEMALE -> fam.partnerBId
        else -> fam.partnerBId
    }
}
