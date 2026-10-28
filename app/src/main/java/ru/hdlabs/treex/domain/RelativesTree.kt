package ru.hdlabs.treex.domain

import kotlin.math.floor

/**
 * Family-graph layout from relatives-tree 3.2.0 (MIT, Sanich Kotikov).
 * https://github.com/SanichKotikov/relatives-tree
 *
 * Pipeline: root family → ancestors up → descendants down → correct origin.
 * Each person is placed once; spouses share a unit; children sit with that unit.
 */
internal object RelativesTree {
    const val SIZE = 2.0
    private const val NODES_IN_COUPLE = 2

    enum class Gender { Male, Female }
    enum class RelType { Blood, Married, Divorced, Adopted, Half }
    enum class FamilyType { Root, Child, Parent }

    data class Relation(val id: String, val type: RelType)

    class Node(
        val id: String,
        val gender: Gender,
        val parents: MutableList<Relation> = mutableListOf(),
        val children: MutableList<Relation> = mutableListOf(),
        val siblings: MutableList<Relation> = mutableListOf(),
        val spouses: MutableList<Relation> = mutableListOf(),
    )

    data class Unit(
        val fid: Int,
        val child: Boolean,
        val nodes: List<Node>,
        var pos: Double = 0.0,
    )

    class Family(
        val id: Int,
        val type: FamilyType,
        val main: Boolean,
        var pid: Int? = null,
        var cid: Int? = null,
        var x: Double = 0.0,
        var y: Double = 0.0,
        var parents: MutableList<Unit> = mutableListOf(),
        var children: MutableList<Unit> = mutableListOf(),
    )

    data class ExtNode(val id: String, val left: Double, val top: Double)

    class Store(nodes: List<Node>, rootId: String) {
        private var nextId = 0
        val families = LinkedHashMap<Int, Family>()
        val nodes: Map<String, Node> = nodes.associate { it.id to it }
        val root: Node = this.nodes[rootId] ?: throw NoSuchElementException("root $rootId")

        fun getNextId(): Int = ++nextId
        fun getNode(id: String): Node = nodes.getValue(id)
        fun getNodes(ids: List<String>): List<Node> = ids.map { getNode(it) }
        fun getFamily(id: Int): Family = families.getValue(id)
        val familiesArray: List<Family> get() = families.values.toList()
        val rootFamilies: List<Family> get() = familiesArray.filter { it.type == FamilyType.Root }
        val rootFamily: Family get() = rootFamilies.first { it.main }
    }

    fun calc(nodes: List<Node>, rootId: String): List<ExtNode> {
        if (nodes.none { it.id == rootId }) return emptyList()
        val store = Store(nodes, rootId)
        inMiddleDirection(store)
        inParentDirection(store)
        inChildDirection(store)
        inLawParentDirection(store)
        correctPositions(store)
        return uniqueNodes(getExtendedNodes(store.familiesArray))
    }

    private fun uniqueNodes(nodes: List<ExtNode>): List<ExtNode> {
        val seen = LinkedHashMap<String, ExtNode>()
        nodes.forEach { node -> seen.putIfAbsent(node.id, node) }
        return seen.values.toList()
    }

    private fun newFamily(id: Int, type: FamilyType, main: Boolean = false) =
        Family(id, type, main)

    private fun newUnit(fid: Int, nodes: List<Node>, isChild: Boolean = false) =
        Unit(fid, isChild, nodes.toList())

    private fun nodeIds(unit: Unit): List<String> = unit.nodes.map { it.id }
    private fun nodeCount(unit: Unit): Int = unit.nodes.size
    private fun hasChildren(unit: Unit): Boolean = unit.nodes.any { it.children.isNotEmpty() }
    private fun rightSide(unit: Unit): Double = unit.pos + nodeCount(unit) * SIZE
    private fun sameAs(target: Unit): (Unit) -> Boolean =
        { unit -> nodeIds(target).joinToString("") == nodeIds(unit).joinToString("") }

    private fun getUnitX(family: Family, unit: Unit): Double = family.x + unit.pos
    private fun unitsToNodes(units: List<Unit>): List<Node> = units.flatMap { it.nodes }
    private fun unitNodesCount(units: List<Unit>): Int = units.sumOf { nodeCount(it) }

    private fun arrangeInOrder(units: List<Unit>) {
        units.forEachIndexed { idx, unit ->
            unit.pos = if (idx == 0) 0.0 else rightSide(units[idx - 1])
        }
    }

