package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

data class TreeCard(val id: String, val left: Float, val top: Float, val height: Float = TreeLayout.CARD_H)

data class ChildGroup(val parentIds: List<String>, val childIds: List<String>, val open: Boolean = false)

data class OpenKnot(val linkId: String, val x: Float, val y: Float)

data class TreePlacement(
    val cards: List<TreeCard>,
    val spouseLinks: List<Pair<String, String>>,
    val childGroups: List<ChildGroup>,
    val width: Float,
    val height: Float,
    val knots: List<OpenKnot> = emptyList(),
)

object TreeLayout {
    const val CARD_W = 168f
    const val CARD_H = 108f
    private const val MAX_CARD_H = 124f
    private const val SPOUSE_GAP = 28f
    private const val HOUSE_GAP = 44f
    private const val ROW_GAP = 76f
    private const val PAD = 16f
    private val X_UNIT = (CARD_W + SPOUSE_GAP) / RelativesTree.SIZE.toFloat()

    fun cardHeight(person: PersonEntity): Float {
        val lines = wrapLines(person.fullName(), 14)
        val years = if (person.birthDate.isBlank() && person.deathDate.isBlank()) 0f else 20f
        return (60f + lines * 20f + years).coerceIn(CARD_H, MAX_CARD_H)
    }

    fun wrapLines(text: String, perLine: Int): Int {
        if (text.isBlank() || perLine <= 0) return 1
        var lines = 1
        var column = 0
        text.split(" ").forEach { word ->
            val parts = if (word.length <= perLine) listOf(word) else word.chunked(perLine)
            parts.forEach { part ->
                if (column == 0) {
                    column = part.length
                } else if (column + 1 + part.length > perLine) {
                    lines += 1
                    column = part.length
                } else {
                    column += 1 + part.length
                }
            }
        }
        return lines
    }

    fun generationMap(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink> = emptyList(),
    ): Map<String, Int> {
        if (people.isEmpty()) return emptyMap()
        val byId = people.associateBy { it.id }
        val generation = HashMap<String, Int>()
        val visiting = HashSet<String>()

        fun depth(id: String): Int {
            generation[id]?.let { return it }
            if (!visiting.add(id)) return 0
            val person = byId[id]
            val parents = listOfNotNull(person?.fatherId, person?.motherId)
                .filter { it in byId && it != id }
            val value = if (parents.isEmpty()) 0 else parents.maxOf { depth(it) } + 1
            visiting.remove(id)
            generation[id] = value
            return value
        }
        people.forEach { depth(it.id) }

        fun hasParents(id: String): Boolean {
            val person = byId[id] ?: return false
            return (person.fatherId != null && person.fatherId in byId) ||
                (person.motherId != null && person.motherId in byId)
        }

        var guard = 0
        while (guard++ < 8) {
            var changed = false
            unions.forEach { union ->
                if (union.partnerAId !in byId || union.partnerBId !in byId) return@forEach
                val generationA = generation.getValue(union.partnerAId)
                val generationB = generation.getValue(union.partnerBId)
                if (generationA == generationB) return@forEach
                val parentsA = hasParents(union.partnerAId)
                val parentsB = hasParents(union.partnerBId)
                when {
                    !parentsA && parentsB -> {
                        generation[union.partnerAId] = generationB
                        changed = true
                    }
                    parentsA && !parentsB -> {
                        generation[union.partnerBId] = generationA
                        changed = true
                    }
                    !parentsA && !parentsB -> {
                        val shared = minOf(generationA, generationB)
                        if (generationA != shared) {
                            generation[union.partnerAId] = shared
                            changed = true
                        }
                        if (generationB != shared) {
                            generation[union.partnerBId] = shared
                            changed = true
                        }
                    }
                    else -> {
                        val shared = maxOf(generationA, generationB)
                        if (generationA != shared) {
                            generation[union.partnerAId] = shared
                            changed = true
                        }
                        if (generationB != shared) {
                            generation[union.partnerBId] = shared
                            changed = true
                        }
                    }
                }
            }
            if (!changed) break
        }

        var parentPass = 0
        while (parentPass++ < people.size + 4) {
            var changed = false
            people.forEach { person ->
                val childGen = generation[person.id] ?: return@forEach
                listOfNotNull(person.fatherId, person.motherId).forEach { parentId ->
                    if (parentId !in byId) return@forEach
                    val target = childGen - 1
                    if ((generation[parentId] ?: 0) < target) {
                        generation[parentId] = target
                        changed = true
                    }
                }
            }
            if (!changed) break
        }

        openLinks.filter { it.slot == "bridge" && it.anchorIsParent }.forEach { link ->
            if (link.anchorId !in byId) return@forEach
            val parentGen = generation.getValue(link.anchorId)
            link.childIds.filter { it in byId }.forEach { childId ->
                val next = parentGen + 1
                if ((generation[childId] ?: 0) < next) {
                    generation[childId] = next
                }
            }
        }

        val minGeneration = generation.values.minOrNull() ?: 0
        return generation.mapValues { (_, value) -> value - minGeneration }
    }

