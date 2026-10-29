package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.max

/**
 * Generational layout (famtree-style): parents centered over their children.
 * Coordinates come only from kinship — never the other way around.
 */
internal object SugiyamaFamilyLayout {
    data class Placed(val id: String, val left: Float, val top: Float, val height: Float)

    fun place(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink>,
        homePersonId: String?,
        cardW: Float,
        gapX: Float,
        gapY: Float,
        pad: Float,
        heights: Map<String, Float>,
    ): List<Placed> {
        if (people.isEmpty()) return emptyList()
        val unions = UnionEr.reconcileChildMembership(people, unions)
        val ids = people.map { it.id }.toSet()
        val byId = people.associateBy { it.id }
        val generation = TreeLayout.generationMap(people, unions, openLinks)
        val extraParents = HashMap<String, MutableList<String>>()
        openLinks.filter { it.slot == "bridge" && it.anchorIsParent && it.anchorId in ids }.forEach { link ->
            link.childIds.filter { it in ids }.forEach { childId ->
                extraParents.getOrPut(childId) { mutableListOf() }.add(link.anchorId)
            }
        }
        fun parentIdsOf(person: PersonEntity): List<String> =
            FamilyKinship.parentIdsOf(person, ids, unions, extraParents)

        val sameGenUnions = FamilyKinship.sameGenerationUnions(unions, ids, generation)
        fun unionsOf(id: String): List<UnionEntity> =
            sameGenUnions.filter { it.partnerAId == id || it.partnerBId == id }

        fun belongsToUnion(person: PersonEntity, union: UnionEntity): Boolean =
            FamilyKinship.belongsToUnion(person, union, ids, unions, extraParents)

        fun childrenOfUnion(union: UnionEntity): List<PersonEntity> =
            FamilyKinship.childrenOfUnion(people, union, ids, unions, extraParents)

        fun singleChildrenOf(id: String): List<PersonEntity> =
            people.filter { child ->
                id in parentIdsOf(child) && unionsOf(id).none { belongsToUnion(child, it) }
            }

        fun orderSiblings(children: List<PersonEntity>): List<PersonEntity> =
            FamilyKinship.orderSiblings(children, ::unionsOf)

        val walker = Walker(
            cardW = cardW,
            gapX = gapX,
            siblingGap = 44f,
            byId = byId,
            ids = ids,
            extraParents = extraParents,
            unions = unions,
            homePersonId = homePersonId,
            unionsOf = ::unionsOf,
            childrenOfUnion = ::childrenOfUnion,
            singleChildrenOf = ::singleChildrenOf,
            orderSiblings = ::orderSiblings,
        )
        val roots = people
            .filter { parentIdsOf(it).isEmpty() }
            .sortedWith(compareBy({ unionsOf(it.id).isEmpty() }, { it.birthDate }, { it.id }))
        roots.forEach { walker.placeSubtree(it.id) }
        people.sortedBy { it.id }.forEach { if (it.id !in walker.leftOf) walker.placeSubtree(it.id) }
        val leftOf = walker.leftOf
        resolveGenerationOverlaps(leftOf, people, generation, sameGenUnions, cardW, gapX)

        val minLeft = leftOf.values.minOrNull() ?: 0f
        val shift = if (minLeft < pad) pad - minLeft else 0f
        if (shift != 0f) leftOf.keys.toList().forEach { leftOf[it] = leftOf.getValue(it) + shift }

        val rowHeight = people.groupBy { generation[it.id] ?: 0 }.mapValues { (_, band) ->
            band.maxOf { heights[it.id] ?: TreeLayout.CARD_H }
        }
        val topOf = HashMap<Int, Float>()
        var y = pad
        rowHeight.keys.sorted().forEach { gen ->
            topOf[gen] = y
            y += (rowHeight[gen] ?: TreeLayout.CARD_H) + gapY
        }
        return people.map { person ->
            val gen = generation[person.id] ?: 0
            Placed(
                id = person.id,
                left = leftOf.getValue(person.id),
                top = topOf.getValue(gen),
                height = rowHeight[gen] ?: TreeLayout.CARD_H,
            )
        }
    }

