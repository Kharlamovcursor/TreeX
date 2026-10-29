package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Keeps layout and kin lines aligned with living people only (no vacant ghosts). */
object TreeIntegrity {
    fun isLiving(person: PersonEntity): Boolean = !person.vacant

    fun livingPeople(people: List<PersonEntity>): List<PersonEntity> =
        people.filter { isLiving(it) }

    fun livingIds(people: List<PersonEntity>): Set<String> =
        livingPeople(people).map { it.id }.toSet()

    fun livingUnions(unions: List<UnionEntity>, people: List<PersonEntity>): List<UnionEntity> {
        val byId = people.associateBy { it.id }
        return unions.filter { union ->
            val a = byId[union.partnerAId]
            val b = byId[union.partnerBId]
            a != null && b != null && isLiving(a) && isLiving(b)
        }
    }

    fun livingParentIds(person: PersonEntity, living: Set<String>, byId: Map<String, PersonEntity>): List<String> =
        listOfNotNull(person.fatherId, person.motherId)
            .filter { it in living && isLiving(byId.getValue(it)) }

    /** Clears parent pointers that point at vacant or missing people. */
    fun repairParentPointers(people: List<PersonEntity>): List<PersonEntity> {
        val living = livingIds(people)
        return people.mapNotNull { person ->
            if (!isLiving(person)) return@mapNotNull null
            val father = person.fatherId?.takeIf { it in living }
            val mother = person.motherId?.takeIf { it in living }
            if (father == person.fatherId && mother == person.motherId) return@mapNotNull null
            person.copy(
                fatherId = father,
                motherId = mother,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }
}
