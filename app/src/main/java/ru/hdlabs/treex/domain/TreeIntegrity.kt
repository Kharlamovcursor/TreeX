package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity

/** Keeps layout and kin lines aligned with living people only (no vacant ghosts). */
object TreeIntegrity {
    fun isLiving(person: PersonEntity): Boolean = !person.vacant

    fun livingPeople(people: List<PersonEntity>): List<PersonEntity> =
        people.filter { isLiving(it) }

    fun livingIds(people: List<PersonEntity>): Set<String> =
        livingPeople(people).map { it.id }.toSet()

    fun livingFamilies(
        families: List<FamilyEntity>,
        familyChildren: List<FamilyChildEntity>,
        people: List<PersonEntity>,
    ): Pair<List<FamilyEntity>, List<FamilyChildEntity>> {
        val byId = people.associateBy { it.id }
        val living = livingIds(people)
        val childrenByFamily = familyChildren.groupBy { it.familyId }
        val keptFamilies = families.filter { fam ->
            val partners = listOfNotNull(fam.partnerAId, fam.partnerBId).filter { it in living }
            val children = childrenByFamily[fam.id].orEmpty().map { it.childId }.filter { it in living }
            when {
                partners.size >= 2 -> partners.all { id -> byId[id]?.let { isLiving(it) } == true }
                partners.size == 1 && children.isNotEmpty() ->
                    byId[partners.single()]?.let { isLiving(it) } == true
                else -> false
            }
        }.map { it.id }.toSet()
        val keptChildren = familyChildren.filter { it.familyId in keptFamilies && it.childId in living }
        return families.filter { it.id in keptFamilies } to keptChildren
    }

    fun livingParentIds(
        person: PersonEntity,
        living: Set<String>,
        byId: Map<String, PersonEntity>,
        parentIds: List<String>,
    ): List<String> =
        parentIds.filter { it in living && isLiving(byId.getValue(it)) }

    /** No-op: parent links live in families since schema v6. */
    fun repairParentPointers(people: List<PersonEntity>): List<PersonEntity> = emptyList()
}
