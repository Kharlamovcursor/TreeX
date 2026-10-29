package ru.hdlabs.treex.relatives

/**
 * Immutable CRUD with bidirectional spouse/parent links (TZ §2).
 * Kotlin mirror of [app/genealogy-tree/familyCrud.ts].
 */
object RelativesFamilyCrud {
    data class PersonRecord(
        val id: String,
        val firstName: String,
        val lastName: String,
        val gender: String,
        val birthDate: String = "",
        val deathDate: String = "",
        val spouses: List<RelativesRelation> = emptyList(),
        val parents: List<RelativesRelation> = emptyList(),
    )

    fun addPartner(
        people: List<PersonRecord>,
        personId: String,
        partner: PersonRecord,
        linkType: String = "married",
    ): List<PersonRecord> {
        val index = people.indexOfFirst { it.id == personId }
        require(index >= 0) { "Person $personId not found" }
        val linkA = RelativesRelation(partner.id, linkType)
        val linkB = RelativesRelation(personId, linkType)
        val updatedA = upsertSpouse(people[index], linkA)
        val updatedB = upsertSpouse(partner, linkB)
        return people.filter { it.id != partner.id }.map { if (it.id == personId) updatedA else it } + updatedB
    }

    fun addChild(
        people: List<PersonRecord>,
        parent1Id: String,
        parent2Id: String?,
        child: PersonRecord,
    ): List<PersonRecord> {
        val parents = buildList {
            add(RelativesRelation(parent1Id, "blood"))
            if (parent2Id != null) add(RelativesRelation(parent2Id, "blood"))
        }
        return people + child.copy(parents = parents)
    }

    fun addParentToSingleParent(
        people: List<PersonRecord>,
        childId: String,
        existingParentId: String,
        newParent: PersonRecord,
    ): List<PersonRecord> {
        val child = people.find { it.id == childId } ?: error("Child $childId not found")
        require(child.parents.any { it.id == existingParentId }) {
            "Parent $existingParentId is not linked to child $childId"
        }
        var next = addPartner(people, existingParentId, newParent, "married")
        next = next.map { person ->
            if (person.id != childId) person
            else person.copy(
                parents = person.parents.filter { it.id != newParent.id } +
                    RelativesRelation(newParent.id, "blood"),
            )
        }
        return next
    }

    fun deletePerson(people: List<PersonRecord>, personId: String): List<PersonRecord> =
        people
            .filter { it.id != personId }
            .map { person ->
                person.copy(
                    spouses = person.spouses.filter { it.id != personId },
                    parents = person.parents.filter { it.id != personId },
                )
            }

    private fun upsertSpouse(person: PersonRecord, link: RelativesRelation): PersonRecord {
        val spouses = person.spouses.filter { it.id != link.id } + link
        return person.copy(spouses = spouses)
    }
}
