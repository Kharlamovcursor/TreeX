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
 * left-to-right **in layer order**. If both spouses have parents, the inner
 * gap grows until those parent units fit above each column — the standard
 * port-clearance rule for family charts.
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
        houseGap: Float = 44f,
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

        fun spouseNeighborUnits(unit: Unit): List<Unit> {
            val members = unit.members.toSet()
            return unions.mapNotNull { union ->
                val other = when {
                    union.partnerAId in members && union.partnerBId in byMember && union.partnerBId !in members ->
                        byMember[union.partnerBId]
                    union.partnerBId in members && union.partnerAId in byMember && union.partnerAId !in members ->
                        byMember[union.partnerAId]
                    else -> null
                }
                other?.takeIf { it.generation == unit.generation }
            }.distinct()
        }

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

        repeat(24) { pass ->
            if (pass % 2 == 0) {
                layers.keys.sorted().drop(1).forEach { gen ->
                    val layer = layers.getValue(gen)
                    val prev = layers[gen - 1] ?: return@forEach
                    layer.sortWith(
                        compareBy(
                            { unit ->
                                val ports = unit.members.flatMap { parentIdsOf(it) }.mapNotNull { port(it, prev) } +
                                    spouseNeighborUnits(unit).mapNotNull { neighbor ->
                                        val idx = layer.indexOf(neighbor)
                                        if (idx >= 0) idx.toFloat() else null
                                    }
                                barycenter(ports, layer.indexOf(unit).toFloat())
                            },
                            { it.members.minOf { id -> byId.getValue(id).birthDate } },
                            { it.members.first() },
                        ),
                    )
                }
            } else {
                layers.keys.sortedDescending().drop(1).forEach { gen ->
                    val layer = layers.getValue(gen)
                    val next = layers[gen + 1] ?: return@forEach
                    layer.sortWith(
                        compareBy(
                            { unit ->
                                val ports = childPersonsOf(unit).mapNotNull { port(it, next) } +
                                    spouseNeighborUnits(unit).mapNotNull { neighbor ->
                                        val idx = layer.indexOf(neighbor)
                                        if (idx >= 0) idx.toFloat() else null
                                    }
                                barycenter(ports, layer.indexOf(unit).toFloat())
                            },
                            { it.members.minOf { id -> byId.getValue(id).birthDate } },
                            { it.members.first() },
                        ),
                    )
                }
            }
        }

        layers.keys.sorted().forEach { gen ->
            layers.getValue(gen).forEach { unit ->
                unit.innerGap = gapX
                if (unit.members.size == 2) {
                    val leftParents = parentIdsOf(unit.members[0]).mapNotNull { byMember[it] }
                        .filter { it.generation == unit.generation - 1 }
                        .distinct()
                    val rightParents = parentIdsOf(unit.members[1]).mapNotNull { byMember[it] }
                        .filter { it.generation == unit.generation - 1 }
                        .distinct()
                    if (leftParents.isNotEmpty() && rightParents.isNotEmpty() &&
                        leftParents.toSet() != rightParents.toSet()
                    ) {
                        val need = (leftParents.sumOf { it.width.toDouble() }.toFloat() +
                            rightParents.sumOf { it.width.toDouble() }.toFloat()) / 2f + houseGap
                        unit.innerGap = max(gapX, need - cardW)
                    }
                }
                unit.width = unit.members.size * cardW + (unit.members.size - 1) * unit.innerGap
                unit.height = unit.members.maxOf { heights[it] ?: TreeLayout.CARD_H }
            }
        }
        layers.forEach { (_, layer) ->
            val rowH = layer.maxOf { it.height }
            layer.forEach { it.height = rowH }
        }

        fun personCenter(personId: String, cardW: Float): Float? {
            val unit = byMember[personId] ?: return null
            val index = unit.members.indexOf(personId)
            if (index < 0) return null
            return unit.left + index * (cardW + unit.innerGap) + cardW / 2f
        }

        fun pack(layer: List<Unit>) {
            var cursor = Float.NEGATIVE_INFINITY
            layer.forEach { unit ->
                unit.left = max(unit.left, cursor)
                cursor = unit.left + unit.width + houseGap
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
                    val targets = childPersonsOf(unit).mapNotNull { personCenter(it, cardW) }
                    pullTo(unit, targets, fromChildren)
                }
                pack(layer)
            }
            layers.keys.sorted().forEach { gen ->
                val layer = layers.getValue(gen)
                layer.forEach { unit ->
                    val targets = unit.members.flatMap { parentIdsOf(it) }.mapNotNull { personCenter(it, cardW) }
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

    private class Unit(
        val members: List<String>,
        val generation: Int,
        var left: Float = 0f,
        var width: Float = 0f,
        var height: Float = 0f,
        var innerGap: Float = 0f,
    )
}
