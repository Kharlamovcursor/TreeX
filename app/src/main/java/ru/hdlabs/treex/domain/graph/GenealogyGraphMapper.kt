package ru.hdlabs.treex.domain.graph

import kotlin.DeprecationLevel
import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.domain.fullName

/**
 * Builds [GenealogyGraph] from Room entities.
 */
object GenealogyGraphMapper {

    private const val LOG_TAG = "GenealogyGraphMapper"

    fun fromFamilies(
        persons: List<PersonEntity>,
        families: List<FamilyEntity>,
        familyChildren: List<FamilyChildEntity>,
    ): GenealogyGraph {
        val living = persons.filter { !it.vacant }
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

        val childrenByFamily = familyChildren
            .sortedWith(compareBy({ it.familyId }, { it.position }, { it.childId }))
            .groupBy { it.familyId }

        val familyMap = linkedMapOf<String, Family>()
        families.forEach { entity ->
            val childRows = childrenByFamily[entity.id].orEmpty()
            val childrenIds = mutableListOf<String>()
            val seenChild = mutableSetOf<String>()
            childRows.forEach { row ->
                if (row.childId in ids && seenChild.add(row.childId)) {
                    childrenIds.add(row.childId)
                }
            }
            familyMap[entity.id] = Family(
                id = entity.id,
                partnerAId = entity.partnerAId?.takeIf { it in ids },
                partnerBId = entity.partnerBId?.takeIf { it in ids },
                childrenIds = childrenIds,
            )
        }

        return GenealogyGraph(persons = personMap, families = familyMap)
    }

    @Deprecated("Use fromFamilies after phase 4c", level = DeprecationLevel.WARNING)
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
