package ru.hdlabs.treex.domain.graph

/**
 * Immutable read-model over persons and family hubs. All kinship queries go through [families].
 */
class GenealogyGraph(
    val persons: Map<String, Person>,
    val families: Map<String, Family>,
) {
    /** Stable iteration order for [familyWhereChild] (first match wins). */
    private val familyOrder: List<Family> = families.values.toList()

    fun person(id: String): Person? = persons[id]

    fun family(id: String): Family? = families[id]

    fun parentsOf(personId: String): List<Person> =
        familyWhereChild(personId)?.let { fam ->
            listOfNotNull(fam.partnerAId, fam.partnerBId)
                .mapNotNull { persons[it] }
        }.orEmpty()

    fun childrenOf(personId: String): List<Person> =
        familiesOf(personId)
            .flatMap { it.childrenIds }
            .distinct()
            .mapNotNull { persons[it] }

    fun spousesOf(personId: String): List<Person> =
        familiesOf(personId)
            .flatMap { fam ->
                when (personId) {
                    fam.partnerAId -> listOfNotNull(fam.partnerBId)
                    fam.partnerBId -> listOfNotNull(fam.partnerAId)
                    else -> emptyList()
                }
            }
            .distinct()
            .mapNotNull { persons[it] }

    /** Families where [personId] is [Family.partnerAId] or [Family.partnerBId]. */
    fun familiesOf(personId: String): List<Family> =
        familyOrder.filter { fam ->
            personId == fam.partnerAId || personId == fam.partnerBId
        }

    /**
     * The family that lists [personId] as a child.
     *
     * If the child appears in [Family.childrenIds] of several families (data quirk), only the **first**
     * family in mapper/insertion order is returned; other families still keep the id in their lists.
     */
    fun familyWhereChild(personId: String): Family? =
        familyOrder.firstOrNull { personId in it.childrenIds }

    fun siblingsOf(personId: String): List<Person> {
        val fam = familyWhereChild(personId) ?: return emptyList()
        return fam.childrenIds
            .filter { it != personId }
            .distinct()
            .mapNotNull { persons[it] }
    }

    fun allFamiliesFor(personId: String): List<Family> {
        val asPartner = familiesOf(personId)
        val asChild = familyWhereChild(personId)
        return if (asChild != null && asChild !in asPartner) {
            asPartner + asChild
        } else {
            asPartner
        }
    }
}
