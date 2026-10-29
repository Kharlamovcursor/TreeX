package ru.hdlabs.treex.domain

import ru.hdlabs.treex.backup.FamilyBackupMapper
import ru.hdlabs.treex.backup.UnionRecord
import ru.hdlabs.treex.backup.hasBothPartners
import ru.hdlabs.treex.backup.partnerIds
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper

data class DataIssue(
    val code: String,
    val message: String,
    val personIds: List<String> = emptyList(),
    val unionIds: List<String> = emptyList(),
)

object TreeDataValidator {
    fun validate(
        people: List<PersonEntity>,
        unions: List<UnionRecord>,
    ): List<DataIssue> {
        val issues = mutableListOf<DataIssue>()
        val ids = people.map { it.id }.toSet()
        val living = people.filter { !it.vacant }
        val byId = people.associateBy { it.id }
        val (families, children) = FamilyBackupMapper.fromUnionRecords(unions)
        val graph = GenealogyGraphMapper.fromFamilies(people, families, children)
        val parentMap = living.associate { person ->
            val fam = graph.familyWhereChild(person.id)
            person.id to (fam?.partnerAId to fam?.partnerBId)
        }

        unions.forEach { union ->
            union.partnerIds().forEach { id ->
                if (id !in ids) {
                    issues += DataIssue(
                        "missing_ref",
                        "В союзе ${union.id} указан партнёр $id, которого нет в древе.",
                        unionIds = listOf(union.id),
                        personIds = listOf(id),
                    )
                }
            }
            union.childIds.forEach { childId ->
                if (childId !in ids) {
                    issues += DataIssue(
                        "missing_ref",
                        "В союзе ${union.id} указан ребёнок $childId, которого нет в древе.",
                        unionIds = listOf(union.id),
                        personIds = listOf(childId),
                    )
                }
            }
        }

        living.forEach { person ->
            val (f, m) = parentMap[person.id] ?: (null to null)
            if (TreeRules.createsCycle(parentMap, person.id, f, m)) {
                issues += DataIssue(
                    "cycle",
                    "Цикл родительства затрагивает «${person.fullName()}».",
                    personIds = listOfNotNull(person.id, f, m).distinct(),
                )
            }
        }

        living.forEach { person ->
            val parents = graph.parentsOf(person.id).map { it.id }
            if (parents.size >= 2 && parents.distinct().size == 1) {
                issues += DataIssue(
                    "duplicate_parent",
                    "У «${person.fullName()}» отец и мать — одно лицо.",
                    personIds = listOf(person.id),
                )
            }
        }

        val spousePairs = unions.filter { it.hasBothPartners() && it.childIds.isEmpty() }
            .groupBy { coupleKey(it.partnerAId!!, it.partnerBId!!) }
        spousePairs.filter { it.value.size > 1 }.forEach { (_, list) ->
            issues += DataIssue(
                "duplicate_spouse",
                "Дублируется брачный союз ${list.first().partnerAId}–${list.first().partnerBId}.",
                unionIds = list.map { it.id },
            )
        }

        living.forEach { person ->
            if (person.birthDate.isNotBlank() && person.deathDate.isNotBlank()) {
                if (person.birthDate > person.deathDate) {
                    issues += DataIssue(
                        "dates",
                        "У «${person.fullName()}» дата рождения позже даты смерти.",
                        personIds = listOf(person.id),
                    )
                }
            }
            graph.parentsOf(person.id).forEach { parent ->
                val parentBirth = parent.birthDate.orEmpty()
                if (parentBirth.isNotBlank() && person.birthDate.isNotBlank() &&
                    parentBirth > person.birthDate
                ) {
                    issues += DataIssue(
                        "dates",
                        "«${parent.fullName}» не может быть родителем «${person.fullName()}» по датам рождения.",
                        personIds = listOf(person.id, parent.id),
                    )
                }
            }
        }

        return issues.distinctBy { it.code + it.message }
    }
}

private fun coupleKey(a: String, b: String): String = "${minOf(a, b)}|${maxOf(a, b)}"