    private fun correctUnitsShift(units: List<Unit>, shift: Double) {
        units.forEach { it.pos += shift }
    }

    private fun widthOf(family: Family): Double =
        (family.parents + family.children).maxOfOrNull { rightSide(it) } ?: 0.0

    private fun heightOf(family: Family): Double {
        val rows = listOf(family.parents.isNotEmpty(), family.children.isNotEmpty()).count { it }
        return rows * SIZE
    }

    private fun rightOf(family: Family): Double = family.x + widthOf(family)

    private fun unitY(family: Family, unit: Unit): Double =
        family.y + if (unit.child && family.parents.isNotEmpty()) SIZE else 0.0

    private fun byGender(target: Gender): Comparator<Node> =
        Comparator { _, b -> if (b.gender != target) -1 else 1 }

    private fun withRelType(type: RelType): (Relation) -> Boolean = { it.type == type }

    private fun hasDiffParents(node: Node): Boolean =
        node.parents.map { it.type }.distinct().size > 1

    private fun setDefaultUnitShift(family: Family) {
        arrangeInOrder(family.parents)
        arrangeInOrder(family.children)
        val diff = unitNodesCount(family.parents) - unitNodesCount(family.children)
        when {
            diff > 0 -> correctUnitsShift(family.children, diff.toDouble())
            diff < 0 -> correctUnitsShift(family.parents, (-diff).toDouble())
        }
        val start = (family.parents + family.children).minOfOrNull { it.pos } ?: 0.0
        if (start != 0.0) {
            correctUnitsShift(family.parents, -start)
            correctUnitsShift(family.children, -start)
        }
    }

    private fun getSpouse(store: Store, spouses: List<Relation>): Node? {
        val married = spouses.find(withRelType(RelType.Married))
        if (married != null) return store.getNode(married.id)
        if (spouses.isEmpty()) return null
        return spouses.map { store.getNode(it.id) }.maxByOrNull { it.children.size }
    }

    private fun getCoupleNodes(store: Store, target: Node): List<Node> =
        listOfNotNull(target, getSpouse(store, target.spouses)).sortedWith(byGender(store.root.gender))

    private fun getSpouseNodes(store: Store, parents: List<Node>): Triple<List<Node>, List<Node>, List<Node>> {
        var middle = parents
        if (middle.size != NODES_IN_COUPLE) {
            middle = getCoupleNodes(store, middle.first())
        }
        var left = emptyList<Node>()
        var right = emptyList<Node>()
        if (middle.size == NODES_IN_COUPLE) {
            val first = middle[0]
            val second = middle[1]
            left = first.spouses.filter { it.id != second.id }.map { store.getNode(it.id) }
            right = second.spouses.filter { it.id != first.id }.map { store.getNode(it.id) }
        }
        return Triple(left, middle, right)
    }

    private fun createChildUnits(store: Store, familyId: Int, child: Node): List<Unit> {
        val (left, middle, right) = getSpouseNodes(store, listOf(child))
        return (left.map { listOf(it) } + listOf(middle) + right.map { listOf(it) })
            .map { nodes -> newUnit(familyId, nodes, isChild = true) }
    }

    private fun childUnitsOfParents(store: Store, familyId: Int, parents: List<Node>): List<Unit> {
        val first = parents.first()
        val second = parents.getOrNull(1)
        return first.children
            .filter { rel -> second == null || second.children.any { it.id == rel.id } }
            .flatMap { rel -> createChildUnits(store, familyId, store.getNode(rel.id)) }
    }

    private fun createFamilyByParents(
        store: Store,
        parentIds: List<String>,
        type: FamilyType = FamilyType.Root,
        isMain: Boolean = false,
    ): Family {
        val family = newFamily(store.getNextId(), type, isMain)
        val parents = parentIds.map { store.getNode(it) }.sortedWith(byGender(store.root.gender))
        family.parents = mutableListOf(newUnit(family.id, parents))
        family.children = childUnitsOfParents(store, family.id, parents).toMutableList()
        setDefaultUnitShift(family)
        return family
    }

    private fun createFamilyWithoutParents(store: Store): List<Family> {
        val family = newFamily(store.getNextId(), FamilyType.Root, main = true)
        family.children = createChildUnits(store, family.id, store.root).toMutableList()
        setDefaultUnitShift(family)
        return listOf(family)
    }

