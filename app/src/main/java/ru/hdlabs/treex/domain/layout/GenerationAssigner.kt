package ru.hdlabs.treex.domain.layout

import ru.hdlabs.treex.domain.graph.GenealogyGraph

/**
 * Assigns integer generation levels (roots = 0, children = parentMax + 1).
 * Partners are always aligned to the same level (max of the pair).
 */
object GenerationAssigner {

    fun assign(graph: GenealogyGraph): Map<String, Int> {
        val levels = mutableMapOf<String, Int>()
        val personIds = graph.persons.keys.sorted()

        fun isRoot(id: String): Boolean {
            val fam = graph.familyWhereChild(id) ?: return true
            val hasParentPartner = fam.partnerAId != null || fam.partnerBId != null
            return !hasParentPartner
        }

        val queue = ArrayDeque<String>()
        personIds.filter { isRoot(it) }.forEach { id ->
            levels[id] = 0
            queue.add(id)
        }
        if (queue.isEmpty() && personIds.isNotEmpty()) {
            val first = personIds.first()
            levels[first] = 0
            queue.add(first)
        }

        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val base = levels[id] ?: 0
            graph.childrenOf(id).sortedBy { it.id }.forEach { child ->
                val next = base + 1
                val prev = levels[child.id]
                if (prev == null || next > prev) {
                    levels[child.id] = next
                    queue.add(child.id)
                }
            }
        }

        personIds.forEach { id ->
            if (id !in levels) {
                val parentLevels = graph.parentsOf(id).mapNotNull { levels[it.id] }
                levels[id] = if (parentLevels.isEmpty()) 0 else parentLevels.max() + 1
            }
        }

        repeat(personIds.size.coerceAtLeast(1)) {
            alignSpouses(graph, levels)
        }

        personIds.forEach { id ->
            val parentMax = graph.parentsOf(id).mapNotNull { levels[it.id] }.maxOrNull()
            if (parentMax != null) {
                val want = parentMax + 1
                val cur = levels[id] ?: 0
                if (want > cur) levels[id] = want
            }
        }
        alignSpouses(graph, levels)

        return personIds.associateWith { levels[it] ?: 0 }
    }

    private fun alignSpouses(graph: GenealogyGraph, levels: MutableMap<String, Int>) {
        graph.families.values.sortedBy { it.id }.forEach { fam ->
            val partners = listOfNotNull(fam.partnerAId, fam.partnerBId)
            if (partners.isEmpty()) return@forEach
            val assigned = partners.mapNotNull { levels[it] }
            if (assigned.isEmpty()) {
                levels[partners.first()] = 0
                return@forEach
            }
            val target = assigned.max()
            partners.forEach { levels[it] = target }
        }
    }
}
