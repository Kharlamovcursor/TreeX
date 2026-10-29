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

        val raw = living.map { person ->
            RelativesGraphNode(
                id = person.id,
                gender = toLibraryGender(person.gender),
                parents = parentsOf(person),
                children = childrenOf(person.id),
                siblings = siblingsOf(person),
                spouses = spousesOf(person.id),
            )
        }
        return normalizeForRelativesTree(raw)
    }

    /**
     * relatives-tree draws child connectors only when **every** parent in a couple lists the same
     * child in [RelativesGraphNode.children] (see bundled calc-tree.js / connectors/children.js).
     * Room unions + fatherId/motherId can leave one parent with an empty children list — fix here.
     */
    internal fun normalizeForRelativesTree(nodes: List<RelativesGraphNode>): List<RelativesGraphNode> {
        if (nodes.isEmpty()) return nodes
        val order = nodes.map { it.id }
        val idSet = order.toSet()

        fun bloodChildIds(n: RelativesGraphNode): MutableSet<String> =
            n.children.filter { it.type == BLOOD && it.id in idSet }.map { it.id }.toMutableSet()

        fun bloodParentIds(n: RelativesGraphNode): MutableSet<String> =
            n.parents.filter { it.type == BLOOD && it.id in idSet }.map { it.id }.toMutableSet()

        val childIdsByParent = order.associateWith { id -> bloodChildIds(nodes.first { it.id == id }) }.toMutableMap()
        val parentIdsByChild = order.associateWith { id -> bloodParentIds(nodes.first { it.id == id }) }.toMutableMap()

        nodes.forEach { n ->
            n.parents.filter { it.type == BLOOD && it.id in idSet }.forEach { p ->
                childIdsByParent.getOrPut(p.id) { mutableSetOf() }.add(n.id)
                parentIdsByChild.getOrPut(n.id) { mutableSetOf() }.add(p.id)
            }
            n.children.filter { it.type == BLOOD && it.id in idSet }.forEach { c ->
                childIdsByParent.getOrPut(n.id) { mutableSetOf() }.add(c.id)
                parentIdsByChild.getOrPut(c.id) { mutableSetOf() }.add(n.id)
            }
        }

        val spouseLinks = mutableMapOf<String, MutableList<RelativesRelation>>()
        nodes.forEach { n ->
            n.spouses.filter { it.id in idSet }.forEach { s ->
                spouseLinks.getOrPut(n.id) { mutableListOf() }.add(s)
                spouseLinks.getOrPut(s.id) { mutableListOf() }
                    .add(RelativesRelation(n.id, s.type))
            }
        }

        return order.map { id ->
            val n = nodes.first { it.id == id }
            val children = childIdsByParent[id].orEmpty().sorted().map { RelativesRelation(it, BLOOD) } +
                n.children.filter { it.type != BLOOD }
            val parents = parentIdsByChild[id].orEmpty().sorted().map { RelativesRelation(it, BLOOD) } +
                n.parents.filter { it.type != BLOOD }
            val spouses = spouseLinks[id]?.distinctBy { it.id }.orEmpty().ifEmpty { n.spouses }
            n.copy(children = children, parents = parents, spouses = spouses)
        }
    }

    private fun toLibraryGender(gender: String): String = when (gender) {
        "f" -> "female"
        "m" -> "male"
        else -> "male"
    }
}
