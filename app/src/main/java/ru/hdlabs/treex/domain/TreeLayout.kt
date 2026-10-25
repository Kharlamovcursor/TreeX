package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.max

data class TreeCard(val id: String, val left: Float, val top: Float)

data class ChildGroup(val parentIds: List<String>, val childIds: List<String>)

data class TreePlacement(
    val cards: List<TreeCard>,
    val spouseLinks: List<Pair<String, String>>,
    val childGroups: List<ChildGroup>,
    val width: Float,
    val height: Float,
)

object TreeLayout {
    const val CARD_W = 148f
    const val CARD_H = 108f
    private const val SPOUSE_GAP = 28f
    private const val HOUSE_GAP = 44f
    private const val ROW_GAP = 76f
    private const val PAD = 16f
    fun bands(people: List<PersonEntity>, unions: List<UnionEntity>): List<List<PersonEntity>> {
        if (people.isEmpty()) return emptyList()
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
                }
            }
            if (!changed) break
        }

        val minGeneration = generation.values.minOrNull() ?: 0
        return people
            .groupBy { (generation[it.id] ?: 0) - minGeneration }
            .toSortedMap()
            .values
            .map { band ->
                band.sortedWith(
                    compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }, { it.id }),
                )
            }
    }

    fun place(people: List<PersonEntity>, unions: List<UnionEntity>): TreePlacement {
        if (people.isEmpty()) return TreePlacement(emptyList(), emptyList(), emptyList(), 0f, 0f)
        val byId = people.associateBy { it.id }
        val spouses = people.associate { person ->
            person.id to spouseIdsOf(unions, person.id).filter { it in byId && it != person.id }
        }
        fun hasParents(id: String): Boolean {
            val person = byId[id] ?: return false
            return (person.fatherId != null && person.fatherId in byId) ||
                (person.motherId != null && person.motherId in byId)
        }
        fun isMarriedIn(id: String): Boolean =
            !hasParents(id) && spouses.getValue(id).any { hasParents(it) }
        fun parentsOf(person: PersonEntity): List<String> =
            listOfNotNull(person.fatherId, person.motherId).filter { it in byId && it != person.id }

        fun buildHouse(bloodIds: List<String>, stack: Set<String>): House {
            val safe = bloodIds.filter { it !in stack }.distinct()
            if (safe.isEmpty()) return House(bloodIds.distinct(), bloodIds.distinct(), emptyList())
            val members = orderMembers(safe, spouses, byId, ::isMarriedIn)
            val memberSet = members.toSet()
            val nextStack = stack + memberSet
            val children = people
                .filter { child ->
                    val parents = parentsOf(child)
                    child.id !in nextStack && parents.isNotEmpty() && parents.all { it in memberSet }
                }
                .sortedWith(compareBy({ it.birthDate }, { it.id }))
                .map { child -> buildHouse(listOf(child.id), nextStack) }
            return House(safe, members, children)
        }

        val roots = mutableListOf<House>()
        val seen = mutableSetOf<String>()
        val rootBlood = people.map { it.id }
            .filter { !hasParents(it) && !isMarriedIn(it) }
            .sortedWith(compareBy({ byId.getValue(it).birthDate }, { byId.getValue(it).lastName }, { it }))
        for (id in rootBlood) {
            if (id in seen) continue
            val component = mutableListOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(id)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!seen.add(current) || hasParents(current) || isMarriedIn(current)) continue
                component.add(current)
                spouses.getValue(current).forEach { queue.add(it) }
            }
            if (component.isNotEmpty()) roots.add(buildHouse(component, emptySet()))
        }
        val placedIds = mutableSetOf<String>()
        fun collect(house: House) {
            placedIds.addAll(house.members)
            house.children.forEach { collect(it) }
        }
        roots.forEach { collect(it) }
        people.filter { it.id !in placedIds }.sortedBy { it.id }.forEach { person ->
            roots.add(buildHouse(listOf(person.id), placedIds.toSet()))
        }

        val cards = mutableListOf<TreeCard>()
        val childGroups = mutableListOf<ChildGroup>()
        var cursor = PAD
        roots.forEach { house ->
            measure(house)
            position(house, cursor, PAD, cards, childGroups, byId)
            cursor += house.width + HOUSE_GAP
        }
        val spouseLinks = unions.mapNotNull { union ->
            if (union.partnerAId in byId && union.partnerBId in byId && union.partnerAId != union.partnerBId) {
                union.partnerAId to union.partnerBId
            } else {
                null
            }
        }.distinct()
        val width = (cards.maxOfOrNull { it.left + CARD_W } ?: PAD) + PAD
        val height = (cards.maxOfOrNull { it.top + CARD_H } ?: PAD) + PAD
        return TreePlacement(cards, spouseLinks, childGroups, width, height)
    }

    private fun orderMembers(
        bloodIds: List<String>,
        spouses: Map<String, List<String>>,
        byId: Map<String, PersonEntity>,
        isMarriedIn: (String) -> Boolean,
    ): List<String> {
        val blood = bloodIds.distinct().sortedWith(
            compareBy({ genderRank(byId.getValue(it).gender) }, { byId.getValue(it).birthDate }, { it }),
        )
        val mates = blood.flatMap { spouses.getValue(it) }
            .distinct()
            .filter { isMarriedIn(it) && it !in blood }
        val used = mutableSetOf<String>()
        val ordered = mutableListOf<String>()
        for (person in blood) {
            val mine = mates.filter { it !in used && it in spouses.getValue(person) }
            val left = mine.filter { byId.getValue(it).gender == "m" && byId.getValue(person).gender != "m" }
            val right = mine.filter { it !in left }
            left.forEach { used.add(it); ordered.add(it) }
            if (used.add(person)) ordered.add(person)
            right.forEach { used.add(it); ordered.add(it) }
        }
        return ordered
    }

    private fun genderRank(gender: String): Int = when (gender) {
        "m" -> 0
        "f" -> 1
        else -> 2
    }

    private fun memberWidth(count: Int): Float =
        if (count <= 0) 0f else count * CARD_W + (count - 1) * SPOUSE_GAP

    private fun measure(house: House): Float {
        house.children.forEach { measure(it) }
        val childrenWidth = if (house.children.isEmpty()) {
            0f
        } else {
            house.children.sumOf { it.width.toDouble() }.toFloat() + (house.children.size - 1) * HOUSE_GAP
        }
        house.width = max(memberWidth(house.members.size), childrenWidth)
        return house.width
    }

    private fun position(
        house: House,
        left: Float,
        top: Float,
        cards: MutableList<TreeCard>,
        childGroups: MutableList<ChildGroup>,
        byId: Map<String, PersonEntity>,
    ) {
        house.left = left
        house.top = top
        val selfWidth = memberWidth(house.members.size)
        val membersLeft = left + (house.width - selfWidth) / 2f
        house.members.forEachIndexed { index, id ->
            cards.add(TreeCard(id, membersLeft + index * (CARD_W + SPOUSE_GAP), top))
        }
        if (house.children.isNotEmpty()) {
            val childrenWidth = house.children.sumOf { it.width.toDouble() }.toFloat() +
                (house.children.size - 1) * HOUSE_GAP
            var childLeft = left + (house.width - childrenWidth) / 2f
            val childTop = top + CARD_H + ROW_GAP
            house.children.forEach { child ->
                position(child, childLeft, childTop, cards, childGroups, byId)
                childLeft += child.width + HOUSE_GAP
            }
            house.children.groupBy { child ->
                val anchor = byId[child.anchors.firstOrNull()]
                listOfNotNull(anchor?.fatherId, anchor?.motherId).filter { it in byId }.sorted()
            }.forEach { (parents, children) ->
                val childIds = children.mapNotNull { it.anchors.firstOrNull() }
                if (parents.isNotEmpty() && childIds.isNotEmpty()) {
                    childGroups.add(ChildGroup(parents, childIds))
                }
            }
        }
    }

    private class House(
        val anchors: List<String>,
        val members: List<String>,
        val children: List<House>,
        var width: Float = 0f,
        var left: Float = 0f,
        var top: Float = 0f,
    )
}
