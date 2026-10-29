package ru.hdlabs.treex.domain

import kotlinx.serialization.Serializable
import ru.hdlabs.treex.backup.UnionRecord
import ru.hdlabs.treex.data.local.PersonEntity
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
        "vacancy" -> {
            val anchor = name(link.anchorId)
            "Пустое место — выберите человека для связи с $anchor или тапните само место, чтобы создать карточку"
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

private fun childParentPair(unions: List<UnionRecord>, childId: String): Pair<String?, String?> {
    val union = unions.find { childId in it.childIds } ?: return null to null
    return union.partnerAId to union.partnerBId
}

fun linksAfterRemoval(
    removed: PersonEntity,
    people: List<PersonEntity>,
    unions: List<UnionRecord>,
): List<OpenLink> {
    val known = people.map { it.id }.toSet()
    if (removed.id !in known) return emptyList()
    val spouses = spouseIdsOf(unions, removed.id).filter { it in known && it != removed.id }
    val (removedFather, removedMother) = childParentPair(unions, removed.id)
    val parentIds = listOfNotNull(removedFather, removedMother).filter { it in known && it != removed.id }
    val links = mutableListOf<OpenLink>()
    spouses.forEach { spouseId ->
        links += OpenLink(UUID.randomUUID().toString(), "spouse", spouseId, spouseId)
    }
    val orphans = mutableListOf<String>()
    people.filter { it.id != removed.id }.forEach { child ->
        val (father, mother) = childParentPair(unions, child.id)
        if (removed.id != father && removed.id != mother) return@forEach
        val lostFather = removed.id == father
        val other = if (lostFather) mother else father
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

/** Legacy helper; parent edges are applied via [KinshipRepository] in [TreeRepository.attachOpenLink]. */
fun reattachPeople(link: OpenLink, target: PersonEntity, people: List<PersonEntity>): List<PersonEntity> =
    emptyList()

fun reattachSpouse(link: OpenLink, targetId: String): Pair<String, String>? {
    if (link.slot == "spouse" && targetId != link.ownerId) return link.ownerId to targetId
    if (link.slot == "bridge" && !link.anchorIsParent && targetId != link.anchorId) return link.anchorId to targetId
    if (link.slot == "vacancy" && targetId != link.ownerId && targetId != link.anchorId) {
        return link.anchorId to targetId
    }
    return null
}

fun transferVacantSlot(
    vacantId: String,
    targetId: String,
    people: List<PersonEntity>,
    unions: List<UnionRecord>,
): Pair<List<PersonEntity>, List<UnionRecord>> {
    if (vacantId == targetId) return emptyList<PersonEntity>() to unions
    val byId = people.associateBy { it.id }.toMutableMap()
    val vacant = byId[vacantId] ?: return emptyList<PersonEntity>() to unions
    val target = byId[targetId] ?: return emptyList<PersonEntity>() to unions
    if (!vacant.vacant) return emptyList<PersonEntity>() to unions
    val updatedUnions = unions.map { union ->
        union.copy(
            partnerAId = union.partnerAId?.replace(vacantId, targetId),
            partnerBId = union.partnerBId?.replace(vacantId, targetId),
            childIds = union.childIds.map { if (it == vacantId) targetId else it },
        )
    }
    return emptyList<PersonEntity>() to updatedUnions.distinctBy { it.id }
}
