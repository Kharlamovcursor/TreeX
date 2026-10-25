package ru.hdlabs.treex.domain

import kotlinx.serialization.Serializable
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import java.util.UUID

fun describeOpenLink(link: OpenLink, people: List<PersonEntity>): String {
    val byId = people.associateBy { it.id }
    fun name(id: String) = byId[id]?.fullName() ?: "человек"
    return when (link.slot) {
        "spouse" -> "Связь супругов у ${name(link.ownerId)} — укажите нового супруга"
        "father" -> "Отец для ${name(link.ownerId)} — выберите человека"
        "mother" -> "Мать для ${name(link.ownerId)} — выберите человека"
        "bridge" -> {
            val kids = link.childIds.map { name(it) }.joinToString(", ")
            val anchor = name(link.anchorId)
            if (link.anchorIsParent) {
                "Дети ($kids) без одного родителя — прикрепите к $anchor или к другому родителю"
            } else {
                "После удаления: дети ($kids) и супруг $anchor — прикрепите линию к нужной карточке"
            }
        }
        else -> "Свободная связь — выберите карточку на древе"
    }
}

@Serializable
data class OpenLink(
    val id: String,
    val slot: String,
    val ownerId: String,
    val anchorId: String,
    val childIds: List<String> = emptyList(),
    val anchorIsParent: Boolean = false,
)

fun linksAfterRemoval(
    removed: PersonEntity,
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
): List<OpenLink> {
    val known = people.map { it.id }.toSet()
    if (removed.id !in known) return emptyList()
    val spouses = spouseIdsOf(unions, removed.id).filter { it in known && it != removed.id }
    val parentIds = listOfNotNull(removed.fatherId, removed.motherId).filter { it in known && it != removed.id }
    val links = mutableListOf<OpenLink>()
    spouses.forEach { spouseId ->
        links += OpenLink(UUID.randomUUID().toString(), "spouse", spouseId, spouseId)
    }
    val orphans = mutableListOf<String>()
    people.filter { it.id != removed.id && (it.fatherId == removed.id || it.motherId == removed.id) }
        .forEach { child ->
            val lostFather = child.fatherId == removed.id
            val other = if (lostFather) child.motherId else child.fatherId
            if (other != null && other in known) {
                links += OpenLink(
                    UUID.randomUUID().toString(),
                    if (lostFather) "father" else "mother",
                    child.id,
                    other,
                )
            } else {
                orphans += child.id
            }
        }
    val parentAnchor = parentIds.firstOrNull()
    val anchor = parentAnchor ?: spouses.firstOrNull()
    if (orphans.isNotEmpty() && anchor != null) {
        links += OpenLink(
            UUID.randomUUID().toString(),
            "bridge",
            anchor,
            anchor,
            orphans,
            anchorIsParent = parentAnchor != null,
        )
    }
    return links
}

fun reattachPeople(link: OpenLink, target: PersonEntity, people: List<PersonEntity>): List<PersonEntity> {
    val byId = people.associateBy { it.id }.toMutableMap()
    if (target.id !in byId) return emptyList()
    val parents = people.associate { it.id to (it.fatherId to it.motherId) }.toMutableMap()
    val updates = mutableListOf<PersonEntity>()
    fun write(person: PersonEntity, fatherId: String?, motherId: String?) {
        if (TreeRules.createsCycle(parents, person.id, fatherId, motherId)) return
        val next = person.copy(
            fatherId = fatherId,
            motherId = motherId,
            updatedAtMillis = System.currentTimeMillis(),
        )
        parents[person.id] = fatherId to motherId
        byId[person.id] = next
        updates += next
    }
    when (link.slot) {
        "father" -> byId[link.ownerId]?.let { owner ->
            if (target.id != owner.id) write(owner, target.id, owner.motherId)
        }
        "mother" -> byId[link.ownerId]?.let { owner ->
            if (target.id != owner.id) write(owner, owner.fatherId, target.id)
        }
        "bridge" -> {
            if (link.anchorIsParent && target.id != link.anchorId) {
                val anchor = byId[link.anchorId]
                val current = byId[target.id]
                if (anchor != null && current != null && current.fatherId == null && current.motherId == null) {
                    if (anchor.gender == "f") write(current, null, anchor.id) else write(current, anchor.id, null)
                }
            }
            link.childIds.forEach { childId ->
                val child = byId[childId] ?: return@forEach
                if (child.id == target.id) return@forEach
                when {
                    child.fatherId == null && (target.gender != "f" || child.motherId != null) ->
                        write(child, target.id, child.motherId)
                    child.motherId == null -> write(child, child.fatherId, target.id)
                    child.fatherId == null -> write(child, target.id, child.motherId)
                }
            }
        }
    }
    return updates
}

fun reattachSpouse(link: OpenLink, targetId: String): Pair<String, String>? {
    if (link.slot == "spouse" && targetId != link.ownerId) return link.ownerId to targetId
    if (link.slot == "bridge" && !link.anchorIsParent && targetId != link.anchorId) return link.anchorId to targetId
    return null
}
