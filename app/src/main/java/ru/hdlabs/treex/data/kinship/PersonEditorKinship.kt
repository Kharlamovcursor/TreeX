package ru.hdlabs.treex.data.kinship

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.data.TreeRepository
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.settings.SettingsRepository
import ru.hdlabs.treex.domain.RelativeAttach
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.capitalizePersonName
import ru.hdlabs.treex.domain.mergePerson
import ru.hdlabs.treex.domain.graph.ValidationIssue

internal fun KinshipWriteResult.requireSuccess() {
    when (this) {
        is KinshipWriteResult.Success -> Unit
        is KinshipWriteResult.BlockedReason -> error(code.name)
        is KinshipWriteResult.Blocked -> {
            val cycle = errors.filterIsInstance<ValidationIssue.CycleDetected>().firstOrNull()
            if (cycle != null) error("Такая связь замыкает круг в древе")
            error(errors.joinToString { it.javaClass.simpleName })
        }
    }
}

internal suspend fun persistPersonEditor(
    kinship: KinshipRepository,
    treeRepository: TreeRepository,
    settingsRepository: SettingsRepository,
    creating: Boolean,
    draft: PersonEntity,
    spouses: List<SpouseDraft>,
    attach: RelativeAttach?,
    preserveUntouched: Boolean,
    touched: Set<String>,
    previous: PersonEntity?,
    baselineSpouseIds: List<String>,
    baselineFatherId: String?,
    baselineMotherId: String?,
    snapshot: Boolean,
): Boolean = withContext(NonCancellable) {
    val merged = if (previous != null && preserveUntouched) mergePerson(previous, draft, touched) else draft
    val clean = merged.copy(
        lastName = capitalizePersonName(merged.lastName),
        birthLastName = if (merged.gender == "f") capitalizePersonName(merged.birthLastName) else "",
        firstName = capitalizePersonName(merged.firstName),
        middleName = capitalizePersonName(merged.middleName),
        fatherId = null,
        motherId = null,
        gender = if (merged.gender == "m" || merged.gender == "f") merged.gender else "u",
        updatedAtMillis = System.currentTimeMillis(),
    )
    val knownIds = treeRepository.observe().first().people.map { it.id }.toSet() + clean.id
    val formSpouses = spouses.filter { it.personId.isNotBlank() && it.personId != clean.id && it.personId in knownIds }
        .distinctBy { it.personId }
    val spouseIds = formSpouses.map { it.personId }

    var changed = false
    if (creating) {
        when (attach?.role) {
            "spouse" -> {
                kinship.addSpouseFromEditor(attach.anchorId, clean).requireSuccess()
                changed = true
            }
            "child" -> {
                val coParent = listOfNotNull(merged.fatherId, merged.motherId)
                    .firstOrNull { it.isNotBlank() && it != attach.anchorId }
                kinship.addChildFromEditor(attach.anchorId, clean, coParent).requireSuccess()
                changed = true
            }
            "father" -> {
                kinship.addPersonFromEditor(clean).requireSuccess()
                kinship.linkExistingAsParent(attach.anchorId, clean.id, ParentRole.FATHER).requireSuccess()
                changed = true
            }
            "mother" -> {
                kinship.addPersonFromEditor(clean).requireSuccess()
                kinship.linkExistingAsParent(attach.anchorId, clean.id, ParentRole.MOTHER).requireSuccess()
                changed = true
            }
            "brother", "sister" -> {
                kinship.addSiblingFromEditor(attach.anchorId, clean).requireSuccess()
                changed = true
            }
            else -> {
                kinship.addPersonFromEditor(clean).requireSuccess()
                changed = true
            }
        }
        if (attach?.role != "spouse" && spouseIds.isNotEmpty()) {
            spouseIds.forEach { sid ->
                kinship.linkExistingAsSpouse(clean.id, sid).requireSuccess()
            }
            changed = true
        }
    } else {
        kinship.updatePersonFields(clean).requireSuccess()
        changed = true
        val replaceSpouses = !preserveUntouched || "spouses" in touched
        if (replaceSpouses) {
            baselineSpouseIds.filter { it !in spouseIds }.forEach { removed ->
                kinship.detachSpouse(clean.id, removed).requireSuccess()
            }
            spouseIds.filter { it !in baselineSpouseIds.toSet() }.forEach { added ->
                kinship.linkExistingAsSpouse(clean.id, added).requireSuccess()
            }
        }
        if ("father" in touched) {
            val next = draft.fatherId?.takeIf { it.isNotBlank() }
            if (baselineFatherId != null && baselineFatherId != next) {
                kinship.detachParent(clean.id, baselineFatherId).requireSuccess()
            }
            if (next != null && next != baselineFatherId) {
                kinship.linkExistingAsParent(clean.id, next, ParentRole.FATHER).requireSuccess()
            }
        }
        if ("mother" in touched) {
            val next = draft.motherId?.takeIf { it.isNotBlank() }
            if (baselineMotherId != null && baselineMotherId != next) {
                kinship.detachParent(clean.id, baselineMotherId).requireSuccess()
            }
            if (next != null && next != baselineMotherId) {
                kinship.linkExistingAsParent(clean.id, next, ParentRole.MOTHER).requireSuccess()
            }
        }
    }

    if (changed && snapshot) {
        treeRepository.snapshotNow()
    }
    if (changed) {
        val settings = settingsRepository.settings.first()
        if (settings.homePersonId.isBlank() && !clean.vacant) {
            val living = treeRepository.observe().first().people.count { !it.vacant }
            if (living == 1) {
                settingsRepository.update { it.copy(homePersonId = clean.id) }
            }
        }
    }
    changed
}
