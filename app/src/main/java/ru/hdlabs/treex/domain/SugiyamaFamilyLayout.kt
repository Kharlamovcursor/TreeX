package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.max

/**
 * Generational family layout from famtree (MIT, felipeplets/famtree, GenerationalLayout).
 *
 * A parent is centered on their own children. The blood relative stands on the
 * left of a couple and the married-in spouse on the right, so a new parent
 * (for example the wife's mother) sits on that person's side instead of
 * crossing the rest of the tree. Unmarried siblings come first; a sibling who
 * brings a spouse is placed at the edge of the group.
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
        val byId = people.associateBy { it.id }
        val generation = TreeLayout.generationMap(people, unions, openLinks)
        val extraParents = HashMap<String, MutableList<String>>()
        openLinks.filter { it.slot == "bridge" && it.anchorIsParent && it.anchorId in ids }.forEach { link ->
            link.childIds.filter { it in ids }.forEach { childId ->
                extraParents.getOrPut(childId) { mutableListOf() }.add(link.anchorId)
            }
        }
        fun parentIdsOf(person: PersonEntity): List<String> =
            (listOfNotNull(person.fatherId, person.motherId) + extraParents[person.id].orEmpty())
                .filter { it in ids && it != person.id }
                .distinct()

        val sameGenUnions = unions.filter {
            it.partnerAId in ids && it.partnerBId in ids && it.partnerAId != it.partnerBId &&
                generation[it.partnerAId] == generation[it.partnerBId]
        }
        fun unionsOf(id: String): List<UnionEntity> =
            sameGenUnions.filter { it.partnerAId == id || it.partnerBId == id }

        fun otherOf(union: UnionEntity, id: String): String =
            if (union.partnerAId == id) union.partnerBId else union.partnerAId

        fun belongsToUnion(person: PersonEntity, union: UnionEntity): Boolean {
            val parents = parentIdsOf(person)
            if (parents.isEmpty()) return false
            val partners = setOf(union.partnerAId, union.partnerBId)
            return parents.all { it in partners }
        }

        fun childrenOfUnion(union: UnionEntity): List<PersonEntity> =
            people.filter { belongsToUnion(it, union) }

        fun singleChildrenOf(id: String): List<PersonEntity> =
            people.filter { child ->
                id in parentIdsOf(child) && unionsOf(id).none { belongsToUnion(child, it) }
            }

        fun orderSiblings(children: List<PersonEntity>): List<PersonEntity> {
            val sorted = children.sortedWith(compareBy({ it.birthDate }, { it.id }))
            val singles = sorted.filter { unionsOf(it.id).isEmpty() }
            val married = sorted.filter { unionsOf(it.id).isNotEmpty() }
            return singles + married
        }

        val walker = Walker(
            cardW = cardW,
            gapX = gapX,
            siblingGap = 44f,
            unionsOf = ::unionsOf,
            otherOf = ::otherOf,
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
        val spousePairs = sameGenUnions.map { setOf(it.partnerAId, it.partnerBId) }.toSet()
        people.groupBy { generation[it.id] ?: 0 }.values.forEach { band ->
            val ordered = band.sortedBy { leftOf.getValue(it.id) }
            for (index in 1 until ordered.size) {
                val previous = ordered[index - 1]
                val current = ordered[index]
                val gap = if (setOf(previous.id, current.id) in spousePairs) gapX else 44f
                val minLeft = leftOf.getValue(previous.id) + cardW + gap
                if (leftOf.getValue(current.id) < minLeft) leftOf[current.id] = minLeft
            }
        }

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

    private class Walker(
        private val cardW: Float,
        private val gapX: Float,
        private val siblingGap: Float,
        private val unionsOf: (String) -> List<UnionEntity>,
        private val otherOf: (UnionEntity, String) -> String,
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
                placeUnion(union, id)
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

        private fun placeUnion(union: UnionEntity, anchor: String): Float {
            placedUnion += union.id
            val other = otherOf(union, anchor)
            val kids = orderSiblings(childrenOfUnion(union)).map { it.id }
            val center = if (kids.isNotEmpty()) {
                centerOver(kids)
            } else {
                val reserved = cursor + siblingGap / 2f
                cursor += cardW * 2 + gapX + siblingGap
                reserved + (cardW + gapX) / 2f
            }
            leftOf[anchor] = center - gapX / 2f - cardW
            leftOf[other] = center + gapX / 2f
            cursor = max(cursor, leftOf.getValue(other) + cardW + siblingGap)
            return center
        }

        private fun centerOver(ids: List<String>): Float {
            val xs = ids.map { placeSubtree(it) }
            return (xs.first() + xs.last()) / 2f
        }
    }
}
