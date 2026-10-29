package ru.hdlabs.treex.backup

import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.childIdsList
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

/**
 * Canonical storage: [FamilyEntity] + [FamilyChildEntity].
 * [UnionRecord] / [UnionEntity] are wire projections for JSON Git sync and legacy helpers.
 */
object FamilyBackupMapper {

    fun toUnionRecords(
        families: List<FamilyEntity>,
        children: List<FamilyChildEntity>,
    ): List<UnionRecord> {
        val byFamily = children.groupBy { it.familyId }.mapValues { (_, rows) ->
            rows.sortedBy { it.position }.map { it.childId }
        }
        return families.sortedBy { it.id }.map { fam ->
            UnionRecord(
                id = fam.id,
                partnerAId = fam.partnerAId,
                partnerBId = fam.partnerBId,
                marriedOn = fam.marriedOn.orEmpty(),
                childIds = byFamily[fam.id].orEmpty(),
                unionType = fam.unionType?.ifBlank { null } ?: "marriage",
            )
        }
    }

    fun toFamilyRecords(
        families: List<FamilyEntity>,
        children: List<FamilyChildEntity>,
    ): List<FamilyRecord> {
        val byFamily = children.groupBy { it.familyId }.mapValues { (_, rows) ->
            rows.sortedBy { it.position }.map { it.childId }
        }
        return families.sortedBy { it.id }.map { fam ->
            FamilyRecord(
                id = fam.id,
                partnerAId = fam.partnerAId,
                partnerBId = fam.partnerBId,
                marriedOn = fam.marriedOn,
                unionType = fam.unionType,
                childIds = byFamily[fam.id].orEmpty(),
            )
        }
    }

    fun toUnionEntities(
        families: List<FamilyEntity>,
        children: List<FamilyChildEntity>,
    ): List<UnionEntity> = toUnionRecords(families, children).map { record ->
        UnionEntity(
            id = record.id,
            partnerAId = record.partnerAId,
            partnerBId = record.partnerBId,
            marriedOn = record.marriedOn,
            childIds = record.childIds.joinToString(","),
            unionType = record.unionType,
        )
    }

    fun fromUnionRecords(unions: List<UnionRecord>): Pair<List<FamilyEntity>, List<FamilyChildEntity>> =
        fromUnionEntities(
            unions.map { u ->
                UnionEntity(
                    id = u.id,
                    partnerAId = u.partnerAId,
                    partnerBId = u.partnerBId,
                    marriedOn = u.marriedOn,
                    childIds = u.childIds.joinToString(","),
                    unionType = u.unionType,
                )
            },
        )

    fun fromFamilyRecords(records: List<FamilyRecord>): Pair<List<FamilyEntity>, List<FamilyChildEntity>> {
        val families = records.sortedBy { it.id }.map { r ->
            FamilyEntity(
                id = r.id,
                partnerAId = r.partnerAId,
                partnerBId = r.partnerBId,
                marriedOn = r.marriedOn?.takeIf { it.isNotBlank() },
                unionType = r.unionType?.takeIf { it.isNotBlank() },
            )
        }
        val children = records.flatMap { r ->
            r.childIds.mapIndexed { index, childId ->
                FamilyChildEntity(r.id, childId, index)
            }
        }
        return families to children
    }

    fun fromUnionEntities(unions: List<UnionEntity>): Pair<List<FamilyEntity>, List<FamilyChildEntity>> {
        val families = unions.sortedBy { it.id }.map { u ->
            FamilyEntity(
                id = u.id,
                partnerAId = u.partnerAId,
                partnerBId = u.partnerBId,
                marriedOn = u.marriedOn.takeIf { it.isNotBlank() },
                unionType = u.unionType.takeIf { it.isNotBlank() },
            )
        }
        val children = unions.flatMap { u ->
            u.childIdsList().mapIndexed { index, childId ->
                FamilyChildEntity(u.id, childId, index)
            }
        }
        return families to children
    }

    fun enrichPeopleWithLegacyParentFields(
        people: List<PersonRecord>,
        families: List<FamilyEntity>,
        children: List<FamilyChildEntity>,
    ): List<PersonRecord> {
        val graph = GenealogyGraphMapper.fromFamilies(
            people.map { it.toEntityStub() },
            families,
            children,
        )
        return people.map { person ->
            val fam = graph.familyWhereChild(person.id) ?: return@map person
            person.copy(
                fatherId = fam.partnerAId?.takeIf { it != person.id },
                motherId = fam.partnerBId?.takeIf { it != person.id && it != fam.partnerAId },
            )
        }
    }

    private fun PersonRecord.toEntityStub() = PersonEntity(
        id = id,
        lastName = lastName,
        birthLastName = birthLastName,
        firstName = firstName,
        middleName = middleName,
        gender = gender,
        birthDate = birthDate,
        deathDate = deathDate,
        birthPlace = birthPlace,
        email = email,
        phone = phone,
        photoFile = photoFile,
        notes = notes,
        vacant = vacant,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
