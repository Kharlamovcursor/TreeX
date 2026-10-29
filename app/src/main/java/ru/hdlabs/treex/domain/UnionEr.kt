package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Entity-Relationship helpers: Person → Union → children. */

fun UnionEntity.childIdsList(): List<String> =
    if (childIds.isBlank()) emptyList() else childIds.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun UnionEntity.withChildIds(ids: List<String>): UnionEntity =
    copy(childIds = ids.distinct().filter { it.isNotBlank() }.joinToString(","))

fun UnionEntity.partnerIds(): List<String> = listOfNotNull(partnerAId, partnerBId)

fun UnionEntity.hasBothPartners(): Boolean = partnerAId != null && partnerBId != null

/** Stable id for a couple union (spouse or two known parents). */
fun coupleUnionId(firstId: String, secondId: String): String = stableUnionId(firstId, secondId)

/** Union for one known parent and a missing second slot. */
fun singleParentUnionId(parentId: String): String = "pu-$parentId"

object UnionEr {
    fun findCoupleUnion(unions: List<UnionEntity>, a: String, b: String): UnionEntity? {
        val want = setOf(a, b)
        return unions.find { setOfNotNull(it.partnerAId, it.partnerBId) == want }
    }

    fun findParentUnionForChild(unions: List<UnionEntity>, childId: String): UnionEntity? =
        unions.find { childId in it.childIdsList() }

    /** Parent ids for layout / generation — unions first, legacy fatherId/motherId fallback. */
    fun parentIdsOf(
        person: PersonEntity,
        ids: Set<String>,
        unions: List<UnionEntity>,
        extraParents: Map<String, List<String>> = emptyMap(),
    ): List<String> {
        val fromUnions = unions
            .filter { person.id in it.childIdsList() }
            .flatMap { it.partnerIds() }
            .filter { it in ids && it != person.id }
            .distinct()
        if (fromUnions.isNotEmpty()) {
            return fromUnions + extraParents[person.id].orEmpty().filter { it in ids }.distinct()
        }
        return (listOfNotNull(person.fatherId, person.motherId) + extraParents[person.id].orEmpty())
            .filter { it in ids && it != person.id }
            .distinct()
    }

    fun childrenOfUnion(
        people: List<PersonEntity>,
        union: UnionEntity,
        ids: Set<String>,
    ): List<PersonEntity> {
        val explicit = union.childIdsList().filter { it in ids }.mapNotNull { id -> people.find { it.id == id } }
        if (explicit.isNotEmpty()) return explicit
        return people.filter { child ->
            val parents = parentIdsOf(child, ids, emptyList()).toSet()
            val partners = union.partnerIds().toSet()
            parents.isNotEmpty() && parents.all { it in partners } && partners.all { it in parents || partners.size == 1 }
        }
    }

    /**
     * Ensures [childIds] on parent unions reflect fatherId/motherId (import / старые базы).
     * Does not remove direct pointers — callers may clear them after migration.
     */
    fun reconcileChildMembership(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): List<UnionEntity> {
        val ids = people.map { it.id }.toSet()
        val mutable = unions.map { it.withChildIds(it.childIdsList()) }.toMutableList()

        fun upsertUnion(union: UnionEntity) {
            val index = mutable.indexOfFirst { it.id == union.id }
            if (index >= 0) {
                val existing = mutable[index]
                val mergedChildren = (existing.childIdsList() + union.childIdsList()).distinct()
                mutable[index] = existing.copy(
                    partnerAId = union.partnerAId ?: existing.partnerAId,
                    partnerBId = union.partnerBId ?: existing.partnerBId,
                    childIds = mergedChildren.joinToString(","),
                    unionType = existing.unionType.ifBlank { union.unionType },
                )
            } else {
                mutable += union
            }
        }

        people.forEach { person ->
            val father = person.fatherId?.takeIf { it in ids }
            val mother = person.motherId?.takeIf { it in ids }
            when {
                father != null && mother != null && father != mother -> {
                    val id = coupleUnionId(father, mother)
                    val a = minOf(father, mother)
                    val b = maxOf(father, mother)
                    upsertUnion(
                        UnionEntity(
                            id = id,
                            partnerAId = a,
                            partnerBId = b,
                            childIds = person.id,
                        ),
                    )
                }
                father != null -> {
                    upsertUnion(
                        UnionEntity(
                            id = singleParentUnionId(father),
                            partnerAId = father,
                            partnerBId = null,
                            childIds = person.id,
                        ),
                    )
                }
                mother != null -> {
                    upsertUnion(
                        UnionEntity(
                            id = singleParentUnionId(mother),
                            partnerAId = mother,
                            partnerBId = null,
                            childIds = person.id,
                        ),
                    )
                }
            }
        }

        return mutable.distinctBy { it.id }
    }

    /** Parent map for cycle checks from union graph (+ legacy fields). */
    fun parentPairsMap(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): Map<String, Pair<String?, String?>> {
        val reconciled = reconcileChildMembership(people, unions)
        val ids = people.map { it.id }.toSet()
        return people.associate { person ->
            val parents = parentIdsOf(person, ids, reconciled)
            val father = parents.firstOrNull { id ->
                people.find { it.id == id }?.gender == "m"
            } ?: parents.firstOrNull()
            val mother = parents.firstOrNull { id ->
                people.find { it.id == id }?.gender == "f"
            } ?: parents.getOrNull(1)
            person.id to (father to mother)
        }
    }

    /** Lines: one group per union that has children (Person → Union → children). */
    fun childGroupsFromUnions(
        unions: List<UnionEntity>,
        placedIds: Set<String>,
        people: List<PersonEntity>,
    ): List<ChildGroup> {
        val byId = people.associateBy { it.id }
        return unions.mapNotNull { union ->
            val parentIds = union.partnerIds().filter { it in placedIds }
            val childIds = union.childIdsList().filter { childId ->
                childId in placedIds && byId[childId]?.vacant != true
            }
            if (parentIds.isEmpty() || childIds.isEmpty()) return@mapNotNull null
            ChildGroup(parentIds.sorted(), childIds)
        }
    }
}
