package ru.hdlabs.treex.data.kinship

import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

internal data class KinshipData(
    val people: Map<String, PersonEntity>,
    val families: Map<String, FamilyEntity>,
    val children: List<FamilyChildEntity>,
) {
    fun toGraph(): GenealogyGraph = GenealogyGraphMapper.fromFamilies(
        people.values.toList(),
        families.values.toList(),
        children,
    )

    fun copyMutable(): MutableKinshipData = MutableKinshipData(
        people = people.toMutableMap(),
        families = families.toMutableMap(),
        children = children.toMutableList(),
    )
}

internal class MutableKinshipData(
    val people: MutableMap<String, PersonEntity>,
    val families: MutableMap<String, FamilyEntity>,
    val children: MutableList<FamilyChildEntity>,
) {
    fun freeze(): KinshipData = KinshipData(people.toMap(), families.toMap(), children.toList())

    fun childrenOf(familyId: String): List<FamilyChildEntity> =
        children.filter { it.familyId == familyId }.sortedBy { it.position }

    fun removeChildRow(familyId: String, childId: String) {
        children.removeAll { it.familyId == familyId && it.childId == childId }
    }

    fun setChildrenForFamily(familyId: String, childIds: List<String>) {
        children.removeAll { it.familyId == familyId }
        childIds.forEachIndexed { index, cid ->
            children += FamilyChildEntity(familyId, cid, index)
        }
    }

    fun appendChild(familyId: String, childId: String) {
        val next = (childrenOf(familyId).maxOfOrNull { it.position } ?: -1) + 1
        if (children.none { it.familyId == familyId && it.childId == childId }) {
            children += FamilyChildEntity(familyId, childId, next)
        }
    }

    fun renumberChildren(familyId: String) {
        val rows = childrenOf(familyId)
        children.removeAll { it.familyId == familyId }
        rows.forEachIndexed { index, row ->
            children += row.copy(position = index)
        }
    }

    fun toGraph(): GenealogyGraph = freeze().toGraph()
}