    fun bands(people: List<PersonEntity>, unions: List<UnionEntity>): List<List<PersonEntity>> {
        if (people.isEmpty()) return emptyList()
        val generation = generationMap(people, unions)
        return people
            .groupBy { generation[it.id] ?: 0 }
            .toSortedMap()
            .values
            .map { band ->
                band.sortedWith(
                    compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }, { it.id }),
                )
            }
    }

    fun place(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink> = emptyList(),
        homePersonId: String? = null,
    ): TreePlacement {
        if (people.isEmpty()) return TreePlacement(emptyList(), emptyList(), emptyList(), 0f, 0f)
        val byId = people.associateBy { it.id }
        val heights = people.associate { it.id to cardHeight(it) }
        val nodes = toRelNodes(people, unions, openLinks)
        val nodeById = nodes.associateBy { it.id }
        val remaining = people.map { it.id }.toMutableSet()
        val cards = mutableListOf<TreeCard>()
        var cursor = PAD
        val home = homePersonId?.trim()?.takeIf { it in remaining }
        while (remaining.isNotEmpty()) {
            val subgraphIds = remaining.toSet()
            val rootId = pickRoot(subgraphIds, byId, home?.takeIf { it in subgraphIds })
            val subgraph = nodesOf(nodeById, subgraphIds)
            val placed = RelativesTree.calc(subgraph, rootId)
                .filter { it.id in remaining }
            if (placed.isEmpty()) {
                remaining.sorted().forEach { id ->
                    cards += TreeCard(id, cursor, PAD, heights.getValue(id))
                    cursor += CARD_W + HOUSE_GAP
                    remaining.remove(id)
                }
                break
            }
            val block = toCards(placed, heights, cursor)
            cards += block
            remaining.removeAll(block.map { it.id }.toSet())
            cursor = (block.maxOfOrNull { it.left + CARD_W } ?: cursor) + HOUSE_GAP
        }
        val childGroups = childGroupsOf(people, cards, openLinks)
        val spouseLinks = unions.mapNotNull { union ->
            if (union.partnerAId in byId && union.partnerBId in byId && union.partnerAId != union.partnerBId) {
                union.partnerAId to union.partnerBId
            } else {
                null
            }
        }.distinct()
        val width = (cards.maxOfOrNull { it.left + CARD_W } ?: PAD) + PAD
        val height = (cards.maxOfOrNull { it.top + it.height } ?: PAD) + PAD
        val knots = openLinks.mapNotNull { link -> knotFor(link, cards) }
        return TreePlacement(cards, spouseLinks, childGroups, width, height, knots)
    }

    private fun nodesOf(
        nodeById: Map<String, RelativesTree.Node>,
        ids: Set<String>,
    ): List<RelativesTree.Node> = ids.mapNotNull { id ->
        val source = nodeById[id] ?: return@mapNotNull null
        RelativesTree.Node(
            id = source.id,
            gender = source.gender,
            parents = source.parents.filter { it.id in ids }.toMutableList(),
            children = source.children.filter { it.id in ids }.toMutableList(),
            siblings = source.siblings.filter { it.id in ids }.toMutableList(),
            spouses = source.spouses.filter { it.id in ids }.toMutableList(),
        )
    }

    private fun pickRoot(
        ids: Set<String>,
        byId: Map<String, PersonEntity>,
        home: String?,
    ): String {
        if (home != null && home in ids) return home
        val eldest = ids.filter { id ->
            val person = byId.getValue(id)
            person.fatherId !in ids && person.motherId !in ids
        }
        return eldest.minWithOrNull(
            compareBy({ byId.getValue(it).birthDate }, { it }),
        ) ?: ids.minWithOrNull(compareBy({ byId.getValue(it).birthDate }, { it })) ?: ids.first()
    }

    private fun toRelNodes(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink>,
    ): List<RelativesTree.Node> {
        val ids = people.map { it.id }.toSet()
        val nodes = people.associate { person ->
            person.id to RelativesTree.Node(
                id = person.id,
                gender = if (person.gender == "f") RelativesTree.Gender.Female else RelativesTree.Gender.Male,
            )
        }
        fun addParent(childId: String, parentId: String, type: RelativesTree.RelType = RelativesTree.RelType.Blood) {
            if (childId !in ids || parentId !in ids || childId == parentId) return
            val child = nodes.getValue(childId)
            val parent = nodes.getValue(parentId)
            if (child.parents.none { it.id == parentId }) {
                child.parents += RelativesTree.Relation(parentId, type)
            }
            if (parent.children.none { it.id == childId }) {
                parent.children += RelativesTree.Relation(childId, type)
            }
        }
        people.forEach { person ->
            person.fatherId?.let { addParent(person.id, it) }
            person.motherId?.let { addParent(person.id, it) }
        }
        openLinks.filter { it.slot == "bridge" && it.anchorIsParent }.forEach { link ->
            link.childIds.forEach { childId -> addParent(childId, link.anchorId) }
        }
        unions.forEach { union ->
            if (union.partnerAId !in ids || union.partnerBId !in ids || union.partnerAId == union.partnerBId) {
                return@forEach
            }
            val a = nodes.getValue(union.partnerAId)
            val b = nodes.getValue(union.partnerBId)
            if (a.spouses.none { it.id == b.id }) {
                a.spouses += RelativesTree.Relation(b.id, RelativesTree.RelType.Married)
            }
            if (b.spouses.none { it.id == a.id }) {
                b.spouses += RelativesTree.Relation(a.id, RelativesTree.RelType.Married)
            }
        }
        people.forEach { person ->
            val node = nodes.getValue(person.id)
            siblingGroupsOf(person, people).full.forEach { sibling ->
                if (node.siblings.none { it.id == sibling.id }) {
                    node.siblings += RelativesTree.Relation(sibling.id, RelativesTree.RelType.Blood)
                }
            }
            siblingGroupsOf(person, people).half.forEach { sibling ->
                if (node.siblings.none { it.id == sibling.id }) {
                    node.siblings += RelativesTree.Relation(sibling.id, RelativesTree.RelType.Half)
                }
            }
        }
        return nodes.values.toList()
    }

    private fun toCards(
        placed: List<RelativesTree.ExtNode>,
        heights: Map<String, Float>,
        originX: Float,
    ): List<TreeCard> {
        val bands = placed.groupBy { it.top }.toSortedMap()
        var top = PAD
        val cards = mutableListOf<TreeCard>()
        bands.forEach { (_, band) ->
            val rowH = band.maxOf { heights[it.id] ?: CARD_H }
            band.forEach { node ->
                cards += TreeCard(node.id, originX + node.left.toFloat() * X_UNIT, top, rowH)
            }
            top += rowH + ROW_GAP
        }
        return cards
    }

    private fun childGroupsOf(
        people: List<PersonEntity>,
        cards: List<TreeCard>,
        openLinks: List<OpenLink>,
    ): List<ChildGroup> {
        val placed = cards.map { it.id }.toSet()
        val groups = mutableListOf<ChildGroup>()
        people.filter { it.id in placed }.groupBy { person ->
            listOfNotNull(person.fatherId, person.motherId).filter { it in placed }.sorted()
        }.forEach { (parents, children) ->
            if (parents.isNotEmpty()) {
                groups += ChildGroup(parents, children.map { it.id })
            }
        }
        openLinks.filter { it.slot == "bridge" && it.anchorId in placed }.forEach { link ->
            val childIds = link.childIds.filter { it in placed }
            if (childIds.isNotEmpty()) {
                groups += ChildGroup(listOf(link.anchorId), childIds, open = true)
            }
        }
        return groups
    }

    private fun knotFor(link: OpenLink, cards: List<TreeCard>): OpenKnot? {
        val owner = cards.find { it.id == link.anchorId } ?: cards.find { it.id == link.ownerId } ?: return null
        if (link.slot != "bridge") return OpenKnot(link.id, owner.left + CARD_W / 2f, owner.top - 4f)
        val children = link.childIds.mapNotNull { id -> cards.find { it.id == id } }
        if (children.isEmpty()) return null
        val x = children.map { it.left + CARD_W / 2f }.average().toFloat()
        val y = (owner.top + owner.height + children.minOf { it.top }) / 2f
        return OpenKnot(link.id, x, y)
    }
}
