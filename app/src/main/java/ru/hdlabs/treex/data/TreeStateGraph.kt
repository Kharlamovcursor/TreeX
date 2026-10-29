package ru.hdlabs.treex.data

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper
import ru.hdlabs.treex.domain.halfSiblingCaption

fun TreeState.genealogyGraph(): GenealogyGraph = GenealogyGraphMapper.fromFamilies(
    people,
    families,
    familyChildren,
)

fun TreeState.halfSiblingCaption(person: PersonEntity, other: PersonEntity): String =
    halfSiblingCaption(person, other, genealogyGraph())

fun GenealogyGraph.fatherIdOf(childId: String): String? = familyWhereChild(childId)?.partnerAId

fun GenealogyGraph.motherIdOf(childId: String): String? = familyWhereChild(childId)?.partnerBId

fun GenealogyGraph.spousePersonIds(personId: String): List<String> = spousesOf(personId).map { it.id }
