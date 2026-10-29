package ru.hdlabs.treex.domain.graph

/**
 * Validation mode: [Read] tolerates some data issues as warnings; [Save] treats them as errors.
 */
enum class ValidationMode {
    Read,
    Save,
}

sealed class ValidationIssue {
    data class DuplicatePersonId(val id: String) : ValidationIssue()
    data class DanglingChildRef(val familyId: String, val childId: String) : ValidationIssue()
    data class DanglingPartnerRef(val familyId: String, val partnerId: String) : ValidationIssue()
    data class ChildInMultipleFamilies(val childId: String, val familyIds: List<String>) : ValidationIssue()
    data class SelfAsParent(val personId: String, val familyId: String) : ValidationIssue()
    data class CycleDetected(val path: List<String>) : ValidationIssue()
    data class DuplicateChildInFamily(val familyId: String, val childId: String) : ValidationIssue()
    data class PartnerAppearsTwiceInFamily(val familyId: String, val personId: String) : ValidationIssue()
}

data class ValidationResult(
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
)

class SaveBlockedException(val issues: List<ValidationIssue>) :
    IllegalStateException("Graph validation failed: ${issues.size} error(s)")

/**
 * Validates [GenealogyGraph] invariants. Marriage edges are not part of cycle detection.
 */
class GenealogyValidator {

    fun validate(graph: GenealogyGraph, mode: ValidationMode = ValidationMode.Read): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        fun modeIssue(issue: ValidationIssue) {
            when (issue) {
                is ValidationIssue.DanglingChildRef,
                is ValidationIssue.DanglingPartnerRef,
                is ValidationIssue.ChildInMultipleFamilies,
                -> if (mode == ValidationMode.Save) errors += issue else warnings += issue
                else -> errors += issue
            }
        }

        graph.families.values.forEach { fam ->
            val partnerIds = listOfNotNull(fam.partnerAId, fam.partnerBId)
            if (fam.partnerAId != null && fam.partnerAId == fam.partnerBId) {
                errors += ValidationIssue.PartnerAppearsTwiceInFamily(fam.id, fam.partnerAId)
            }
            partnerIds.forEach { pid ->
                if (pid !in graph.persons) {
                    modeIssue(ValidationIssue.DanglingPartnerRef(fam.id, pid))
                }
            }
            val seenChild = mutableSetOf<String>()
            fam.childrenIds.forEach { cid ->
                if (!seenChild.add(cid)) {
                    errors += ValidationIssue.DuplicateChildInFamily(fam.id, cid)
                }
                if (cid !in graph.persons) {
                    modeIssue(ValidationIssue.DanglingChildRef(fam.id, cid))
                }
                if (cid in partnerIds) {
                    errors += ValidationIssue.SelfAsParent(cid, fam.id)
                }
            }
        }

        val childToFamilies = mutableMapOf<String, MutableList<String>>()
        graph.families.values.forEach { fam ->
            fam.childrenIds.distinct().forEach { cid ->
                childToFamilies.getOrPut(cid) { mutableListOf() }.add(fam.id)
            }
        }
        childToFamilies.forEach { (childId, familyIds) ->
            if (familyIds.size > 1) {
                modeIssue(ValidationIssue.ChildInMultipleFamilies(childId, familyIds))
            }
        }

        detectCycles(graph)?.let { errors += it }

        return ValidationResult(errors = errors, warnings = warnings)
    }

    fun assertSaveable(graph: GenealogyGraph) {
        val result = validate(graph, ValidationMode.Save)
        if (result.errors.isNotEmpty()) {
            throw SaveBlockedException(result.errors)
        }
    }

    private fun detectCycles(graph: GenealogyGraph): ValidationIssue.CycleDetected? {
        val memo = mutableMapOf<String, Boolean>()
        graph.persons.keys.forEach { start ->
            val path = mutableListOf<String>()
            val stack = mutableSetOf<String>()
            if (hasCycleFrom(start, graph, path, stack, memo)) {
                return ValidationIssue.CycleDetected(path.toList())
            }
        }
        return null
    }

    private fun hasCycleFrom(
        personId: String,
        graph: GenealogyGraph,
        path: MutableList<String>,
        stack: MutableSet<String>,
        memo: MutableMap<String, Boolean>,
    ): Boolean {
        memo[personId]?.let { return it }
        if (personId in stack) {
            val idx = path.indexOf(personId)
            if (idx >= 0) {
                while (path.size > idx + 1) path.removeAt(path.lastIndex)
            }
            path.add(personId)
            return true
        }
        stack.add(personId)
        path.add(personId)
        for (parent in graph.parentsOf(personId)) {
            if (hasCycleFrom(parent.id, graph, path, stack, memo)) {
                return true
            }
        }
        path.removeAt(path.lastIndex)
        stack.remove(personId)
        memo[personId] = false
        return false
    }
}
