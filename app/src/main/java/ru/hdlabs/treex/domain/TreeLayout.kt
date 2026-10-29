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

/**
 * Layout facade: reads kinship from [PersonEntity]/[UnionEntity], assigns Y via [generationMap],
 * X via [SugiyamaFamilyLayout] (full recompute each time). Rendering uses [TreePlacement] only.
 */
object TreeLayout {
    const val CARD_W = 168f
    const val CARD_H = 108f
    private const val MAX_CARD_H = 124f
    private const val SPOUSE_GAP = 28f
    private const val ROW_GAP = 76f
    private const val PAD = 16f

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
        val living = TreeIntegrity.livingIds(people)
        val generation = HashMap<String, Int>()
        val visiting = HashSet<String>()

        fun depth(id: String): Int {
            generation[id]?.let { return it }
            if (!visiting.add(id)) return 0
            val person = byId[id]
            val parents = listOfNotNull(person?.fatherId, person?.motherId)
                .filter { it in living && it in byId && it != id }
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

        repeat(people.size + unions.size + 4) {
            var changed = false
            people.forEach { person ->
                val parentGenerations = listOfNotNull(person.fatherId, person.motherId)
                    .filter { it in living && it in byId && it != person.id }
                    .map { generation.getValue(it) }
                if (parentGenerations.isNotEmpty()) {
                    val below = parentGenerations.max() + 1
                    if ((generation[person.id] ?: 0) < below) {
                        generation[person.id] = below
                        changed = true
                    }
                }
            }
            unions.forEach { union ->
                if (union.partnerAId !in byId || union.partnerBId !in byId) return@forEach
                val generationA = generation.getValue(union.partnerAId)
                val generationB = generation.getValue(union.partnerBId)
                if (generationA == generationB) return@forEach
                val parentsA = hasParents(union.partnerAId)
                val parentsB = hasParents(union.partnerBId)
                val shared = when {
                    !parentsA && parentsB -> generationB
                    parentsA && !parentsB -> generationA
                    !parentsA && !parentsB -> minOf(generationA, generationB)
                    else -> maxOf(generationA, generationB)
                }
                if (generationA != shared) {
                    generation[union.partnerAId] = shared
                    changed = true
                }
                if (generationB != shared) {
                    generation[union.partnerBId] = shared
                    changed = true
                }
            }
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
            if (!changed) return@repeat
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
        val livingPeople = TreeIntegrity.livingPeople(people)
        if (livingPeople.isEmpty()) return TreePlacement(emptyList(), emptyList(), emptyList(), 0f, 0f)
        val livingUnions = TreeIntegrity.livingUnions(unions, people)
        val byId = people.associateBy { it.id }
        val heights = livingPeople.associate { it.id to cardHeight(it) }
        val generation = generationMap(livingPeople, livingUnions, openLinks)
        val placed = SugiyamaFamilyLayout.place(
            people = livingPeople,
            unions = livingUnions,
            openLinks = openLinks,
            homePersonId = homePersonId?.takeIf { it in byId },
            cardW = CARD_W,
            gapX = SPOUSE_GAP,
            gapY = ROW_GAP,
            pad = PAD,
            heights = heights,
        )
        val rowHeight = livingPeople.groupBy { generation[it.id] ?: 0 }.mapValues { (_, band) ->
            band.maxOf { heights[it.id] ?: CARD_H }
        }
        val cards = placed.map { node ->
            val gen = generation[node.id] ?: 0
            TreeCard(node.id, node.left, node.top, rowHeight[gen] ?: node.height)
        }
        val childGroups = childGroupsOf(livingPeople, cards, openLinks, byId)
        val spouseLinks = livingUnions.map { it.partnerAId to it.partnerBId }.distinct()
        val width = (cards.maxOfOrNull { it.left + CARD_W } ?: PAD) + PAD
        val height = (cards.maxOfOrNull { it.top + it.height } ?: PAD) + PAD
        val knots = openLinks.mapNotNull { link -> knotFor(link, cards) }
        return TreePlacement(cards, spouseLinks, childGroups, width, height, knots)
    }

    private fun childGroupsOf(
        people: List<PersonEntity>,
        cards: List<TreeCard>,
        openLinks: List<OpenLink>,
        byId: Map<String, PersonEntity>,
    ): List<ChildGroup> {
        val placed = cards.map { it.id }.toSet()
        val groups = mutableListOf<ChildGroup>()
        people.filter { it.id in placed }.groupBy { person ->
            TreeIntegrity.livingParentIds(person, placed, byId).sorted()
        }.forEach { (parents, children) ->
            if (parents.isNotEmpty()) {
                groups += ChildGroup(parents, children.map { it.id })
            }
        }
        openLinks.filter { it.slot == "bridge" && it.anchorId in placed }.forEach { link ->
            val childIds = link.childIds.filter { childId ->
                val child = people.find { it.id == childId } ?: return@filter false
                childId in placed && child.fatherId != link.anchorId && child.motherId != link.anchorId
            }
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
