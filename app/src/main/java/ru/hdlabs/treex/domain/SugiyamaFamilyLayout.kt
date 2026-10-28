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

        val seen = HashSet<Unit>()
        val primaryParent = HashMap<Unit, Unit>()
        val visitOrder = mutableListOf<Unit>()

        fun orient(child: Unit, parent: Unit) {
            if (child.members.size != 2) return
            val blood = child.members.filter { member ->
                parentIdsOf(member).any { byMember[it] === parent }
            }
            val other = child.members.filter { it !in blood }
            if (blood.size != 1 || other.size != 1) return
            val spouseParents = parentIdsOf(other[0]).mapNotNull { byMember[it] }.distinct()
            val spouseOnLeft = spouseParents.any { seen.contains(it) && it !== parent }
            child.members = if (spouseOnLeft) listOf(other[0], blood[0]) else listOf(blood[0], other[0])
        }

        fun walk(parent: Unit) {
            kidsOf(parent).forEach { child ->
                if (!seen.add(child)) return@forEach
                primaryParent[child] = parent
                orient(child, parent)
                visitOrder += child
                walk(child)
            }
        }

        val roots = units
            .filter { unit -> unit.members.none { parentIdsOf(it).isNotEmpty() } }
            .sortedWith(
                compareBy(
                    { unit -> kidsOf(unit).isEmpty() },
                    { unit ->
                        kidsOf(unit).minOfOrNull { child ->
                            child.members.minOf { birthOf(it) }
                        } ?: unit.members.minOf { birthOf(it) }
                    },
                    { it.members.minOf { id -> birthOf(id) } },
                ),
            )
        roots.forEach { root ->
            if (!seen.add(root)) return@forEach
            visitOrder += root
            walk(root)
        }
        units.sortedWith(
            compareBy({ it.generation }, { it.members.minOf { id -> birthOf(id) } }, { it.members.first() }),
        ).forEach { unit ->
            if (seen.add(unit)) {
                visitOrder += unit
                walk(unit)
            }
        }

        units.forEach { unit ->
            unit.innerGap = gapX
            unit.width = cardsWidth(unit)
            unit.height = unit.members.maxOf { heights[it] ?: TreeLayout.CARD_H }
        }
        val layers = visitOrder.groupBy { it.generation }.mapValues { it.value.toMutableList() }.toSortedMap()
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
