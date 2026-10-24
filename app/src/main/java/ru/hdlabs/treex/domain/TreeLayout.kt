package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

object TreeLayout {
    fun bands(people: List<PersonEntity>, unions: List<UnionEntity>): List<List<PersonEntity>> {
        if (people.isEmpty()) return emptyList()
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
                }
            }
            if (!changed) break
        }

        val minGeneration = generation.values.minOrNull() ?: 0
        return people
            .groupBy { (generation[it.id] ?: 0) - minGeneration }
            .toSortedMap()
            .values
            .map { band ->
                band.sortedWith(
                    compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }, { it.id }),
                )
            }
    }
}
