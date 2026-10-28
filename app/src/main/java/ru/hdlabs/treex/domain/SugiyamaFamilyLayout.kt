package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.max

/**
 * Descendant chart on family units (Reingold–Tilford tidy tree, 1981).
 *
 * A unit is a married couple or a single person. Units are visited from the
 * parents, children in birth order. The blood child stands on the side of
 * those parents; the spouse stands on the outer side, so a later parent
 * branch (and its own parents above) does not cross the others. Each sibling
 * group is then centered under its parent couple — spouses included.
 */
internal object SugiyamaFamilyLayout {
    data class Placed(val id: String, val left: Float, val top: Float, val height: Float)

    fun place(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink>,
        cardW: Float,
        gapX: Float,
        gapY: Float,
        pad: Float,
        heights: Map<String, Float>,
    ): List<Placed> {
        if (people.isEmpty()) return emptyList()
        val ids = people.map { it.id }.toSet()
        val generation = TreeLayout.generationMap(people, unions, openLinks)
        val byId = people.associateBy { it.id }
        val units = unitsOf(people, unions, generation)
        val byMember = HashMap<String, Unit>()
        units.forEach { unit -> unit.members.forEach { byMember[it] = unit } }
        val extraParents = HashMap<String, MutableList<String>>()
        openLinks.filter { it.slot == "bridge" && it.anchorIsParent && it.anchorId in ids }.forEach { link ->
            link.childIds.filter { it in ids }.forEach { childId ->
                extraParents.getOrPut(childId) { mutableListOf() }.add(link.anchorId)
            }
        }

        fun parentIdsOf(personId: String): List<String> {
            val person = byId[personId] ?: return emptyList()
            return (listOfNotNull(person.fatherId, person.motherId) + extraParents[personId].orEmpty())
                .filter { it in byMember }
        }

        val childrenOfPerson = HashMap<String, MutableList<String>>()
        people.forEach { person ->
            parentIdsOf(person.id).forEach { parentId ->
                childrenOfPerson.getOrPut(parentId) { mutableListOf() }.add(person.id)
            }
        }

        fun birthOf(id: String): String = byId[id]?.birthDate.orEmpty()

        fun kidsOf(parent: Unit): List<Unit> {
            return parent.members
                .flatMap { childrenOfPerson[it].orEmpty() }
                .mapNotNull { byMember[it] }
                .filter { it.generation == parent.generation + 1 }
                .distinct()
                .sortedWith(
                    compareBy(
                        { child ->
                            val blood = child.members.filter { member ->
                                parentIdsOf(member).any { byMember[it] === parent }
                            }
                            (blood.ifEmpty { child.members }).minOf { birthOf(it) }
                        },
                        { it.members.first() },
                    ),
                )
        }

        fun cardsWidth(unit: Unit): Float =
            unit.members.size * cardW + (unit.members.size - 1) * gapX

        val primaryParent = HashMap<Unit, Unit>()
        val layers = units.groupBy { it.generation }.mapValues { it.value.toMutableList() }.toSortedMap()

        fun parentRank(personId: String): Int? {
            val ranks = parentIdsOf(personId).mapNotNull { parentId ->
                val parent = byMember[parentId] ?: return@mapNotNull null
                val layer = layers[parent.generation] ?: return@mapNotNull null
                val index = layer.indexOf(parent)
                if (index < 0) null else index
            }
            return ranks.minOrNull()
        }

        fun orderKey(unit: Unit): String {
            val parents = unit.members.flatMap { parentIdsOf(it) }.mapNotNull { byMember[it] }.distinct()
            val underCouple = parents.any { it.members.size >= 2 }
            if (underCouple) {
                val blood = unit.members.filter { parentIdsOf(it).isNotEmpty() }
                return (blood.ifEmpty { unit.members }).minOf { birthOf(it) }
            }
            val childBirth = kidsOf(unit).minOfOrNull { child ->
                val blood = child.members.filter { member ->
                    parentIdsOf(member).any { byMember[it] === unit }
                }
                (blood.ifEmpty { child.members }).minOf { birthOf(it) }
            }
            return childBirth ?: unit.members.minOf { birthOf(it) }
        }

        fun orient(unit: Unit) {
            if (unit.members.size != 2) return
            val leftRank = parentRank(unit.members[0])
            val rightRank = parentRank(unit.members[1])
            val swap = when {
                leftRank != null && rightRank != null -> leftRank > rightRank
                leftRank == null && rightRank != null -> true
                else -> false
            }
            if (swap) unit.members = listOf(unit.members[1], unit.members[0])
        }

        layers.keys.sorted().forEach { gen ->
            val layer = layers.getValue(gen)
            layer.sortWith(compareBy({ orderKey(it) }, { it.members.minOf { id -> birthOf(id) } }, { it.members.first() }))
            layer.forEach { unit -> orient(unit) }
            layer.forEach { unit ->
                val parents = unit.members.flatMap { parentIdsOf(it) }.mapNotNull { byMember[it] }.distinct()
                val primary = parents.minByOrNull { parent ->
                    layers[parent.generation]?.indexOf(parent) ?: Int.MAX_VALUE
                }
                if (primary != null) primaryParent[unit] = primary
            }
        }
        units.forEach { unit ->
            unit.innerGap = gapX
            unit.width = cardsWidth(unit)
            unit.height = unit.members.maxOf { heights[it] ?: TreeLayout.CARD_H }
        }
        layers.forEach { (_, layer) ->
            val rowH = layer.maxOf { it.height }
            layer.forEach { it.height = rowH }
        }

        fun shift(unit: Unit, delta: Float) {
            if (delta == 0f) return
            unit.left += delta
            kidsOf(unit).forEach { child ->
                if (primaryParent[child] === unit) shift(child, delta)
            }
        }

        fun placeRow(row: List<Unit>, parentGap: Float) {
            if (row.isEmpty()) return
            var cursor = Float.NEGATIVE_INFINITY
            var index = 0
            while (index < row.size) {
                val parent = primaryParent[row[index]]
                val group = mutableListOf<Unit>()
                while (index < row.size && primaryParent[row[index]] === parent) {
                    group += row[index]
                    index += 1
                }
                val gaps = (group.size - 1) * SIBLING_GAP
                val groupWidth = group.sumOf { it.width.toDouble() }.toFloat() + gaps
                val desired = if (parent == null) {
                    if (cursor == Float.NEGATIVE_INFINITY) 0f else cursor
                } else {
                    parent.left + parent.width / 2f - groupWidth / 2f
                }
                var x = max(desired, if (cursor == Float.NEGATIVE_INFINITY) desired else cursor)
                group.forEach { unit ->
                    shift(unit, x - unit.left)
                    x += unit.width + SIBLING_GAP
                }
                cursor = x - SIBLING_GAP + parentGap
            }
        }

        layers.keys.sorted().forEach { gen ->
            val gap = if (gen == layers.firstKey()) FAMILY_GAP else SIBLING_GAP
            placeRow(layers.getValue(gen), gap)
        }

        layers.keys.sortedDescending().forEach { gen ->
            val layer = layers.getValue(gen)
            layer.forEach { unit ->
                val centers = kidsOf(unit).flatMap { child ->
                    child.members.filter { member ->
                        parentIdsOf(member).any { byMember[it] === unit }
                    }.map { member ->
                        val index = child.members.indexOf(member)
                        child.left + index * (cardW + gapX) + cardW / 2f
                    }
                }
                if (centers.isNotEmpty()) {
                    unit.left = centers.average().toFloat() - unit.width / 2f
                }
            }
            var cursor = Float.NEGATIVE_INFINITY
            val gap = if (gen == layers.firstKey()) FAMILY_GAP else SIBLING_GAP
            layer.forEach { unit ->
                if (cursor != Float.NEGATIVE_INFINITY && unit.left < cursor + gap) {
                    val delta = cursor + gap - unit.left
                    if (gen == layers.firstKey()) unit.left += delta else shift(unit, delta)
                }
                cursor = unit.left + unit.width
            }
        }

        layers.keys.sorted().drop(1).forEach { gen ->
            val parents = layers[gen - 1] ?: return@forEach
            var edge = Float.NEGATIVE_INFINITY
            parents.forEach { parent ->
                val kids = kidsOf(parent).filter { primaryParent[it] === parent }
                if (kids.isEmpty()) return@forEach
                val left = kids.minOf { it.left }
                val overlap = edge + SIBLING_GAP - left
                if (overlap > 0f) shift(parent, overlap)
                val placedKids = kidsOf(parent).filter { primaryParent[it] === parent }
                edge = max(edge, placedKids.maxOf { it.left + it.width })
            }
        }
        val topGen = layers.firstKey()
        layers.getValue(topGen).forEach { unit ->
            val centers = kidsOf(unit).flatMap { child ->
                child.members.filter { member ->
                    parentIdsOf(member).any { byMember[it] === unit }
                }.map { member ->
                    val index = child.members.indexOf(member)
                    child.left + index * (cardW + gapX) + cardW / 2f
                }
            }
            if (centers.isNotEmpty()) {
                unit.left = centers.average().toFloat() - unit.width / 2f
            }
        }
        var topCursor = Float.NEGATIVE_INFINITY
        layers.getValue(topGen).forEach { unit ->
            if (topCursor != Float.NEGATIVE_INFINITY && unit.left < topCursor + FAMILY_GAP) {
                unit.left = topCursor + FAMILY_GAP
            }
            topCursor = unit.left + unit.width
        }

        val minLeft = units.minOf { it.left }
        if (minLeft < pad) units.forEach { it.left += pad - minLeft }
        val topOf = HashMap<Int, Float>()
        var y = pad
        layers.keys.sorted().forEach { gen ->
            topOf[gen] = y
            y += layers.getValue(gen).maxOf { it.height } + gapY
        }

        return units.flatMap { unit ->
            unit.members.mapIndexed { index, id ->
                Placed(
                    id = id,
                    left = unit.left + index * (cardW + unit.innerGap),
                    top = topOf.getValue(unit.generation),
                    height = unit.height,
                )
            }
        }
    }

