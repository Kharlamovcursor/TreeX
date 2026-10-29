package ru.hdlabs.treex.domain.graph

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.domain.fullName

/**
 * Builds [GenealogyGraph] from Room entities. Unions are authoritative; [PersonEntity.fatherId]/[motherId]
 * are used only to synthesize missing families.
 */
object GenealogyGraphMapper {

    private const val LOG_TAG = "GenealogyGraphMapper"

    fun fromEntities(people: List<PersonEntity>, unions: List<UnionEntity>): GenealogyGraph {
        val legacyFamilyIds = mutableSetOf<String>()
        val living = people.filter { !it.vacant }
        val ids = living.map { it.id }.toSet()

        val seenPersonIds = mutableSetOf<String>()
        living.forEach { person ->
            if (!seenPersonIds.add(person.id)) {
                AppEventLog.warn(LOG_TAG, "Duplicate person id in input: ${person.id}")
            }
        }

        val personMap = linkedMapOf<String, Person>()
        living.forEach { entity ->
            personMap[entity.id] = entity.toGraphPerson()
        }

        val familyMap = linkedMapOf<String, Family>()
        unions.forEach { union ->
            val parsed = parseChildIds(union.id, union.childIds)
            familyMap[union.id] = Family(
                id = union.id,
                partnerAId = union.partnerAId?.takeIf { it in ids },
                partnerBId = union.partnerBId?.takeIf { it in ids },
                childrenIds = parsed,
            )
        }

        living.forEach { child ->
            val alreadyInFamily = familyMap.values.any { child.id in it.childrenIds }
            if (alreadyInFamily) return@forEach
            val father = child.fatherId?.takeIf { it in ids && it != child.id }
            val mother = child.motherId?.takeIf { it in ids && it != child.id }
            if (father == null && mother == null) return@forEach
            val legacyId = legacyFamilyId(child.id)
            legacyFamilyIds.add(legacyId)
            AppEventLog.info(LOG_TAG, "Synthetic legacy family $legacyId for child ${child.id}")
            familyMap[legacyId] = Family(
                id = legacyId,
                partnerAId = father,
                partnerBId = mother,
                childrenIds = listOf(child.id),
            )
        }

        if (legacyFamilyIds.isNotEmpty()) {
            AppEventLog.info(LOG_TAG, "Legacy families: ${legacyFamilyIds.size}")
        }

        return GenealogyGraph(persons = personMap, families = familyMap)
    }

    internal fun legacyFamilyId(childId: String): String = "legacy-$childId"

    internal fun parseChildIds(unionId: String, raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val distinct = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        parts.forEach { id ->
            if (!seen.add(id)) {
                AppEventLog.warn(LOG_TAG, "Duplicate child id '$id' in union $unionId, keeping one")
            } else {
                distinct.add(id)
            }
        }
        return distinct
    }
}

private fun PersonEntity.toGraphPerson(): Person {
    val gender = when (gender) {
        "m" -> Gender.MALE
        "f" -> Gender.FEMALE
        else -> Gender.UNKNOWN
    }
    val avatar = photoFile.takeIf { it.isNotBlank() }
    return Person(
        id = id,
        fullName = fullName(),
        gender = gender,
        birthDate = birthDate.takeIf { it.isNotBlank() },
        deathDate = deathDate.takeIf { it.isNotBlank() },
        avatarUrl = avatar,
    )
}
