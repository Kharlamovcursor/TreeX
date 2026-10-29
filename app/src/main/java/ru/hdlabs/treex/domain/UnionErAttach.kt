package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Applies relative attach flows through [FamilyTreeMutations] (ER-only). */
object UnionErAttach {
    fun applyRelativeAttach(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        anchorId: String,
        role: String,
        newPerson: PersonEntity,
    ): List<UnionEntity> {
        val reconciled = UnionEr.reconcileChildMembership(people, unions)
        var state = familyTreeStateFrom(people, reconciled)
        val familyPerson = newPerson.toFamilyPerson()
        state = when (role) {
            "father", "mother" -> FamilyTreeMutations.addParentToPerson(state, anchorId, familyPerson)
            "spouse" -> FamilyTreeMutations.addPartnerToPerson(state, anchorId, familyPerson)
            "brother", "sister" -> FamilyTreeMutations.addSiblingOf(state, anchorId, familyPerson)
            "son", "daughter", "child" -> addChildViaAnchor(state, reconciled, anchorId, familyPerson)
            else -> FamilyTreeMutations.addPerson(state, familyPerson)
        }
        return state.unions.map { it.toUnionEntity() }
    }

    private fun addChildViaAnchor(
        state: FamilyTreeState,
        reconciled: List<UnionEntity>,
        anchorId: String,
        child: FamilyPerson,
    ): FamilyTreeState {
        var next = FamilyTreeMutations.addPerson(state, child)
        val couple = reconciled.firstOrNull { union ->
            anchorId in union.partnerIds() && union.hasBothPartners()
        }
        if (couple != null) {
            val partners = couple.partnerIds()
            return FamilyTreeMutations.addChildToCouple(next, partners[0], partners[1], child)
        }
        val single = reconciled.firstOrNull { anchorId in it.partnerIds() }
        if (single != null) {
            return FamilyTreeMutations.addChildToUnion(next, single.id, child)
        }
        val (withUnion, union) = FamilyTreeMutations.addUnion(next, anchorId, partner2Id = null)
        return FamilyTreeMutations.addChildToUnion(withUnion, union.id, child)
    }
}
