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
        val living = livingIds(people)
        return unions.filter { union ->
            val partners = union.partnerIds().filter { it in living }
            val children = union.childIdsList().filter { it in living }
            when {
                partners.size >= 2 -> partners.all { id -> byId[id]?.let { isLiving(it) } == true }
                partners.size == 1 && children.isNotEmpty() ->
                    byId[partners.single()]?.let { isLiving(it) } == true
                else -> false
            }
        }
    }

    fun livingParentIds(
        person: PersonEntity,
        living: Set<String>,
        byId: Map<String, PersonEntity>,
        unions: List<UnionEntity> = emptyList(),
    ): List<String> =
        UnionEr.parentIdsOf(person, living, unions)
            .filter { it in living && isLiving(byId.getValue(it)) }

    /** No-op: parent links live in families since schema v6. */
    fun repairParentPointers(people: List<PersonEntity>): List<PersonEntity> = emptyList()
}
