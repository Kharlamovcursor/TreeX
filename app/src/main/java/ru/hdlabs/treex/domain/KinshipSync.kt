package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

/** Legacy stub until 4c-2 removes this object. Parent fields no longer exist on [PersonEntity]. */
object KinshipSync {
    fun parentFieldsFromUnions(
        person: PersonEntity,
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): PersonEntity = person

    fun syncAllPeopleParentFields(
        people: List<PersonEntity>,
        unions: List<UnionEntity>,
    ): List<PersonEntity> = emptyList()
}
