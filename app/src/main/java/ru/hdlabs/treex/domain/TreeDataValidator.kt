package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

data class DataIssue(
    val code: String,
    val message: String,
    val personIds: List<String> = emptyList(),
    val unionIds: List<String> = emptyList(),
)

object TreeDataValidator {
    fun validate(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): List<DataIssue> {
        val issues = mutableListOf<DataIssue>()
        val ids = people.map { it.id }.toSet()
        val living = people.filter { !it.vacant }
        val byId = people.associateBy { it.id }
        val compact = UnionEr.compactUnions(people, unions)

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
            union.childIdsList().forEach { childId ->
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

        val parentMap = UnionEr.parentPairsMap(living, compact)
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
            val parents = UnionEr.parentIdsOf(person, ids, compact)
            if (parents.size >= 2 && parents.distinct().size == 1) {
                issues += DataIssue(
                    "duplicate_parent",
                    "У «${person.fullName()}» отец и мать — одно лицо.",
                    personIds = listOf(person.id),
                )
            }
        }

        val spousePairs = unions.filter { it.hasBothPartners() && it.childIdsList().isEmpty() }
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
            UnionEr.parentIdsOf(person, ids, compact).forEach { parentId ->
                val parent = byId[parentId] ?: return@forEach
                if (parent.birthDate.isNotBlank() && person.birthDate.isNotBlank() &&
                    parent.birthDate > person.birthDate
                ) {
                    issues += DataIssue(
                        "dates",
                        "«${parent.fullName()}» не может быть родителем «${person.fullName()}» по датам рождения.",
                        personIds = listOf(person.id, parentId),
                    )
                }
            }
        }

        return issues.distinctBy { it.code + it.message }
    }
}

private fun coupleKey(a: String, b: String): String = "${minOf(a, b)}|${maxOf(a, b)}"