    private fun createDiffTypeFamilies(store: Store): List<Family> {
        val blood = createFamilyByParents(
            store,
            store.root.parents.filter { it.type == RelType.Blood }.map { it.id },
            FamilyType.Root,
            isMain = true,
        )
        val adopted = createFamilyByParents(
            store,
            store.root.parents.filter { it.type == RelType.Adopted }.map { it.id },
        )
        correctOverlaps(blood, adopted)
        return listOf(blood, adopted)
    }

    private fun createBloodFamilies(store: Store): List<Family> {
        val mainFamily = createFamilyByParents(
            store,
            store.root.parents.map { it.id },
            FamilyType.Root,
            isMain = true,
        )
        val parents = unitsToNodes(mainFamily.parents)
        if (parents.size != NODES_IN_COUPLE) return listOf(mainFamily)
        val (left, _, right) = getSpouseNodes(store, parents)
        return left.map { createFamilyByParents(store, listOf(it.id)) } +
            mainFamily +
            right.map { createFamilyByParents(store, listOf(it.id)) }
    }

    private fun correctOverlaps(bloodFamily: Family, adoptedFamily: Family) {
        val sharedIds = unitsToNodes(bloodFamily.children)
            .filter { node -> unitsToNodes(adoptedFamily.children).any { it.id == node.id } }
            .map { it.id }
        val cachePos = bloodFamily.children.map { it.pos }
        bloodFamily.children = bloodFamily.children.sortedWith { a, b ->
            val foundA = a.nodes.any { it.id in sharedIds }
            val foundB = b.nodes.any { it.id in sharedIds }
            when {
                foundA && !foundB -> 1
                !foundA && foundB -> -1
                else -> 0
            }
        }.toMutableList()
        bloodFamily.children.forEachIndexed { idx, unit -> unit.pos = cachePos[idx] }
        adoptedFamily.children = adoptedFamily.children
            .filter { unit -> unit.nodes.any { it.id !in sharedIds } }
            .toMutableList()
        setDefaultUnitShift(adoptedFamily)
    }

    private fun inMiddleDirection(store: Store): Store {
        val families = when {
            store.root.parents.isEmpty() -> createFamilyWithoutParents(store)
            hasDiffParents(store.root) -> createDiffTypeFamilies(store)
            else -> createBloodFamilies(store)
        }
        for (i in 1 until families.size) {
            families[i].x = rightOf(families[i - 1])
        }
        families.forEach { store.families[it.id] = it }
        return store
    }

    private fun getParentUnits(store: Store, unit: Unit): List<Unit> {
        val units = mutableListOf<Unit>()
        unit.nodes.forEach { child ->
            val parents = store.getNodes(child.parents.map { it.id })
                .sortedWith(byGender(store.root.gender))
            if (parents.isNotEmpty()) units += newUnit(unit.fid, parents)
        }
        return units
    }

    private fun createParentFamily(store: Store, childIds: List<String>): Family {
        val family = newFamily(store.getNextId(), FamilyType.Parent)
        val childUnit = newUnit(family.id, store.getNodes(childIds), isChild = true)
        family.children = mutableListOf(childUnit)
        family.parents = getParentUnits(store, childUnit).toMutableList()
        arrangeInOrder(family.children)
        arrangeInOrder(family.parents)
        return family
    }

    private fun updateParentFamily(store: Store, family: Family, childUnit: Unit) {
        val childFamily = store.getFamily(childUnit.fid)
        family.cid = childFamily.id
        family.y = unitY(childFamily, childUnit) - SIZE
        family.x = getUnitX(childFamily, childUnit)
    }

    private fun arrangeParentsIn(family: Family) {
        if (family.parents.isEmpty() || family.children.size <= 1) return
        family.parents.forEach { unit ->
            val ids = unit.nodes.first().children.map { it.id }.toSet()
            val shifts = family.children.mapNotNull { child ->
                val index = child.nodes.indexOfFirst { it.id in ids }
                if (index == -1) null else child.pos + index * SIZE
            }
            val middle = if (shifts.isEmpty()) 0.0 else (shifts.first() + shifts.last()) / 2.0
            unit.pos = floor(middle - (unitNodesCount(family.parents) - 1))
        }
    }

