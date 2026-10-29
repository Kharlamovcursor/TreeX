package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.abs

/**
 * Genealogical structure only: parents, unions, children, sibling order.
 * No card coordinates — layout consumes this model.
 */
internal object FamilyKinship {
    fun parentIdsOf(
        person: PersonEntity,
        ids: Set<String>,
        extraParents: Map<String, List<String>> = emptyMap(),
    ): List<String> =
        (listOfNotNull(person.fatherId, person.motherId) + extraParents[person.id].orEmpty())
            .filter { it in ids && it != person.id }
            .distinct()

    fun sameGenerationUnions(
        unions: List<UnionEntity>,
        ids: Set<String>,
        generation: Map<String, Int>,
    ): List<UnionEntity> =
        unions.filter {
            it.partnerAId in ids && it.partnerBId in ids && it.partnerAId != it.partnerBId &&
                generation[it.partnerAId] == generation[it.partnerBId]
        }

    fun belongsToUnion(
        person: PersonEntity,
        union: UnionEntity,
        ids: Set<String>,
        extraParents: Map<String, List<String>>,
    ): Boolean {
        val parents = parentIdsOf(person, ids, extraParents)
        if (parents.isEmpty()) return false
        val partners = setOf(union.partnerAId, union.partnerBId)
        return parents.all { it in partners }
    }

    fun childrenOfUnion(
        people: List<PersonEntity>,
        union: UnionEntity,
        ids: Set<String>,
        extraParents: Map<String, List<String>>,
    ): List<PersonEntity> =
        people.filter { belongsToUnion(it, union, ids, extraParents) }

    fun orderSiblings(
        children: List<PersonEntity>,
        unionsOf: (String) -> List<UnionEntity>,
    ): List<PersonEntity> {
        val sorted = children.sortedWith(compareBy({ it.birthDate }, { it.id }))
        val singles = sorted.filter { unionsOf(it.id).isEmpty() }
        val married = sorted.filter { unionsOf(it.id).isNotEmpty() }
        return singles + married
    }

    fun otherPartner(union: UnionEntity, id: String): String =
        if (union.partnerAId == id) union.partnerBId else union.partnerAId

    fun leftPartnerId(
        union: UnionEntity,
        byId: Map<String, PersonEntity>,
        ids: Set<String>,
        extraParents: Map<String, List<String>>,
        parentColumnCenter: (String) -> Float?,
        homePersonId: String?,
    ): String {
        val a = union.partnerAId
        val b = union.partnerBId
        val home = homePersonId?.takeIf { it in byId }
        if (home == a || home == b) return home

        fun column(id: String): Float? {
            val parents = parentIdsOf(byId.getValue(id), ids, extraParents)
            if (parents.isEmpty()) return null
            val xs = parents.mapNotNull(parentColumnCenter)
            if (xs.size != parents.size) return null
            return xs.average().toFloat()
        }

        val columnA = column(a)
        val columnB = column(b)
        if (columnA != null && columnB != null && abs(columnA - columnB) > 0.1f) {
            return if (columnA < columnB) a else b
        }
        if (columnA != null && columnB == null) return a
        if (columnB != null && columnA == null) return b

        return union.partnerAId
    }
}