    private fun unitsOf(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        generation: Map<String, Int>,
    ): List<Unit> {
        val byId = people.associateBy { it.id }
        val remaining = people.map { it.id }.toMutableSet()
        val units = mutableListOf<Unit>()
        unions.forEach { union ->
            if (union.partnerAId !in remaining || union.partnerBId !in remaining) return@forEach
            val genA = generation.getValue(union.partnerAId)
            val genB = generation.getValue(union.partnerBId)
            if (genA != genB) return@forEach
            val members = listOf(union.partnerAId, union.partnerBId).sortedWith(
                compareBy({ byId.getValue(it).gender == "f" }, { byId.getValue(it).birthDate }, { it }),
            )
            units += Unit(members, genA)
            remaining.removeAll(members.toSet())
        }
        remaining.sortedWith(
            compareBy({ generation.getValue(it) }, { byId.getValue(it).birthDate }, { it }),
        ).forEach { id ->
            units += Unit(listOf(id), generation.getValue(id))
        }
        return units
    }

    private const val SIBLING_GAP = 44f
    private const val FAMILY_GAP = 160f

    private class Unit(
        var members: List<String>,
        val generation: Int,
        var left: Float = 0f,
        var width: Float = 0f,
        var height: Float = 0f,
        var innerGap: Float = 0f,
    )
}
