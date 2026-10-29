package ru.hdlabs.treex.domain

import kotlin.DeprecationLevel
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Person → Union → children. One canonical union list for kinship and GEDCOM export. */

data class ChildGroup(
    val parentIds: List<String>,
    val childIds: List<String>,
    val open: Boolean = false,
    val unionId: String? = null,
)

fun UnionEntity.childIdsList(): List<String> =
    if (childIds.isBlank()) emptyList() else childIds.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun UnionEntity.withChildIds(ids: List<String>): UnionEntity =
    copy(childIds = ids.distinct().filter { it.isNotBlank() }.joinToString(","))

fun UnionEntity.partnerIds(): List<String> = listOfNotNull(partnerAId, partnerBId)

fun UnionEntity.hasBothPartners(): Boolean = partnerAId != null && partnerBId != null

fun coupleUnionId(firstId: String, secondId: String): String = stableUnionId(firstId, secondId)

fun singleParentUnionId(parentId: String): String = "pu-$parentId"

private fun coupleKey(a: String, b: String): String = "${minOf(a, b)}|${maxOf(a, b)}"

object UnionEr {
    fun findCoupleUnion(unions: List<UnionEntity>, a: String, b: String): UnionEntity? {
        val want = setOf(a, b)
        return unions.find { setOfNotNull(it.partnerAId, it.partnerBId) == want }
    }

    fun findParentUnionForChild(unions: List<UnionEntity>, childId: String): UnionEntity? =
        unions.find { childId in it.childIdsList() }

    /** Родители ребёнка: партнёры союза, где ребёнок в childrenIds. */
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
            return (fromUnions + extraParents[person.id].orEmpty().filter { it in ids }).distinct()
        }
        return extraParents[person.id].orEmpty().filter { it in ids && it != person.id }.distinct()
    }

    fun childrenOfUnion(
        people: List<PersonEntity>,
        union: UnionEntity,
        ids: Set<String>,
        unions: List<UnionEntity>,
    ): List<PersonEntity> {
        val partners = union.partnerIds().toSet()
        return people.filter { child ->
            child.id in ids && !child.vacant &&
                parentIdsOf(child, ids, unions).toSet() == partners && partners.isNotEmpty()
        }.ifEmpty {
            union.childIdsList().filter { it in ids }.mapNotNull { id -> people.find { it.id == id } }
        }
    }

    /**
     * Сжимает союзы: один союз на пару родителей с детьми, без дублирующих pu-* при двух родителях,
     * отдельные брачные союзы без детей.
     */
    @Deprecated("Use GenealogyGraph / families", level = DeprecationLevel.WARNING)
    fun compactUnions(people: List<PersonEntity>, existing: List<UnionEntity>): List<UnionEntity> {
        val ids = people.map { it.id }.toSet()
        val living = people.filter { !it.vacant }
        val parentingByKey = LinkedHashMap<String, UnionEntity>()

        living.forEach { child ->
            var parents = existing
                .filter { child.id in it.childIdsList() }
                .flatMap { it.partnerIds() }
                .filter { it in ids }
                .distinct()
            when (parents.size) {
                2 -> {
                    val a = minOf(parents[0], parents[1])
                    val b = maxOf(parents[0], parents[1])
                    val key = "2:${coupleKey(a, b)}"
                    val prev = parentingByKey[key]
                    val children = ((prev?.childIdsList() ?: emptyList()) + child.id).distinct()
                    parentingByKey[key] = UnionEntity(
                        id = coupleUnionId(a, b),
                        partnerAId = a,
                        partnerBId = b,
                        childIds = children.joinToString(","),
                        marriedOn = prev?.marriedOn ?: existing.find { it.id == coupleUnionId(a, b) }?.marriedOn.orEmpty(),
                    )
                }
                1 -> {
                    val p = parents[0]
                    val inCouple = parentingByKey.values.any { u ->
                        child.id in u.childIdsList() && p in u.partnerIds() && u.hasBothPartners()
                    }
                    if (inCouple) return@forEach
                    val key = "1:$p"
                    val prev = parentingByKey[key]
                    val children = ((prev?.childIdsList() ?: emptyList()) + child.id).distinct()
                    parentingByKey[key] = UnionEntity(
                        id = singleParentUnionId(p),
                        partnerAId = p,
                        partnerBId = null,
                        childIds = children.joinToString(","),
                    )
                }
            }
        }

        val parenting = parentingByKey.values.toList()
        val parentCoupleKeys = parenting
            .filter { it.hasBothPartners() }
            .map { coupleKey(it.partnerAId!!, it.partnerBId!!) }
            .toSet()

        val spouseOnly = existing.mapNotNull { u ->
            val a = u.partnerAId ?: return@mapNotNull null
            val b = u.partnerBId ?: return@mapNotNull null
            if (a !in ids || b !in ids || a == b) return@mapNotNull null
            if (coupleKey(a, b) in parentCoupleKeys) return@mapNotNull null
            if (u.childIdsList().isNotEmpty()) return@mapNotNull null
            val left = minOf(a, b)
            val right = maxOf(a, b)
            UnionEntity(
                id = coupleUnionId(left, right),
                partnerAId = left,
                partnerBId = right,
                marriedOn = u.marriedOn,
            )
        }.distinctBy { it.id }

        return (parenting + spouseOnly).distinctBy { it.id }
    }

    /** @see compactUnions */
    @Deprecated("Use KinshipRepository", level = DeprecationLevel.WARNING)
    fun reconcileChildMembership(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): List<UnionEntity> = compactUnions(people, unions)

    fun parentPairsMap(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): Map<String, Pair<String?, String?>> {
        val compact = compactUnions(people, unions)
        val ids = people.map { it.id }.toSet()
        return people.associate { person ->
            val parents = parentIdsOf(person, ids, compact)
            val father = parents.firstOrNull { id -> people.find { it.id == id }?.gender == "m" }
                ?: parents.firstOrNull()
            val mother = parents.firstOrNull { id -> people.find { it.id == id }?.gender == "f" }
                ?: parents.getOrNull(1)
            person.id to (father to mother)
        }
    }

    /** Одна линия родитель→дети на каждый набор родителей (без дублей от лишних союзов). */
    fun childGroupsByParentPairs(
        people: List<PersonEntity>,
        placedIds: Set<String>,
        unions: List<UnionEntity>,
    ): List<ChildGroup> {
        val ids = placedIds
        return people
            .filter { it.id in placedIds && !it.vacant }
            .groupBy { person -> parentIdsOf(person, ids, unions).sorted() }
            .filterKeys { it.isNotEmpty() }
            .map { (parentIds, children) ->
                ChildGroup(parentIds, children.map { it.id }.sorted())
            }
    }
}
