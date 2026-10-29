package ru.hdlabs.treex.domain

import java.util.UUID

/**
 * Pure ER mutations: all parent/child/spouse changes go through [FamilyUnion].
 * No Person→Person parent edges are created here.
 */
object FamilyTreeMutations {
    fun addPerson(state: FamilyTreeState, person: FamilyPerson): FamilyTreeState {
        require(person.id.isNotBlank())
        if (state.person(person.id) != null) return state
        return state.copy(people = state.people + person)
    }

    fun addUnion(
        state: FamilyTreeState,
        partner1Id: String,
        partner2Id: String?,
        type: FamilyUnionType = FamilyUnionType.Marriage,
        marriedOn: String = "",
        unionId: String? = null,
    ): Pair<FamilyTreeState, FamilyUnion> {
        require(partner1Id.isNotBlank())
        require(partner2Id == null || partner2Id != partner1Id)
        val existing = if (partner2Id != null) {
            findUnionByPartners(state, partner1Id, partner2Id)
        } else {
            null
        }
        if (existing != null) return state to existing

        val id = unionId ?: when (partner2Id) {
            null -> singleParentUnionId(partner1Id)
            else -> coupleUnionId(
                minOf(partner1Id, partner2Id),
                maxOf(partner1Id, partner2Id),
            )
        }
        val union = FamilyUnion(
            id = id,
            partner1Id = partner1Id,
            partner2Id = partner2Id,
            childrenIds = emptyList(),
            type = type,
            marriedOn = marriedOn,
        )
        return state.copy(unions = state.unions + union) to union
    }

    fun addChildToUnion(
        state: FamilyTreeState,
        unionId: String,
        child: FamilyPerson,
    ): FamilyTreeState {
        val union = state.union(unionId) ?: error("Union $unionId not found")
        var next = addPerson(state, child)
        val updated = union.copy(
            childrenIds = (union.childrenIds + child.id).distinct(),
        )
        next = next.copy(unions = next.unions.map { if (it.id == unionId) updated else it })
        return next
    }

    /** Rule 1: child for two partners — reuse or create couple union, append child. */
    fun addChildToCouple(
        state: FamilyTreeState,
        partner1Id: String,
        partner2Id: String,
        child: FamilyPerson,
    ): FamilyTreeState {
        val (withUnion, union) = addUnion(state, partner1Id, partner2Id)
        return addChildToUnion(withUnion, union.id, child)
    }

    /** Rule 2: first parent for child — union with one filled partner slot. */
    fun addParentToPerson(
        state: FamilyTreeState,
        childId: String,
        parent: FamilyPerson,
    ): FamilyTreeState {
        require(state.person(childId) != null) { "Child $childId must exist" }
        var next = addPerson(state, parent)

        val existingForChild = next.unions.find { childId in it.childrenIds }
        if (existingForChild != null) {
            val filled = fillPartnerSlot(existingForChild, parent.id)
            val withChild = if (childId in filled.childrenIds) filled else filled.copy(
                childrenIds = filled.childrenIds + childId,
            )
            return next.copy(unions = next.unions.map { if (it.id == existingForChild.id) withChild else it })
        }

        val (withUnion, union) = addUnion(next, parent.id, partner2Id = null)
        val withChild = union.copy(childrenIds = (union.childrenIds + childId).distinct())
        return withUnion.copy(unions = withUnion.unions.map { if (it.id == union.id) withChild else it })
    }

    /** Rule 3: spouse — couple union, empty children. */
    fun addPartnerToPerson(
        state: FamilyTreeState,
        personId: String,
        partner: FamilyPerson,
        type: FamilyUnionType = FamilyUnionType.Marriage,
        marriedOn: String = "",
    ): FamilyTreeState {
        require(state.person(personId) != null)
        val (next, _) = addUnion(
            addPerson(state, partner),
            personId,
            partner.id,
            type = type,
            marriedOn = marriedOn,
        )
        return next
    }

    fun addSiblingOf(
        state: FamilyTreeState,
        anchorId: String,
        sibling: FamilyPerson,
    ): FamilyTreeState {
        require(state.person(anchorId) != null)
        var next = addPerson(state, sibling)
        val anchorUnions = next.unions.filter { anchorId in it.childrenIds }
        if (anchorUnions.isEmpty()) return next
        next = next.copy(
            unions = next.unions.map { union ->
                if (anchorId !in union.childrenIds) union
                else union.copy(childrenIds = (union.childrenIds + sibling.id).distinct())
            },
        )
        return next
    }

    fun findUnionByPartners(state: FamilyTreeState, a: String, b: String): FamilyUnion? {
        val want = setOf(a, b)
        return state.unions.find { setOfNotNull(it.partner1Id, it.partner2Id) == want }
    }

    fun newPersonId(): String = UUID.randomUUID().toString()

    private fun fillPartnerSlot(union: FamilyUnion, parentId: String): FamilyUnion {
        if (union.partner1Id == parentId || union.partner2Id == parentId) return union
        return when {
            union.partner1Id == null -> union.copy(partner1Id = parentId)
            union.partner2Id == null -> union.copy(partner2Id = parentId)
            else -> union
        }
    }
}
