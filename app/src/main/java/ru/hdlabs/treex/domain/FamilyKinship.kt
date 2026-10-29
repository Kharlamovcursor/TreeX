package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.abs

/**
 * Genealogical structure: Person → Union → children.
 * Layout consumes this model only — never infers kinship from pixels.
 */
internal object FamilyKinship {
    fun parentIdsOf(
        person: PersonEntity,
        ids: Set<String>,
        unions: List<UnionEntity>,
        extraParents: Map<String, List<String>> = emptyMap(),
    ): List<String> = UnionEr.parentIdsOf(person, ids, unions, extraParents)

    fun sameGenerationUnions(
        unions: List<UnionEntity>,
        ids: Set<String>,
        generation: Map<String, Int>,
    ): List<UnionEntity> =
        unions.filter {
            val a = it.partnerAId
            val b = it.partnerBId
            a != null && b != null && a in ids && b in ids && a != b &&
                generation[a] == generation[b]
        }

    fun belongsToUnion(
        person: PersonEntity,
        union: UnionEntity,
        ids: Set<String>,
        unions: List<UnionEntity>,
        extraParents: Map<String, List<String>>,
    ): Boolean {
        if (person.id in union.childIdsList()) return true
        val parents = parentIdsOf(person, ids, unions, extraParents)
        if (parents.isEmpty()) return false
        val partners = union.partnerIds().toSet()
        return parents.all { it in partners }
    }

    fun childrenOfUnion(
        people: List<PersonEntity>,
        union: UnionEntity,
        ids: Set<String>,
        unions: List<UnionEntity>,
        extraParents: Map<String, List<String>>,
    ): List<PersonEntity> {
        val explicit = union.childIdsList().filter { it in ids }.mapNotNull { id -> people.find { it.id == id } }
        if (explicit.isNotEmpty()) return explicit
        return people.filter { belongsToUnion(it, union, ids, unions, extraParents) }
    }

    fun orderSiblings(
        children: List<PersonEntity>,
        unionsOf: (String) -> List<UnionEntity>,
    ): List<PersonEntity> {
        val sorted = children.sortedWith(compareBy({ it.birthDate }, { it.id }))
        val singles = sorted.filter { unionsOf(it.id).isEmpty() }
        val married = sorted.filter { unionsOf(it.id).isNotEmpty() }
        return singles + married
    }

    fun otherPartner(union: UnionEntity, id: String): String? =
        when (id) {
            union.partnerAId -> union.partnerBId
            union.partnerBId -> union.partnerAId
            else -> null
        }

    fun leftPartnerId(
        union: UnionEntity,
        byId: Map<String, PersonEntity>,
        ids: Set<String>,
        unions: List<UnionEntity>,
        extraParents: Map<String, List<String>>,
        parentColumnCenter: (String) -> Float?,
        homePersonId: String?,
    ): String {
        val a = union.partnerAId ?: return union.partnerBId ?: union.id
        val b = union.partnerBId ?: return a
        val home = homePersonId?.takeIf { it in byId }
        if (home == a || home == b) return home

        fun column(id: String): Float? {
            val parents = parentIdsOf(byId.getValue(id), ids, unions, extraParents)
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

        return a
    }
}
