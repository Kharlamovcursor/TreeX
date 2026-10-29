package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
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
        families: List<FamilyEntity>,
        familyChildren: List<FamilyChildEntity>,
    ): List<DataIssue> {
        val issues = mutableListOf<DataIssue>()
        val ids = people.map { it.id }.toSet()
        val living = people.filter { !it.vacant }
        val graph = GenealogyGraphMapper.fromFamilies(people, families, familyChildren)
        val parentMap = living.associate { person ->
            val fam = graph.familyWhereChild(person.id)
            person.id to (fam?.partnerAId to fam?.partnerBId)
        }

        families.forEach { fam ->
            listOfNotNull(fam.partnerAId, fam.partnerBId).forEach { id ->
                if (id !in ids) {
                    issues += DataIssue(
                        "missing_ref",
                        "В семье ${fam.id} указан партнёр $id, которого нет в древе.",
                        unionIds = listOf(fam.id),
                        personIds = listOf(id),
                    )
                }
            }
        }
        familyChildren.forEach { row ->
            if (row.childId !in ids) {
                issues += DataIssue(
                    "missing_ref",
                    "В семье ${row.familyId} указан ребёнок ${row.childId}, которого нет в древе.",
                    unionIds = listOf(row.familyId),
                    personIds = listOf(row.childId),
                )
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

        val spouseFamilies = families.filter { it.partnerAId != null && it.partnerBId != null }
        spouseFamilies
            .groupBy { coupleKey(it.partnerAId!!, it.partnerBId!!) }
            .filter { it.value.size > 1 }
            .forEach { (_, list) ->
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
