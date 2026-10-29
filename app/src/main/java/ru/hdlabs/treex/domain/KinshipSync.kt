package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Синхронизация legacy-полей Person с ER-союзами (координаты дерева не затрагиваются). */
object KinshipSync {
    fun parentFieldsFromUnions(
        person: PersonEntity,
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): PersonEntity {
        if (person.vacant) return person.copy(fatherId = null, motherId = null)
        val ids = people.map { it.id }.toSet()
        val parents = unions
            .filter { person.id in it.childIdsList() }
            .flatMap { it.partnerIds() }
            .filter { it in ids && it != person.id }
            .distinct()
        if (parents.isEmpty()) {
            return person.copy(fatherId = null, motherId = null)
        }
        val byId = people.associateBy { it.id }
        val father = parents.firstOrNull { byId[it]?.gender == "m" }
            ?: parents.firstOrNull()
        val mother = parents.firstOrNull { byId[it]?.gender == "f" }
            ?: parents.firstOrNull { it != father }
        return person.copy(fatherId = father, motherId = mother)
    }

    fun syncAllPeopleParentFields(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): List<PersonEntity> {
        val compact = UnionEr.compactUnions(people, unions)
        return people.map { parentFieldsFromUnions(it, people, compact) }
    }
}