    private fun arrangeParentFamilies(store: Store, start: Family) {
        var family = start
        while (true) {
            val cid = family.cid ?: break
            val nextFamily = store.getFamily(cid)
            val unit = family.children.first()
            if (nextFamily.cid == null) {
                unit.pos = (widthOf(family) - nodeCount(unit) * SIZE) / 2.0
            } else {
                if (family.parents.size == 2 && unitNodesCount(family.parents) > 2) {
                    unit.pos = floor(family.parents[1].pos / 2.0)
                }
                val index = nextFamily.parents.indexOfFirst(sameAs(unit))
                if (index < 0) break
                if (index == 0 && nextFamily.parents[index].pos == 0.0) {
                    nextFamily.x = getUnitX(family, unit)
                } else {
                    nextFamily.parents[index].pos = getUnitX(family, unit) - nextFamily.x
                }
                val nextIdx = index + 1
                if (nextIdx < nextFamily.parents.size) {
                    correctUnitsShift(
                        nextFamily.parents.drop(nextIdx),
                        rightOf(family) - getUnitX(nextFamily, nextFamily.parents[nextIdx]),
                    )
                }
            }
            family = nextFamily
        }
    }

    private fun parentUnitsWithParents(family: Family): List<Unit> =
        family.parents.filter { unit -> unit.nodes.any { it.parents.isNotEmpty() } }

    private fun inParentDirection(store: Store): Store {
        var stack = parentUnitsWithParents(store.rootFamily).toMutableList()
        while (stack.isNotEmpty()) {
            val childUnit = stack.removeAt(stack.lastIndex)
            val family = createParentFamily(store, nodeIds(childUnit))
            updateParentFamily(store, family, childUnit)
            arrangeParentFamilies(store, family)
            store.families[family.id] = family
            stack += parentUnitsWithParents(family).reversed()
        }
        return store
    }

    private fun updateChildFamily(store: Store, family: Family, parentUnit: Unit) {
        val parentFamily = store.getFamily(parentUnit.fid)
        family.pid = parentFamily.id
        family.y = parentFamily.y + heightOf(parentFamily) - SIZE
        family.x = getUnitX(parentFamily, parentUnit)
    }

    private fun arrangeChildFamilies(store: Store, start: Family) {
        var family = start
        var right = 0.0
        while (true) {
            val pid = family.pid ?: break
            right = maxOf(right, rightOf(family))
            val nextFamily = store.getFamily(pid)
            val unit = family.parents.first()
            val index = nextFamily.children.indexOfFirst(sameAs(unit))
            if (index >= 0) {
                if (index == 0) {
                    nextFamily.x = getUnitX(family, unit) - nextFamily.children[index].pos
                } else {
                    nextFamily.children[index].pos = getUnitX(family, unit) - nextFamily.x
                }
                val nextIdx = index + 1
                if (nextIdx < nextFamily.children.size) {
                    correctUnitsShift(
                        nextFamily.children.drop(nextIdx),
                        right - getUnitX(nextFamily, nextFamily.children[nextIdx]),
                    )
                }
            }
            arrangeParentsIn(nextFamily)
            if (nextFamily.pid == null) {
                val families = store.rootFamilies
                val startIndex = families.indexOfFirst { it.id == nextFamily.id } + 1
                if (startIndex in families.indices) {
                    val shift = rightOf(nextFamily) - families[startIndex].x
                    for (i in startIndex until families.size) families[i].x += shift
                }
            }
            family = nextFamily
        }
    }

    private fun unitsWithChildren(family: Family): List<Unit> =
        family.children.filter(::hasChildren).reversed()

    private fun inChildDirection(store: Store): Store {
        store.rootFamilies.forEach { rootFamily ->
            var stack = unitsWithChildren(rootFamily).toMutableList()
            while (stack.isNotEmpty()) {
                val parentUnit = stack.removeAt(stack.lastIndex)
                val family = createFamilyByParents(store, nodeIds(parentUnit), FamilyType.Child)
                updateChildFamily(store, family, parentUnit)
                arrangeChildFamilies(store, family)
                store.families[family.id] = family
                stack += unitsWithChildren(family)
            }
        }
        return store
    }

