package ru.hdlabs.treex.data

import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

fun TreeState.genealogyGraph(): GenealogyGraph = GenealogyGraphMapper.fromFamilies(
    people,
    families,
    familyChildren,
)

fun GenealogyGraph.fatherIdOf(childId: String): String? = familyWhereChild(childId)?.partnerAId

fun GenealogyGraph.motherIdOf(childId: String): String? = familyWhereChild(childId)?.partnerBId

fun GenealogyGraph.spousePersonIds(personId: String): List<String> = spousesOf(personId).map { it.id }
