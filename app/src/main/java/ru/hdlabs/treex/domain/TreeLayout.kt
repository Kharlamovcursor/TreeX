package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import kotlin.math.max

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

object TreeLayout {
    const val CARD_W = 168f
    const val CARD_H = 108f
    private const val MAX_CARD_H = 124f
    private const val SPOUSE_GAP = 28f
    private const val HOUSE_GAP = 44f
    private const val ROW_GAP = 76f
    private const val PAD = 16f

    fun cardHeight(person: PersonEntity): Float {
        val lines = wrapLines(person.fullName(), 14)
        val years = if (person.birthDate.isBlank() && person.deathDate.isBlank()) 0f else 20f
        return (60f + lines * 20f + years).coerceIn(CARD_H, MAX_CARD_H)
    }

    private fun uniformRowHeight(memberIds: List<String>, heights: Map<String, Float>): Float =
        if (memberIds.isEmpty()) CARD_H else memberIds.maxOf { heights[it] ?: CARD_H }

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
                    else -> {
                        val shared = maxOf(generationA, generationB)
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

        var parentPass = 0
        while (parentPass++ < people.size + 4) {
            var changed = false
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
            if (!changed) break
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

    private fun alignRowsByGeneration(cards: MutableList<TreeCard>, generation: Map<String, Int>) {
        if (cards.isEmpty() || generation.isEmpty()) return
        val byGen = cards.groupBy { generation[it.id] ?: 0 }
        var y = PAD
        for (g in byGen.keys.sorted()) {
            val row = byGen.getValue(g)
            val rowH = row.maxOf { it.height }
            row.forEach { card ->
                val index = cards.indexOfFirst { it.id == card.id }
                if (index >= 0) {
                    cards[index] = cards[index].copy(top = y)
                }
            }
            y += rowH + ROW_GAP
        }
        resolveHorizontalOverlaps(cards, generation)
    }

    private fun resolveHorizontalOverlaps(cards: MutableList<TreeCard>, generation: Map<String, Int>) {
        val byGen = cards.groupBy { generation[it.id] ?: 0 }
        byGen.values.forEach { row ->
            if (row.size < 2) return@forEach
            val ordered = row.sortedBy { it.left }.map { it.id }
            for (index in 1 until ordered.size) {
                val prev = cards.first { it.id == ordered[index - 1] }
                val currIndex = cards.indexOfFirst { it.id == ordered[index] }
                val curr = cards[currIndex]
                val minLeft = prev.left + CARD_W + SPOUSE_GAP
                if (curr.left < minLeft - 0.1f) {
                    cards[currIndex] = curr.copy(left = minLeft)
                }
            }
        }
    }

    fun place(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
        openLinks: List<OpenLink> = emptyList(),
        homePersonId: String? = null,
    ): TreePlacement {
        if (people.isEmpty()) return TreePlacement(emptyList(), emptyList(), emptyList(), 0f, 0f)
        val byId = people.associateBy { it.id }
        val heights = people.associate { it.id to cardHeight(it) }
        val spouses = people.associate { person ->
            person.id to spouseIdsOf(unions, person.id).filter { it in byId && it != person.id }
        }
        val held = openLinks.filter { it.slot == "bridge" && it.anchorId in byId }
            .flatMap { it.childIds }
            .filter { it in byId }
            .toSet()
        fun hasParents(id: String): Boolean {
            val person = byId[id] ?: return false
            return (person.fatherId != null && person.fatherId in byId) ||
                (person.motherId != null && person.motherId in byId)
        }
        fun isMarriedIn(id: String): Boolean =
            !hasParents(id) && spouses.getValue(id).any { hasParents(it) }
        /** Родитель супруга (тёща/тесть): не отдельное дерево сбоку, а цепочка над парой. */
        fun isInLawOnlyAnchor(id: String): Boolean {
            val children = people.filter { it.fatherId == id || it.motherId == id }
            if (children.isEmpty()) return false
            return children.all { child ->
                spouses.getValue(child.id).any { mate -> hasParents(mate) }
            }
        }
        fun parentsOf(person: PersonEntity): List<String> =
            listOfNotNull(person.fatherId, person.motherId).filter { it in byId && it != person.id }

        fun buildParentChain(personIds: List<String>, stack: Set<String>): House? {
            val ids = personIds.filter { it in byId && it !in stack }.distinct()
            if (ids.isEmpty()) return null
            val stackWith = stack + ids
            val grandparents = ids.flatMap { parentsOf(byId.getValue(it)) }
                .filter { it !in stackWith }
                .distinct()
            val above = if (grandparents.isNotEmpty()) buildParentChain(grandparents, stackWith) else null
            val members = orderMembers(ids, spouses, byId)
            return House(ids, members, emptyList(), above)
        }

        fun buildHouse(bloodIds: List<String>, stack: Set<String>): House {
            val safe = bloodIds.filter { it !in stack }.distinct()
            if (safe.isEmpty()) return House(bloodIds.distinct(), bloodIds.distinct(), emptyList())
            val members = orderMembers(safe, spouses, byId)
            val memberSet = members.toSet()
            val nextStack = stack + memberSet
            val chainsByMember = buildMap {
                for (memberId in members) {
                    val parentIds = parentsOf(byId.getValue(memberId))
                        .filter { it !in stack && it !in memberSet }
                    if (parentIds.isEmpty()) continue
                    val chain = buildParentChain(parentIds, nextStack) ?: continue
                    put(memberId, chain)
                }
            }
            val useSplitParents = chainsByMember.size >= 2 && members.size >= 2
            val parentChain = if (!useSplitParents) chainsByMember.values.firstOrNull() else null
            val memberParentChains = if (useSplitParents) chainsByMember else emptyMap()
            val natural = people.filter { child ->
                if (child.id in nextStack) return@filter false
                val parents = parentsOf(child)
                if (parents.isEmpty()) return@filter false
                when {
                    parents.all { it in memberSet } -> true
                    parents.size == 1 -> parents[0] in memberSet
                    else -> {
                        val fatherIn = child.fatherId in memberSet
                        val motherIn = child.motherId in memberSet
                        when {
                            fatherIn && motherIn -> true
                            fatherIn || motherIn -> {
                                val missing = if (fatherIn) child.motherId else child.fatherId
                                missing == null || missing !in byId
                            }
                            else -> false
                        }
                    }
                }
            }
            val bridged = openLinks.filter { it.slot == "bridge" && it.anchorId in memberSet }
                .flatMap { it.childIds }
                .mapNotNull { byId[it] }
                .filter { it.id !in nextStack && natural.none { child -> child.id == it.id } }
            val children = (natural + bridged)
                .sortedWith(compareBy({ it.birthDate }, { it.id }))
                .map { child -> buildHouse(listOf(child.id), nextStack) }
            return House(safe, members, children, parentChain, memberParentChains)
        }

        val graphComponents = treeComponents(people, unions)
        val home = homePersonId?.trim()?.takeIf { it.isNotBlank() && it in byId }
        val orderedComponents = graphComponents.sortedWith(
            compareByDescending<Set<String>> { it.size }.thenByDescending { home != null && home in it },
        )
        val roots = mutableListOf<House>()
        val seen = mutableSetOf<String>()
        for (component in orderedComponents) {
            val inComponent = component - held
            if (inComponent.isEmpty()) continue
            val rootBlood = inComponent
                .filter { !hasParents(it) && !isMarriedIn(it) && !isInLawOnlyAnchor(it) }
                .sortedWith(compareBy({ byId.getValue(it).birthDate }, { byId.getValue(it).lastName }, { it }))
            if (rootBlood.isEmpty()) {
                val anchor = home?.takeIf { it in inComponent }
                    ?: inComponent.minByOrNull { byId.getValue(it).birthDate }
                if (anchor != null && anchor !in seen) {
                    seen.add(anchor)
                    roots.add(buildHouse(listOf(anchor), emptySet()))
                }
                continue
            }
            for (id in rootBlood) {
                if (id in seen) continue
                val blood = mutableListOf<String>()
                val queue = ArrayDeque<String>()
                queue.add(id)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    if (current !in inComponent) continue
                    if (!seen.add(current) || hasParents(current) || isMarriedIn(current)) continue
                    blood.add(current)
                    spouses.getValue(current).filter { it in inComponent }.forEach { queue.add(it) }
                }
                if (blood.isNotEmpty()) roots.add(buildHouse(blood, emptySet()))
            }
        }
        val placedIds = mutableSetOf<String>()
        fun collect(house: House) {
            house.parentChain?.let { collect(it) }
            house.memberParentChains.values.forEach { collect(it) }
            placedIds.addAll(house.members)
            house.children.forEach { collect(it) }
        }
        roots.forEach { collect(it) }
        people.filter { it.id !in placedIds && it.id !in held }.sortedBy { it.id }.forEach { person ->
            roots.add(buildHouse(listOf(person.id), placedIds.toSet()))
        }

        val cards = mutableListOf<TreeCard>()
        val cardIds = mutableSetOf<String>()
        val childGroups = mutableListOf<ChildGroup>()
        var cursor = PAD
        roots.forEach { house ->
            measure(house)
            position(house, cursor, PAD, cards, cardIds, childGroups, byId, heights, openLinks)
            cursor += house.width + HOUSE_GAP
        }
        alignRowsByGeneration(cards, generationMap(people, unions, openLinks))
        val spouseLinks = unions.mapNotNull { union ->
            if (union.partnerAId in byId && union.partnerBId in byId && union.partnerAId != union.partnerBId) {
                union.partnerAId to union.partnerBId
            } else {
                null
            }
        }.distinct()
        val width = (cards.maxOfOrNull { it.left + CARD_W } ?: PAD) + PAD
        val height = (cards.maxOfOrNull { it.top + it.height } ?: PAD) + PAD
        val knots = openLinks.mapNotNull { link -> knotFor(link, cards) }
        return TreePlacement(cards, spouseLinks, childGroups, width, height, knots)
    }

    private fun orderMembers(
        bloodIds: List<String>,
        spouses: Map<String, List<String>>,
        byId: Map<String, PersonEntity>,
    ): List<String> {
        val blood = bloodIds.distinct().sortedWith(
            compareBy({ genderRank(byId.getValue(it).gender) }, { byId.getValue(it).birthDate }, { it }),
        )
        val mates = blood.flatMap { spouses.getValue(it) }
            .distinct()
            .filter { mate -> mate !in blood && blood.any { person -> mate in spouses.getValue(person) } }
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
        house.parentChain?.let { measure(it) }
        house.memberParentChains.values.forEach { measure(it) }
        house.children.forEach { measure(it) }
        val childrenWidth = if (house.children.isEmpty()) {
            0f
        } else {
            house.children.sumOf { it.width.toDouble() }.toFloat() + (house.children.size - 1) * HOUSE_GAP
        }
        val rowWidth = memberWidth(house.members.size)
        val parentsWidth = when {
            house.memberParentChains.isNotEmpty() -> rowWidth
            else -> house.parentChain?.width ?: 0f
        }
        house.width = max(rowWidth, max(childrenWidth, parentsWidth))
        return house.width
    }

    private fun subtreeHeight(house: House, heights: Map<String, Float>): Float {
        val rowHeight = if (house.members.isEmpty()) 0f else uniformRowHeight(house.members, heights)
        val below = if (house.children.isEmpty()) {
            0f
        } else {
            ROW_GAP + house.children.maxOf { subtreeHeight(it, heights) }
        }
        val splitAbove = house.memberParentChains.values.maxOfOrNull { subtreeHeight(it, heights) } ?: 0f
        val singleAbove = house.parentChain?.let { subtreeHeight(it, heights) } ?: 0f
        val above = when {
            splitAbove > 0f -> splitAbove + ROW_GAP
            singleAbove > 0f -> singleAbove + ROW_GAP
            else -> 0f
        }
        return above + rowHeight + below
    }

    private fun position(
        house: House,
        left: Float,
        top: Float,
        cards: MutableList<TreeCard>,
        cardIds: MutableSet<String>,
        childGroups: MutableList<ChildGroup>,
        byId: Map<String, PersonEntity>,
        heights: Map<String, Float>,
        openLinks: List<OpenLink>,
        siblingRowHeight: Float? = null,
    ) {
        house.left = left
        house.top = top
        var membersTop = top
        val selfWidth = memberWidth(house.members.size)
        val membersLeft = left + (house.width - selfWidth) / 2f
        house.parentChain?.let { parents ->
            val parentLeft = left + (house.width - parents.width) / 2f
            position(parents, parentLeft, top, cards, cardIds, childGroups, byId, heights, openLinks, siblingRowHeight = null)
            membersTop = top + subtreeHeight(parents, heights) + ROW_GAP
        }
        if (house.memberParentChains.isNotEmpty()) {
            var maxAbove = 0f
            house.members.forEachIndexed { index, id ->
                val chain = house.memberParentChains[id] ?: return@forEachIndexed
                val colLeft = membersLeft + index * (CARD_W + SPOUSE_GAP) + (CARD_W - chain.width) / 2f
                position(chain, colLeft, top, cards, cardIds, childGroups, byId, heights, openLinks, siblingRowHeight = null)
                maxAbove = max(maxAbove, subtreeHeight(chain, heights))
            }
            membersTop = top + maxAbove + ROW_GAP
        }
        if (house.parentChain != null || house.memberParentChains.isNotEmpty()) {
            house.members.forEach { memberId ->
                linkMemberToParents(memberId, byId, childGroups)
            }
        }
        val memberRowH = siblingRowHeight ?: uniformRowHeight(house.members, heights)
        house.members.forEachIndexed { index, id ->
            if (cardIds.add(id)) {
                cards.add(
                    TreeCard(
                        id,
                        membersLeft + index * (CARD_W + SPOUSE_GAP),
                        membersTop,
                        memberRowH,
                    ),
                )
            }
        }
        if (house.children.isNotEmpty()) {
            val childrenWidth = house.children.sumOf { it.width.toDouble() }.toFloat() +
                (house.children.size - 1) * HOUSE_GAP
            var childLeft = left + (house.width - childrenWidth) / 2f
            val childTop = membersTop + memberRowH + ROW_GAP
            val childrenRowH = house.children.maxOf { uniformRowHeight(it.members, heights) }
            house.children.forEach { child ->
                position(
                    child,
                    childLeft,
                    childTop,
                    cards,
                    cardIds,
                    childGroups,
                    byId,
                    heights,
                    openLinks,
                    siblingRowHeight = childrenRowH,
                )
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
            openLinks.filter { it.slot == "bridge" && it.anchorId in house.members }.forEach { link ->
                val childIds = link.childIds.filter { id -> cards.any { it.id == id } }
                if (childIds.isNotEmpty()) childGroups.add(ChildGroup(listOf(link.anchorId), childIds, open = true))
            }
        }
    }

    private fun linkMemberToParents(
        memberId: String,
        byId: Map<String, PersonEntity>,
        childGroups: MutableList<ChildGroup>,
    ) {
        val person = byId[memberId] ?: return
        val parents = listOfNotNull(person.fatherId, person.motherId)
            .filter { it in byId && it != memberId }
            .distinct()
            .sorted()
        if (parents.isEmpty()) return
        val duplicate = childGroups.any { group ->
            group.childIds == listOf(memberId) && group.parentIds.sorted() == parents
        }
        if (!duplicate) {
            childGroups.add(ChildGroup(parents, listOf(memberId)))
        }
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

    private class House(
        val anchors: List<String>,
        val members: List<String>,
        val children: List<House>,
        val parentChain: House? = null,
        val memberParentChains: Map<String, House> = emptyMap(),
        var width: Float = 0f,
        var left: Float = 0f,
        var top: Float = 0f,
    )
}
