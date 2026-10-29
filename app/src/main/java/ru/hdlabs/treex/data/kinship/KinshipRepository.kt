package ru.hdlabs.treex.data.kinship

import androidx.annotation.WorkerThread
import androidx.room.withTransaction
import ru.hdlabs.treex.data.local.AppDatabase
import ru.hdlabs.treex.data.local.FamilyChildEntity
import ru.hdlabs.treex.data.local.FamilyEntity
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.capitalizePersonName
import ru.hdlabs.treex.domain.graph.Person
import ru.hdlabs.treex.domain.graph.ValidationIssue
import java.util.UUID

class KinshipRepository(
    private val database: AppDatabase,
    private val onTreeChanged: suspend () -> Unit,
) {
    private val peopleDao get() = database.personDao()
    private val familyDao get() = database.familyDao()
    private val familyChildDao get() = database.familyChildDao()

    @WorkerThread
    suspend fun addPerson(person: Person): KinshipWriteResult =
        mutate { data ->
            val id = person.id.ifBlank { newPersonId() }
            if (id in data.people) {
                return@mutate KinshipWriteResult.Blocked(listOf(ValidationIssue.DuplicatePersonId(id)))
            }
            data.people[id] = person.toNewEntity(id)
            KinshipWriteResult.Success(id)
        }

    @WorkerThread
    suspend fun addPersonFromEditor(entity: PersonEntity): KinshipWriteResult =
        mutate { data ->
            val id = entity.id.ifBlank { newPersonId() }
            if (id in data.people) {
                return@mutate KinshipWriteResult.Blocked(listOf(ValidationIssue.DuplicatePersonId(id)))
            }
            val now = System.currentTimeMillis()
            data.people[id] = normalizeEntity(
                entity.copy(
                    id = id,
                    createdAtMillis = entity.createdAtMillis.takeIf { it > 0 } ?: now,
                    updatedAtMillis = now,
                ),
            )
            KinshipWriteResult.Success(id)
        }

    /** UI editor saves full profile fields (names, notes, …). */
    @WorkerThread
    suspend fun updatePersonFields(entity: PersonEntity): KinshipWriteResult =
        mutate { data ->
            if (entity.id !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            data.people[entity.id] = normalizeEntity(entity)
            KinshipWriteResult.Success(entity.id)
        }

    @WorkerThread
    suspend fun updatePerson(person: Person): KinshipWriteResult {
        val existing = peopleDao.all().find { it.id == person.id }
            ?: return KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
        return updatePersonFields(
            existing.copy(
                gender = when (person.gender) {
                    ru.hdlabs.treex.domain.graph.Gender.MALE -> "m"
                    ru.hdlabs.treex.domain.graph.Gender.FEMALE -> "f"
                    else -> existing.gender
                },
                birthDate = person.birthDate.orEmpty(),
                deathDate = person.deathDate.orEmpty(),
                photoFile = person.avatarUrl ?: existing.photoFile,
            ),
        )
    }

    @WorkerThread
    suspend fun addSpouse(anchorPersonId: String, spouse: Person): KinshipWriteResult =
        addSpouseFromEditor(anchorPersonId, spouse.toNewEntity(spouse.id.ifBlank { newPersonId() }))

    @WorkerThread
    suspend fun addSpouseFromEditor(anchorPersonId: String, spouse: PersonEntity): KinshipWriteResult =
        mutate { data ->
            if (anchorPersonId !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            val spouseId = spouse.id.ifBlank { newPersonId() }
            if (FamilyMutationValidator.hasDuplicateSpouseFamily(data.toGraph(), anchorPersonId, spouseId)) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.DUPLICATE_FAMILY)
            }
            data.people[spouseId] = normalizeEntity(spouse.copy(id = spouseId))
            val familyId = newPersonId()
            val (a, b) = orderedPartners(anchorPersonId, spouseId)
            data.families[familyId] = FamilyEntity(familyId, a, b)
            KinshipWriteResult.Success(spouseId, familyId)
        }

    @WorkerThread
    suspend fun addChild(anchorPersonId: String, child: Person, coParentId: String? = null): KinshipWriteResult =
        addChildFromEditor(anchorPersonId, child.toNewEntity(child.id.ifBlank { newPersonId() }), coParentId)

    @WorkerThread
    suspend fun addChildFromEditor(anchorPersonId: String, child: PersonEntity, coParentId: String? = null): KinshipWriteResult =
        mutate { data ->
            if (anchorPersonId !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            coParentId?.let { if (it !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND) }
            val childId = child.id.ifBlank { newPersonId() }
            data.people[childId] = normalizeEntity(child.copy(id = childId))
            val target = FamilyMutationValidator.childTargetFamilies(data.toGraph(), anchorPersonId, coParentId)
            val familyId = when (target) {
                FamilyMutationValidator.ChildTargetResult.Ambiguous ->
                    return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.AMBIGUOUS_FAMILY)
                FamilyMutationValidator.ChildTargetResult.NeedNewFamily -> {
                    val fid = newPersonId()
                    val (a, b) = if (coParentId != null) orderedPartners(anchorPersonId, coParentId) else anchorPersonId to null
                    data.families[fid] = FamilyEntity(fid, a, b)
                    fid
                }
                is FamilyMutationValidator.ChildTargetResult.UseFamily -> target.familyId
            }
            data.appendChild(familyId, childId)
            KinshipWriteResult.Success(childId, familyId)
        }

    @WorkerThread
    suspend fun addParents(childPersonId: String, father: Person?, mother: Person?): KinshipWriteResult =
        mutate { data ->
            if (childPersonId !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            if (father == null && mother == null) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.EMPTY_PARENTS)
            if (data.toGraph().familyWhereChild(childPersonId) != null) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.ALREADY_HAS_PARENTS)
            }
            val fatherId = father?.let { f ->
                val id = f.id.ifBlank { newPersonId() }
                data.people[id] = f.toNewEntity(id)
                id
            }
            val motherId = mother?.let { m ->
                val id = m.id.ifBlank { newPersonId() }
                data.people[id] = m.toNewEntity(id)
                id
            }
            val familyId = newPersonId()
            data.families[familyId] = FamilyEntity(familyId, fatherId, motherId)
            data.setChildrenForFamily(familyId, listOf(childPersonId))
            KinshipWriteResult.Success(childPersonId, familyId)
        }

    @WorkerThread
    suspend fun addSibling(anchorPersonId: String, sibling: Person): KinshipWriteResult =
        addSiblingFromEditor(anchorPersonId, sibling.toNewEntity(sibling.id.ifBlank { newPersonId() }))

    @WorkerThread
    suspend fun addSiblingFromEditor(anchorPersonId: String, sibling: PersonEntity): KinshipWriteResult =
        addSiblingInternal(anchorPersonId, normalizeEntity(sibling), linkExisting = false)

    @WorkerThread
    suspend fun linkExistingAsSpouse(anchorPersonId: String, existingPersonId: String): KinshipWriteResult =
        mutate { data ->
            if (anchorPersonId !in data.people || existingPersonId !in data.people) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            }
            if (FamilyMutationValidator.hasDuplicateSpouseFamily(data.toGraph(), anchorPersonId, existingPersonId)) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.DUPLICATE_FAMILY)
            }
            val familyId = newPersonId()
            val (a, b) = orderedPartners(anchorPersonId, existingPersonId)
            data.families[familyId] = FamilyEntity(familyId, a, b)
            KinshipWriteResult.Success(existingPersonId, familyId)
        }

    @WorkerThread
    suspend fun linkExistingAsChild(anchorPersonId: String, existingChildId: String, coParentId: String? = null): KinshipWriteResult =
        mutate { data ->
            if (anchorPersonId !in data.people || existingChildId !in data.people) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            }
            coParentId?.let { if (it !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND) }
            val target = FamilyMutationValidator.childTargetFamilies(data.toGraph(), anchorPersonId, coParentId)
            val familyId = when (target) {
                FamilyMutationValidator.ChildTargetResult.Ambiguous ->
                    return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.AMBIGUOUS_FAMILY)
                FamilyMutationValidator.ChildTargetResult.NeedNewFamily -> {
                    val fid = newPersonId()
                    val (a, b) = if (coParentId != null) orderedPartners(anchorPersonId, coParentId) else anchorPersonId to null
                    data.families[fid] = FamilyEntity(fid, a, b)
                    fid
                }
                is FamilyMutationValidator.ChildTargetResult.UseFamily -> target.familyId
            }
            data.appendChild(familyId, existingChildId)
            KinshipWriteResult.Success(existingChildId, familyId)
        }

    @WorkerThread
    suspend fun linkExistingAsParent(childPersonId: String, existingParentId: String, role: ParentRole): KinshipWriteResult =
        mutate { data ->
            if (childPersonId !in data.people || existingParentId !in data.people) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            }
            var fam = data.toGraph().familyWhereChild(childPersonId)
            if (fam == null) {
                val familyId = newPersonId()
                val entity = when (role) {
                    ParentRole.FATHER -> FamilyEntity(familyId, existingParentId, null)
                    ParentRole.MOTHER -> FamilyEntity(familyId, null, existingParentId)
                    ParentRole.UNKNOWN -> FamilyEntity(familyId, existingParentId, null)
                }
                data.families[familyId] = entity
                data.setChildrenForFamily(familyId, listOf(childPersonId))
            } else {
                val entity = data.families[fam.id] ?: return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
                when (role) {
                    ParentRole.FATHER -> {
                        if (entity.partnerAId != null && entity.partnerAId != existingParentId) {
                            return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.SLOT_OCCUPIED)
                        }
                        data.families[fam.id] = entity.copy(partnerAId = existingParentId)
                    }
                    ParentRole.MOTHER -> {
                        if (entity.partnerBId != null && entity.partnerBId != existingParentId) {
                            return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.SLOT_OCCUPIED)
                        }
                        data.families[fam.id] = entity.copy(partnerBId = existingParentId)
                    }
                    ParentRole.UNKNOWN -> {
                        if (entity.partnerAId == null) data.families[fam.id] = entity.copy(partnerAId = existingParentId)
                        else if (entity.partnerBId == null) data.families[fam.id] = entity.copy(partnerBId = existingParentId)
                        else return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.SLOT_OCCUPIED)
                    }
                }
            }
            KinshipWriteResult.Success(existingParentId)
        }

    @WorkerThread
    suspend fun linkExistingAsSibling(anchorPersonId: String, existingSiblingId: String): KinshipWriteResult =
        mutate { data ->
            if (anchorPersonId !in data.people || existingSiblingId !in data.people) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            }
            when (val pf = FamilyMutationValidator.parentFamilyForSibling(data.toGraph(), anchorPersonId)) {
                FamilyMutationValidator.ParentFamilyResult.Ambiguous ->
                    return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.AMBIGUOUS_PARENT_FAMILY)
                FamilyMutationValidator.ParentFamilyResult.NoParentFamily ->
                    return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.NO_PARENT_FAMILY)
                is FamilyMutationValidator.ParentFamilyResult.UseFamily -> {
                    data.appendChild(pf.familyId, existingSiblingId)
                    KinshipWriteResult.Success(existingSiblingId, pf.familyId)
                }
            }
        }

    @WorkerThread
    suspend fun unlinkSpouse(familyId: String, partnerId: String): KinshipWriteResult =
        mutate { data ->
            val fam = data.families[familyId] ?: return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            if (partnerId != fam.partnerAId && partnerId != fam.partnerBId) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            }
            val updated = when (partnerId) {
                fam.partnerAId -> fam.copy(partnerAId = null)
                else -> fam.copy(partnerBId = null)
            }
            data.families[familyId] = updated
            cleanupEmptyFamily(data, familyId)
            KinshipWriteResult.Success(partnerId, familyId)
        }

    @WorkerThread
    suspend fun unlinkChild(familyId: String, childId: String): KinshipWriteResult =
        mutate { data ->
            if (familyId !in data.families) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            data.removeChildRow(familyId, childId)
            data.renumberChildren(familyId)
            KinshipWriteResult.Success(childId, familyId)
        }

    @WorkerThread
    suspend fun detachParent(childId: String, parentId: String): KinshipWriteResult =
        mutate { data ->
            val graph = data.toGraph()
            val fam = graph.familyWhereChild(childId) ?: return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            val entity = data.families[fam.id] ?: return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            if (parentId !in setOfNotNull(entity.partnerAId, entity.partnerBId)) {
                return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            }
            val updated = when (parentId) {
                entity.partnerAId -> entity.copy(partnerAId = null)
                else -> entity.copy(partnerBId = null)
            }
            data.families[fam.id] = updated
            cleanupEmptyFamily(data, fam.id)
            KinshipWriteResult.Success(childId, fam.id)
        }

    @WorkerThread
    suspend fun detachSpouse(personId: String, spouseId: String): KinshipWriteResult {
        val graph = load().toGraph()
        val family = graph.familiesOf(personId).find { fam ->
            spouseId in setOfNotNull(fam.partnerAId, fam.partnerBId)
        } ?: return KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
        return unlinkSpouse(family.id, spouseId)
    }

    @WorkerThread
    suspend fun removePerson(personId: String): KinshipWriteResult =
        mutate { data ->
            if (personId !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            data.children.removeAll { it.childId == personId }
            data.families.keys.toList().forEach { fid ->
                val fam = data.families[fid] ?: return@forEach
                var u = fam
                if (fam.partnerAId == personId) u = u.copy(partnerAId = null)
                if (fam.partnerBId == personId) u = u.copy(partnerBId = null)
                data.families[fid] = u
                cleanupEmptyFamily(data, fid)
            }
            data.people.remove(personId)
            KinshipWriteResult.Success(personId)
        }

    private suspend fun addSiblingInternal(anchorPersonId: String, sibling: PersonEntity, linkExisting: Boolean): KinshipWriteResult =
        mutate { data ->
            if (anchorPersonId !in data.people) return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.PERSON_NOT_FOUND)
            val siblingId = sibling.id.ifBlank { newPersonId() }
            if (!linkExisting) data.people[siblingId] = sibling.copy(id = siblingId)
            when (val pf = FamilyMutationValidator.parentFamilyForSibling(data.toGraph(), anchorPersonId)) {
                FamilyMutationValidator.ParentFamilyResult.Ambiguous ->
                    return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.AMBIGUOUS_PARENT_FAMILY)
                FamilyMutationValidator.ParentFamilyResult.NoParentFamily ->
                    return@mutate KinshipWriteResult.BlockedReason(KinshipBlockCode.NO_PARENT_FAMILY)
                is FamilyMutationValidator.ParentFamilyResult.UseFamily -> {
                    data.appendChild(pf.familyId, siblingId)
                    KinshipWriteResult.Success(siblingId, pf.familyId)
                }
            }
        }

    private suspend fun mutate(block: suspend (MutableKinshipData) -> KinshipWriteResult): KinshipWriteResult {
        val before = load()
        var result: KinshipWriteResult = KinshipWriteResult.Blocked(emptyList())
        try {
        database.withTransaction {
            val mutable = before.copyMutable()
            result = block(mutable)
            when (result) {
                is KinshipWriteResult.Success -> {
                    val after = mutable.freeze()
                    val validation = FamilyMutationValidator.validateForSave(before.toGraph(), after.toGraph())
                    if (validation.errors.isNotEmpty()) {
                        throw KinshipValidationAbort(validation.errors)
                    }
                    persistDiff(before, after)
                    val ok = result as KinshipWriteResult.Success
                    result = KinshipWriteResult.Success(
                        id = ok.id,
                        familyId = ok.familyId,
                        warnings = validation.warnings,
                    )
                }
                is KinshipWriteResult.Blocked,
                is KinshipWriteResult.BlockedReason,
                -> Unit
            }
        }
        } catch (e: KinshipValidationAbort) {
            return KinshipWriteResult.Blocked(e.errors)
        }
        if (result is KinshipWriteResult.Success) {
            onTreeChanged()
        }
        return result
    }

    private class KinshipValidationAbort(val errors: List<ValidationIssue>) : RuntimeException()

    private suspend fun load(): KinshipData = KinshipData(
        people = peopleDao.all().associateBy { it.id },
        families = familyDao.all().associateBy { it.id },
        children = familyChildDao.all(),
    )

    private suspend fun persistDiff(before: KinshipData, after: KinshipData) {
        (before.people.keys - after.people.keys).forEach { peopleDao.delete(it) }
        after.people.values.forEach { peopleDao.upsert(it) }
        (before.families.keys - after.families.keys).forEach { id ->
            familyChildDao.deleteByFamily(id)
            familyDao.delete(id)
        }
        after.families.values.forEach { familyDao.upsert(it) }
        val familiesTouched = (before.families.keys + after.families.keys).filter { fid ->
            before.families[fid] != after.families[fid] ||
                before.children.filter { it.familyId == fid } != after.children.filter { it.familyId == fid }
        }
        familiesTouched.forEach { fid ->
            familyChildDao.deleteByFamily(fid)
            val rows = after.children.filter { it.familyId == fid }.sortedBy { it.position }
            if (rows.isNotEmpty()) familyChildDao.insertAll(rows)
        }
    }

    private fun cleanupEmptyFamily(data: MutableKinshipData, familyId: String) {
        val fam = data.families[familyId] ?: return
        val hasChildren = data.children.any { it.familyId == familyId }
        if (fam.partnerAId == null && fam.partnerBId == null && !hasChildren) {
            data.families.remove(familyId)
            data.children.removeAll { it.familyId == familyId }
        }
    }

    private fun orderedPartners(a: String, b: String): Pair<String, String> =
        if (a <= b) a to b else b to a

    private fun Person.toNewEntity(id: String): PersonEntity = PersonEntity(
        id = id,
        lastName = "",
        firstName = "",
        middleName = "",
        gender = when (gender) {
            ru.hdlabs.treex.domain.graph.Gender.MALE -> "m"
            ru.hdlabs.treex.domain.graph.Gender.FEMALE -> "f"
            else -> "u"
        },
        birthDate = birthDate.orEmpty(),
        deathDate = deathDate.orEmpty(),
        photoFile = avatarUrl.orEmpty(),
    )

    private fun normalizeEntity(entity: PersonEntity): PersonEntity = entity.copy(
        lastName = capitalizePersonName(entity.lastName),
        birthLastName = capitalizePersonName(entity.birthLastName),
        firstName = capitalizePersonName(entity.firstName),
        middleName = capitalizePersonName(entity.middleName),
        fatherId = null,
        motherId = null,
    )
}
