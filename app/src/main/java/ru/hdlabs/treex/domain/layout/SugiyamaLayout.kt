package ru.hdlabs.treex.domain.layout

import ru.hdlabs.treex.domain.graph.GenealogyGraph

/**
 * Orchestrates generation assignment, block placement, nodes, and edges.
 */
object SugiyamaLayout {

    fun layout(graph: GenealogyGraph): TreeLayout {
        val levels = GenerationAssigner.assign(graph)
        val drafts = FamilyBlockBuilder.build(graph, levels)
        val minLevel = levels.values.minOrNull() ?: 0

        val personPos = mutableMapOf<String, Pair<Float, Float>>()
        val blocksByLevel = drafts.groupBy { it.generationLevel }
            .mapValues { (_, list) -> list.sortedBy { anchorId(it) } }

        val allLevels = blocksByLevel.keys.sorted()
        val blockGeometries = linkedMapOf<String, BlockGeom>()

        allLevels.forEach { level ->
            val levelTop = levelTopY(minLevel, level)
            placeLevel(graph, blocksByLevel[level].orEmpty(), levelTop, personPos, blockGeometries)
            barycenterDown(graph, level, allLevels, blocksByLevel, minLevel, personPos, blockGeometries)
        }
        allLevels.asReversed().forEach { level ->
            barycenterUp(graph, level, blocksByLevel, minLevel, personPos, blockGeometries)
        }
        allLevels.forEach { level ->
            sweepLevel(blocksByLevel[level].orEmpty(), level, minLevel, personPos, blockGeometries)
        }
        assignUnplacedPersons(graph, levels, minLevel, personPos, blockGeometries)
        resolveCollisionsOnLevels(levels, minLevel, personPos)
        recomputeBlockCenters(drafts, personPos, blockGeometries)

        val familyBlocks = drafts.sortedBy { it.familyId }.map { draft ->
            val geom = blockGeometries.getValue(draft.familyId)
            FamilyBlock(
                familyId = draft.familyId,
                partnerAId = draft.partnerAId,
                partnerBId = draft.partnerBId,
                centerX = geom.centerX,
                topY = geom.topY,
                bottomY = geom.bottomY,
                generationLevel = draft.generationLevel,
            )
        }

        val nodes = graph.persons.keys.sorted().mapNotNull { id ->
            val pos = personPos[id] ?: return@mapNotNull null
            val level = levels[id] ?: 0
            LayoutNode(
                personId = id,
                x = pos.first,
                y = pos.second,
                width = LayoutConfig.nodeWidth,
                height = LayoutConfig.nodeHeight,
                generationLevel = level,
            )
        }

        val edges = EdgeBuilder.build(graph, nodes, familyBlocks)
        val bounds = computeBounds(nodes)
        return TreeLayout(
            nodes = nodes,
            familyBlocks = familyBlocks,
            edges = edges,
            bounds = bounds,
        )
    }

    private data class BlockGeom(
        var leftX: Float,
        var centerX: Float,
        val topY: Float,
        val bottomY: Float,
        val draft: FamilyBlockDraft,
    )

    private fun anchorId(draft: FamilyBlockDraft): String =
        draft.partnerAId ?: draft.partnerBId ?: draft.familyId

    private fun levelTopY(minLevel: Int, level: Int): Float =
        (level - minLevel) * (LayoutConfig.nodeHeight + LayoutConfig.levelGap)

    private fun blockWidth(draft: FamilyBlockDraft): Float {
        val hasA = draft.partnerAId != null
        val hasB = draft.partnerBId != null
        return when {
            hasA && hasB -> LayoutConfig.nodeWidth + LayoutConfig.spouseGap + LayoutConfig.nodeWidth
            hasA && !hasB -> LayoutConfig.nodeWidth + LayoutConfig.spouseGap + LayoutConfig.slotWidth
            !hasA && hasB -> LayoutConfig.slotWidth + LayoutConfig.spouseGap + LayoutConfig.nodeWidth
            else -> LayoutConfig.nodeWidth
        }
    }

