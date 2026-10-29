package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

fun treeComponents(
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
): List<Set<String>> {
    if (people.isEmpty()) return emptyList()
    val ids = people.map { it.id }.toSet()
    val neighbors = ids.associateWith { mutableSetOf<String>() }.toMutableMap()
    people.forEach { person ->
        person.fatherId?.takeIf { it in ids }?.let { parent ->
            neighbors.getValue(person.id).add(parent)
            neighbors.getValue(parent).add(person.id)
        }
        person.motherId?.takeIf { it in ids }?.let { parent ->
            neighbors.getValue(person.id).add(parent)
            neighbors.getValue(parent).add(person.id)
        }
    }
    unions.forEach { union ->
        union.childIdsList().filter { it in ids }.forEach { childId ->
            union.partnerIds().filter { it in ids }.forEach { parentId ->
                neighbors.getValue(childId).add(parentId)
                neighbors.getValue(parentId).add(childId)
            }
        }
        val a = union.partnerAId
        val b = union.partnerBId
        if (a != null && b != null && a in ids && b in ids && a != b) {
            neighbors.getValue(a).add(b)
            neighbors.getValue(b).add(a)
        }
    }
    val seen = mutableSetOf<String>()
    val result = mutableListOf<Set<String>>()
    ids.forEach { start ->
        if (start in seen) return@forEach
        val component = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue
            component.add(current)
            neighbors[current]?.forEach { next -> if (next !in seen) queue.add(next) }
        }
        if (component.isNotEmpty()) result += component
    }
    return result
}

fun mainTreeComponent(
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
    homePersonId: String?,
): Set<String> {
    val components = treeComponents(people, unions)
    if (components.isEmpty()) return emptySet()
    val home = homePersonId?.trim()?.takeIf { it.isNotEmpty() }
    if (home != null) {
        return components.find { home in it } ?: components.maxBy { it.size }
    }
    return components.maxBy { it.size }
}

fun detachedFromMainPersonIds(
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
    homePersonId: String?,
): Set<String> {
    val main = mainTreeComponent(people, unions, homePersonId)
    return people.filter { it.id !in main && !it.vacant }.map { it.id }.toSet()
}