    private fun resolveGenerationOverlaps(
        leftOf: MutableMap<String, Float>,
        people: List<PersonEntity>,
        generation: Map<String, Int>,
        sameGenUnions: List<UnionEntity>,
        cardW: Float,
        gapX: Float,
    ) {
        val spousePairs = sameGenUnions.mapNotNull { u ->
            val a = u.partnerAId ?: return@mapNotNull null
            val b = u.partnerBId ?: return@mapNotNull null
            setOf(a, b)
        }.toSet()
        fun shiftBlock(id: String, delta: Float) {
            if (delta == 0f) return
            val partners = spousePairs.firstOrNull { id in it }?.toList() ?: listOf(id)
            partners.forEach { partnerId ->
                if (partnerId in leftOf) leftOf[partnerId] = leftOf.getValue(partnerId) + delta
            }
        }

        people.groupBy { generation[it.id] ?: 0 }.values.forEach { band ->
            val ordered = band.sortedBy { leftOf.getValue(it.id) }
            for (index in 1 until ordered.size) {
                val previous = ordered[index - 1]
                val current = ordered[index]
                if (setOf(previous.id, current.id) in spousePairs) continue
                val prevRight = maxOf(
                    leftOf.getValue(previous.id) + cardW,
                    spousePairs.firstOrNull { previous.id in it }?.maxOf { leftOf.getValue(it) + cardW } ?: 0f,
                )
                val minLeft = prevRight + 44f
                val currentLeft = minOf(
                    leftOf.getValue(current.id),
                    spousePairs.firstOrNull { current.id in it }?.minOf { leftOf.getValue(it) } ?: leftOf.getValue(current.id),
                )
                if (currentLeft + 0.1f < minLeft) shiftBlock(current.id, minLeft - currentLeft)
            }
        }
    }

    private class Walker(
        private val cardW: Float,
        private val gapX: Float,
        private val siblingGap: Float,
        private val byId: Map<String, PersonEntity>,
        private val ids: Set<String>,
        private val extraParents: Map<String, List<String>>,
        private val unions: List<UnionEntity>,
        private val homePersonId: String?,
        private val unionsOf: (String) -> List<UnionEntity>,
        private val childrenOfUnion: (UnionEntity) -> List<PersonEntity>,
        private val singleChildrenOf: (String) -> List<PersonEntity>,
        private val orderSiblings: (List<PersonEntity>) -> List<PersonEntity>,
    ) {
        val leftOf = HashMap<String, Float>()
        private val placedUnion = HashSet<String>()
        private val placing = HashSet<String>()
        private var cursor = 0f

        fun placeSubtree(id: String): Float {
            leftOf[id]?.let { return it + cardW / 2f }
            if (!placing.add(id)) return cursor + cardW / 2f
            val union = unionsOf(id).firstOrNull { it.id !in placedUnion }
            if (union != null) {
                placeUnion(union)
                placing.remove(id)
                return leftOf.getValue(id) + cardW / 2f
            }
            val kids = orderSiblings(singleChildrenOf(id)).map { it.id }
            if (kids.isNotEmpty()) {
                val center = centerOver(kids)
                leftOf[id] = center - cardW / 2f
                cursor = max(cursor, leftOf.getValue(id) + cardW + siblingGap)
                placing.remove(id)
                return center
            }
            val left = cursor
            cursor += cardW + siblingGap
            leftOf[id] = left
            placing.remove(id)
            return left + cardW / 2f
        }

        private fun placeUnion(union: UnionEntity): Float {
            placedUnion += union.id
            val kids = orderSiblings(childrenOfUnion(union)).map { it.id }
            val center = if (kids.isNotEmpty()) {
                centerOver(kids)
            } else {
                val reserved = cursor + siblingGap / 2f
                cursor += cardW * 2 + gapX + siblingGap
                reserved + (cardW + gapX) / 2f
            }
            val leftId = FamilyKinship.leftPartnerId(
                union = union,
                byId = byId,
                ids = ids,
                unions = unions,
                extraParents = extraParents,
                parentColumnCenter = parentColumnCenter@{ partnerId ->
                    val parents = FamilyKinship.parentIdsOf(byId.getValue(partnerId), ids, unions, extraParents)
                    if (parents.isEmpty()) return@parentColumnCenter null
                    parents.mapNotNull { parentId -> leftOf[parentId]?.plus(cardW / 2f) }.takeIf { it.size == parents.size }
                        ?.average()?.toFloat()
                },
                homePersonId = homePersonId,
            )
            val rightId = FamilyKinship.otherPartner(union, leftId)
            if (rightId == null) {
                leftOf[leftId] = center - cardW / 2f
                cursor = max(cursor, leftOf.getValue(leftId) + cardW + siblingGap)
            } else {
                leftOf[leftId] = center - gapX / 2f - cardW
                leftOf[rightId] = center + gapX / 2f
                cursor = max(cursor, leftOf.getValue(rightId) + cardW + siblingGap)
            }
            return center
        }

        private fun centerOver(childIds: List<String>): Float {
            val xs = childIds.map { placeSubtree(it) }
            return (xs.first() + xs.last()) / 2f
        }
    }
}