    private fun placePartnersInBlock(
        leftX: Float,
        draft: FamilyBlockDraft,
        topY: Float,
        personPos: MutableMap<String, Pair<Float, Float>>,
    ) {
        var x = leftX
        if (draft.partnerAId != null) {
            val existing = personPos[draft.partnerAId]
            val px = existing?.first ?: x
            personPos[draft.partnerAId] = px to topY
            x = px + LayoutConfig.nodeWidth + LayoutConfig.spouseGap
        } else if (draft.partnerBId != null) {
            x += LayoutConfig.slotWidth + LayoutConfig.spouseGap
        }
        if (draft.partnerBId != null) {
            val existing = personPos[draft.partnerBId]
            val px = existing?.first ?: x
            personPos[draft.partnerBId] = px to topY
        }
    }

    private fun placeLevel(
        graph: GenealogyGraph,
        drafts: List<FamilyBlockDraft>,
        levelTop: Float,
        personPos: MutableMap<String, Pair<Float, Float>>,
        blockGeometries: MutableMap<String, BlockGeom>,
    ) {
        var cursor = 0f
        drafts.forEach { draft ->
            val width = blockWidth(draft)
            placePartnersInBlock(cursor, draft, levelTop, personPos)
            val left = cursor
            val center = left + width / 2f
            val bottom = levelTop + LayoutConfig.nodeHeight
            blockGeometries[draft.familyId] = BlockGeom(left, center, levelTop, bottom, draft)
            cursor += width + LayoutConfig.blockGap
        }
    }

    private fun barycenterDown(
        graph: GenealogyGraph,
        level: Int,
        allLevels: List<Int>,
        blocksByLevel: Map<Int, List<FamilyBlockDraft>>,
        minLevel: Int,
        personPos: MutableMap<String, Pair<Float, Float>>,
        blockGeometries: MutableMap<String, BlockGeom>,
    ) {
        if (level <= allLevels.minOrNull() ?: level) return
        reorderByBarycenter(graph, blocksByLevel[level].orEmpty(), level, minLevel, personPos, blockGeometries) { fam ->
            fam.childrenIds.mapNotNull { personPos[it]?.first }.average().toFloat()
                .takeIf { fam.childrenIds.isNotEmpty() && personPos.keys.containsAll(fam.childrenIds) }
        }
    }

    private fun barycenterUp(
        graph: GenealogyGraph,
        level: Int,
        blocksByLevel: Map<Int, List<FamilyBlockDraft>>,
        minLevel: Int,
        personPos: MutableMap<String, Pair<Float, Float>>,
        blockGeometries: MutableMap<String, BlockGeom>,
    ) {
        reorderByBarycenter(graph, blocksByLevel[level].orEmpty(), level, minLevel, personPos, blockGeometries) { fam ->
            listOfNotNull(fam.partnerAId, fam.partnerBId)
                .mapNotNull { personPos[it]?.first }
                .average()
                .toFloat()
                .takeIf { listOfNotNull(fam.partnerAId, fam.partnerBId).isNotEmpty() }
        }
    }

    private fun reorderByBarycenter(
        graph: GenealogyGraph,
        drafts: List<FamilyBlockDraft>,
        level: Int,
        minLevel: Int,
        personPos: MutableMap<String, Pair<Float, Float>>,
        blockGeometries: MutableMap<String, BlockGeom>,
        bary: (ru.hdlabs.treex.domain.graph.Family) -> Float?,
    ) {
        if (drafts.size <= 1) return
        val sorted = drafts.sortedWith(compareBy<FamilyBlockDraft> { draft ->
            val fam = graph.family(draft.familyId)
            fam?.let { bary(it) } ?: blockGeometries[draft.familyId]?.centerX ?: 0f
        }.thenBy { it.familyId })
        val levelTop = levelTopY(minLevel, level)
        var cursor = 0f
        sorted.forEach { draft ->
            val width = blockWidth(draft)
            val geom = blockGeometries.getValue(draft.familyId)
            geom.leftX = cursor
            geom.centerX = cursor + width / 2f
            placePartnersInBlock(cursor, draft, levelTop, personPos)
            cursor += width + LayoutConfig.blockGap
        }
    }

