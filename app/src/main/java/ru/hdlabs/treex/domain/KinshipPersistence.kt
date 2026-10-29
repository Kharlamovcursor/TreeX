package ru.hdlabs.treex.domain

import kotlin.DeprecationLevel
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/**
 * Единая запись родства в БД: союзы + синхронизация полей Person.
 * Координаты дерева не затрагиваются.
 */
object KinshipPersistence {
    @Deprecated("Use KinshipRepository", level = DeprecationLevel.WARNING)
    fun rebuildUnions(
        people: List<PersonEntity>,
        existingUnions: List<UnionEntity>,
        focusPersonId: String,
        spouseDrafts: List<SpouseDraft>,
        attach: RelativeAttach?,
        focusPerson: PersonEntity,
    ): List<UnionEntity> {
        val ids = people.map { it.id }.toSet()
        var unions = dropRecreatableSpouseUnions(existingUnions, focusPersonId)
        unions = unions + spouseDrafts
            .filter { it.personId.isNotBlank() && it.personId in ids && it.personId != focusPersonId }
            .distinctBy { it.personId }
            .map { spouse ->
                val a = minOf(focusPersonId, spouse.personId)
                val b = maxOf(focusPersonId, spouse.personId)
                UnionEntity(
                    id = stableUnionId(a, b),
                    partnerAId = a,
                    partnerBId = b,
                    marriedOn = spouse.marriedOn.trim(),
                )
            }
        if (attach != null && attach.anchorId in ids && attach.anchorId != focusPersonId) {
            unions = UnionErAttach.applyRelativeAttach(
                people = people,
                unions = unions,
                anchorId = attach.anchorId,
                role = attach.role,
                newPerson = focusPerson,
            )
        }
        return UnionEr.compactUnions(people, unions)
    }

    /** Брачные союзы без детей для focus пересоздаются; родительские союзы сохраняем. */
    private fun dropRecreatableSpouseUnions(
        unions: List<UnionEntity>,
        focusPersonId: String,
    ): List<UnionEntity> = unions.filter { union ->
        when {
            focusPersonId !in union.partnerIds() -> true
            union.childIdsList().isNotEmpty() -> true
            union.hasBothPartners() && union.childIdsList().isEmpty() -> false
            else -> true
        }
    }
}
