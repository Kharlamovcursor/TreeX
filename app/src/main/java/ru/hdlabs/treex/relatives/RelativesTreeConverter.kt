package ru.hdlabs.treex.relatives

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.UnionEr
import ru.hdlabs.treex.domain.childIdsList
import ru.hdlabs.treex.domain.partnerIds
import ru.hdlabs.treex.domain.siblingGroupsOf

/** Maps Room kinship → relatives-tree [RelativesGraphNode] list (MIT layout input). */
object RelativesTreeConverter {
    private const val BLOOD = "blood"
    private const val MARRIED = "married"
    private const val HALF = "half"

    fun toGraphNodes(people: List<PersonEntity>, unions: List<UnionEntity>): List<RelativesGraphNode> {
        val living = people.filter { !it.vacant }
        if (living.isEmpty()) return emptyList()
        val ids = living.map { it.id }.toSet()
        val compact = UnionEr.compactUnions(living, unions)
        val byId = living.associateBy { it.id }

        fun spousesOf(id: String): List<RelativesRelation> =
            compact
                .filter { id in it.partnerIds() }
                .flatMap { union ->
                    union.partnerIds().filter { it != id && it in ids }.map { RelativesRelation(it, MARRIED) }
                }
                .distinctBy { it.id }

        fun childrenOf(id: String): List<RelativesRelation> =
            compact
                .filter { id in it.partnerIds() }
                .flatMap { it.childIdsList() }
                .filter { it in ids }
                .distinct()
                .map { RelativesRelation(it, BLOOD) }

        fun parentsOf(person: PersonEntity): List<RelativesRelation> =
            UnionEr.parentIdsOf(person, ids, compact).map { RelativesRelation(it, BLOOD) }

        fun siblingsOf(person: PersonEntity): List<RelativesRelation> {
            val groups = siblingGroupsOf(person, living)
            return groups.full.map { RelativesRelation(it.id, BLOOD) } +
                groups.half.map { RelativesRelation(it.id, HALF) }
        }

        return living.map { person ->
            RelativesGraphNode(
                id = person.id,
                gender = toLibraryGender(person.gender),
                parents = parentsOf(person),
                children = childrenOf(person.id),
                siblings = siblingsOf(person),
                spouses = spousesOf(person.id),
            )
        }
    }

    private fun toLibraryGender(gender: String): String = when (gender) {
        "f" -> "female"
        "m" -> "male"
        else -> "male"
    }
}