    private fun sweepLevel(
        drafts: List<FamilyBlockDraft>,
        level: Int,
        minLevel: Int,
        personPos: MutableMap<String, Pair<Float, Float>>,
        blockGeometries: MutableMap<String, BlockGeom>,
    ) {
        if (drafts.isEmpty()) return
        val levelTop = levelTopY(minLevel, level)
        var cursor = 0f
        drafts.sortedBy { blockGeometries[it.familyId]?.leftX ?: 0f }.forEach { draft ->
            val width = blockWidth(draft)
            val geom = blockGeometries.getValue(draft.familyId)
            if (geom.leftX < cursor) {
                geom.leftX = cursor
                geom.centerX = cursor + width / 2f
                placePartnersInBlock(cursor, draft, levelTop, personPos)
            }
            cursor = geom.leftX + width + LayoutConfig.blockGap
        }
    }

    private fun assignUnplacedPersons(
        graph: GenealogyGraph,
        levels: Map<String, Int>,
        minLevel: Int,
        personPos: MutableMap<String, Pair<Float, Float>>,
        blockGeometries: Map<String, BlockGeom>,
    ) {
        val byLevel = levels.keys.sorted().groupBy { levels[it] ?: 0 }
        byLevel.forEach { (level, ids) ->
            val topY = levelTopY(minLevel, level)
            var cursor = personPos.values.filter { it.second == topY }.maxOfOrNull { it.first + LayoutConfig.nodeWidth }
                ?: 0f
            cursor += LayoutConfig.blockGap
            ids.filter { it !in personPos }.forEach { personId ->
                val fam = graph.familyWhereChild(personId)
                val x = if (fam != null) {
                    val siblings = fam.childrenIds
                        .filter { (levels[it] ?: 0) == level }
                        .sorted()
                    val index = siblings.indexOf(personId).coerceAtLeast(0)
                    val count = siblings.size.coerceAtLeast(1)
                    val totalW = count * LayoutConfig.nodeWidth +
                        (count - 1).coerceAtLeast(0) * LayoutConfig.spouseGap
                    val center = blockGeometries[fam.id]?.centerX ?: (cursor + LayoutConfig.nodeWidth / 2f)
                    center - totalW / 2f + index * (LayoutConfig.nodeWidth + LayoutConfig.spouseGap)
                } else {
                    cursor
                }
                personPos[personId] = x to topY
                cursor = maxOf(cursor, x + LayoutConfig.nodeWidth + LayoutConfig.blockGap)
            }
        }
    }

    private fun recomputeBlockCenters(
        drafts: List<FamilyBlockDraft>,
        personPos: Map<String, Pair<Float, Float>>,
        blockGeometries: MutableMap<String, BlockGeom>,
    ) {
        drafts.forEach { draft ->
            val geom = blockGeometries[draft.familyId] ?: return@forEach
            val centers = listOfNotNull(draft.partnerAId, draft.partnerBId).mapNotNull { id ->
                personPos[id]?.let { it.first + LayoutConfig.nodeWidth / 2f }
            }
            if (centers.isNotEmpty()) {
                geom.centerX = centers.average().toFloat()
            }
        }
    }

    private fun resolveCollisionsOnLevels(
        levels: Map<String, Int>,
        minLevel: Int,
        personPos: MutableMap<String, Pair<Float, Float>>,
    ) {
        val byLevel = levels.keys.groupBy { levels[it] ?: 0 }
        byLevel.keys.sorted().forEach { level ->
            val topY = levelTopY(minLevel, level)
            val ids = byLevel[level].orEmpty().sorted()
            var cursor = 0f
            val minGap = LayoutConfig.spouseGap
            ids.forEach { id ->
                val pos = personPos[id] ?: return@forEach
                if (pos.second != topY) return@forEach
                val x = if (cursor <= 0f) pos.first else maxOf(pos.first, cursor)
                personPos[id] = x to topY
                cursor = x + LayoutConfig.nodeWidth + minGap
            }
        }
    }

    private fun computeBounds(nodes: List<LayoutNode>): Bounds {
        if (nodes.isEmpty()) return Bounds(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        nodes.forEach { n ->
            minX = minOf(minX, n.x)
            minY = minOf(minY, n.y)
            maxX = maxOf(maxX, n.x + n.width)
            maxY = maxOf(maxY, n.y + n.height)
        }
        return Bounds(minX, minY, maxX, maxY)
    }
}