    /**
     * relatives-tree parent families put each person's parents in a unit on the
     * parent row. In-laws of a couple are the same: extra parent units on that
     * family, then [setDefaultUnitShift]. Siblings of the couple go to a
     * neighbouring family so the two parent pairs sit over that couple only.
     */
    private fun inLawParentDirection(store: Store): Store {
        val placed = store.familiesArray
            .filter { it.type == FamilyType.Parent || it.type == FamilyType.Root }
            .flatMap { family -> family.parents.flatMap { unit -> unit.nodes.map { it.id } } }
            .toMutableSet()
        val snapshot = store.familiesArray.filter { it.type == FamilyType.Root || it.type == FamilyType.Child }
        snapshot.forEach { family ->
            val familyParentIds = family.parents.flatMap { nodeIds(it) }.toSet()
            fun inLawParents(node: Node): List<Node> {
                val parents = node.parents.map { store.getNode(it.id) }
                    .filter { it.id !in familyParentIds && it.id !in placed }
                return if (parents.isEmpty()) emptyList() else parents.sortedWith(byGender(store.root.gender))
            }
            val (affected, others) = family.children.partition { unit ->
                unit.nodes.any { inLawParents(it).isNotEmpty() }
            }
            if (affected.isEmpty()) return@forEach
            val extra = if (others.isEmpty()) {
                null
            } else {
                val split = newFamily(store.getNextId(), family.type)
                split.y = family.y
                split.parents = family.parents
                    .map { unit -> newUnit(split.id, unit.nodes) }
                    .toMutableList()
                split.children = others
                    .map { unit -> newUnit(split.id, unit.nodes, isChild = true) }
                    .toMutableList()
                setDefaultUnitShift(split)
                remapChildFamilies(store, family, others, split)
                family.children = affected
                    .map { unit -> newUnit(family.id, unit.nodes, isChild = true) }
                    .toMutableList()
                store.families[split.id] = split
                split
            }
            val newUnits = mutableListOf<Unit>()
            family.children.forEach { unit ->
                unit.nodes.forEach { node ->
                    val parents = inLawParents(node)
                    if (parents.isEmpty()) return@forEach
                    newUnits += newUnit(family.id, parents)
                    placed += parents.map { it.id }
                }
            }
            if (newUnits.isEmpty()) return@forEach
            family.parents.addAll(newUnits)
            setDefaultUnitShift(family)
            extra?.let { split ->
                val dx = rightOf(family) - split.x
                split.x += dx
                store.familiesArray.filter { it.pid == split.id }.forEach { child -> child.x += dx }
            }
            val walk = newUnits.toMutableList()
            while (walk.isNotEmpty()) {
                val parentUnit = walk.removeAt(walk.lastIndex)
                val next = createParentFamily(store, nodeIds(parentUnit))
                if (next.parents.isEmpty()) continue
                if (next.parents.flatMap { nodeIds(it) }.all { it in placed }) continue
                updateParentFamily(
                    store,
                    next,
                    parentUnit.copy(fid = family.id, child = false),
                )
                arrangeParentFamilies(store, next)
                store.families[next.id] = next
                placed += next.parents.flatMap { nodeIds(it) }
                walk += parentUnitsWithParents(next).reversed()
            }
        }
        return store
    }

    private fun remapChildFamilies(store: Store, from: Family, moved: List<Unit>, extra: Family) {
        val movedKeys = moved.map { nodeIds(it).joinToString("") }.toSet()
        store.familiesArray.filter { it.pid == from.id }.forEach { child ->
            val key = nodeIds(child.parents.first()).joinToString("")
            if (key in movedKeys) {
                val match = extra.children.first { nodeIds(it).joinToString("") == key }
                child.pid = extra.id
                child.x = getUnitX(extra, match)
                child.y = extra.y + heightOf(extra) - SIZE
            }
        }
    }

    private fun correctPositions(store: Store): Store {
        val families = store.familiesArray
        val root = store.rootFamily
        val parents = families.find { it.cid == root.id }
        if (parents != null && parents.children.isNotEmpty() && root.parents.isNotEmpty()) {
            val shift = getUnitX(parents, parents.children.first()) - getUnitX(root, root.parents.first())
            families.filter { it.type == FamilyType.Child || it.type == FamilyType.Root }
                .forEach { it.x += shift }
        }
        val minY = families.minOfOrNull { it.y } ?: 0.0
        if (minY != 0.0) families.forEach { it.y += -minY }
        val minX = families.minOfOrNull { it.x } ?: 0.0
        if (minX != 0.0) families.forEach { it.x += -minX }
        return store
    }

    private fun extendNode(family: Family, unit: Unit): List<ExtNode> {
        val top = unitY(family, unit)
        return unit.nodes.mapIndexed { idx, node ->
            ExtNode(node.id, getUnitX(family, unit) + idx * SIZE, top)
        }
    }

    private fun getExtendedNodes(families: List<Family>): List<ExtNode> = families.flatMap { family ->
        val parents = family.parents.flatMap { extendNode(family, it) }
        val children = if (family.type == FamilyType.Parent) {
            emptyList()
        } else {
            family.children.flatMap { extendNode(family, it) }
        }
        parents + children
    }
}
