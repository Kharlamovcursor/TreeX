package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.max

/**
 * Layered family layout on genealogy units (couple or single).
 *
 * Ranks are generations. Crossing reduction is the barycenter heuristic from
 * Sugiyama, Tagawa, Toda (1981), with *ports* on each spouse so a wife's
 * parents attach to her card, not the couple midpoint. Horizontal coordinates
 * follow the Graphviz/ELK layered sweep: center over neighbors, then pack
 * left-to-right **in layer order**. Spouses stay one card-gap apart. Inside a
 * couple the partner whose parents sit further left takes the left card.
 * Units that do not share parents keep a wider gutter than siblings.
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

        fun childPersonsOf(unit: Unit): List<String> =
            unit.members.flatMap { childrenOfPerson[it].orEmpty() }
                .filter { childId ->
                    val child = byMember[childId] ?: return@filter false
                    child.generation == unit.generation + 1
                }
                .distinct()

        val layers = units.groupBy { it.generation }.mapValues { it.value.toMutableList() }.toSortedMap()
        layers.forEach { (_, layer) ->
            layer.sortWith(
                compareBy(
                    { it.members.minOf { id -> byId.getValue(id).birthDate } },
                    { it.members.first() },
                ),
            )
        }

        fun port(personId: String, layer: List<Unit>): Float? {
            val unit = byMember[personId] ?: return null
            val index = layer.indexOf(unit)
            if (index < 0) return null
            val slot = unit.members.indexOf(personId).coerceAtLeast(0)
            return index + slot / unit.members.size.toFloat()
        }

        fun barycenter(ports: List<Float>, fallback: Float): Float =
            if (ports.isEmpty()) fallback else ports.average().toFloat()

        val pivot = layers.keys.sorted().let { keys -> keys[keys.size / 2] }
        fun byBirth(unit: Unit): Pair<String, String> =
            unit.members.minOf { id -> byId.getValue(id).birthDate } to unit.members.first()
        for (gen in layers.keys.filter { it < pivot }.sortedDescending()) {
            val layer = layers.getValue(gen)
            val next = layers[gen + 1] ?: continue
            layer.sortWith(
                compareBy(
                    { unit ->
                        barycenter(childPersonsOf(unit).mapNotNull { port(it, next) }, layer.size / 2f)
                    },
                    { byBirth(it).first },
                    { byBirth(it).second },
                ),
            )
        }
        for (gen in layers.keys.filter { it > pivot }.sorted()) {
            val layer = layers.getValue(gen)
            val prev = layers[gen - 1] ?: continue
            layer.sortWith(
                compareBy(
                    { unit ->
                        barycenter(
                            unit.members.flatMap { parentIdsOf(it) }.mapNotNull { port(it, prev) },
                            layer.size / 2f,
                        )
                    },
                    { byBirth(it).first },
                    { byBirth(it).second },
                ),
            )
        }

        fun personCenter(personId: String): Float? {
            val unit = byMember[personId] ?: return null
            val index = unit.members.indexOf(personId)
            if (index < 0) return null
            return unit.left + index * (cardW + unit.innerGap) + cardW / 2f
        }

        fun parentCenter(personId: String): Float? {
            val centers = parentIdsOf(personId).mapNotNull { personCenter(it) }
            return if (centers.isEmpty()) null else centers.average().toFloat()
        }

        fun orientTowardParents(unit: Unit) {
            if (unit.members.size != 2) return
            val leftId = unit.members[0]
            val rightId = unit.members[1]
            val leftParents = parentCenter(leftId)
            val rightParents = parentCenter(rightId)
            val coupleMid = unit.left + unit.width / 2f
            val swap = when {
                leftParents != null && rightParents != null -> leftParents > rightParents + 1f
                leftParents != null -> leftParents > coupleMid + 8f
                rightParents != null -> rightParents < coupleMid - 8f
                else -> false
            }
            if (swap) unit.members = listOf(rightId, leftId)
        }

        fun orientByParentOrder() {
            layers.keys.sorted().forEach { gen ->
                val prev = layers[gen - 1] ?: return@forEach
                layers.getValue(gen).forEach { unit ->
                    if (unit.members.size != 2) return@forEach
                    fun key(id: String): Float? {
                        val ports = parentIdsOf(id).mapNotNull { port(it, prev) }
                        return if (ports.isEmpty()) null else ports.average().toFloat()
                    }
                    val leftKey = key(unit.members[0])
                    val rightKey = key(unit.members[1])
                    val swap = when {
                        leftKey != null && rightKey != null -> leftKey > rightKey
                        leftKey == null && rightKey != null -> true
                        else -> false
                    }
                    if (swap) unit.members = listOf(unit.members[1], unit.members[0])
                }
            }
        }

        orientByParentOrder()
        units.forEach { unit ->
            unit.innerGap = gapX
            unit.width = unit.members.size * cardW + (unit.members.size - 1) * gapX
            unit.height = unit.members.maxOf { heights[it] ?: TreeLayout.CARD_H }
        }
        layers.forEach { (_, layer) ->
            val rowH = layer.maxOf { it.height }
            layer.forEach { it.height = rowH }
        }

        fun separation(left: Unit, right: Unit): Float {
            val leftParents = left.members.flatMap { parentIdsOf(it) }.mapNotNull { byMember[it] }.toSet()
            val rightParents = right.members.flatMap { parentIdsOf(it) }.mapNotNull { byMember[it] }.toSet()
            return if (leftParents.intersect(rightParents).isNotEmpty()) SIBLING_GAP else FAMILY_GAP
        }

        fun pack(layer: List<Unit>) {
            layer.forEachIndexed { index, unit ->
                if (index == 0) return@forEachIndexed
                val previous = layer[index - 1]
                val minLeft = previous.left + previous.width + separation(previous, unit)
                unit.left = max(unit.left, minLeft)
            }
        }

        fun pullTo(unit: Unit, targets: List<Float>, blend: Float) {
            if (targets.isEmpty()) return
            val desired = targets.average().toFloat() - unit.width / 2f
            unit.left = (1f - blend) * unit.left + blend * desired
        }

        repeat(4) { sweep ->
            val fromChildren = if (sweep == 0) 1f else 0.55f
            layers.keys.sortedDescending().forEach { gen ->
                val layer = layers.getValue(gen)
                layer.forEach { unit ->
                    val targets = childPersonsOf(unit).mapNotNull { personCenter(it) }
                    pullTo(unit, targets, fromChildren)
                }
                pack(layer)
            }
            layers.keys.sorted().forEach { gen ->
                val layer = layers.getValue(gen)
                layer.forEach { unit -> orientTowardParents(unit) }
                layer.forEach { unit ->
                    val targets = unit.members.flatMap { parentIdsOf(it) }.mapNotNull { personCenter(it) }
                    pullTo(unit, targets, 0.45f)
                }
                pack(layer)
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
