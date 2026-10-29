package ru.hdlabs.treex.data.kinship

import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyValidator
import ru.hdlabs.treex.domain.graph.ValidationIssue
import ru.hdlabs.treex.domain.graph.ValidationMode
import ru.hdlabs.treex.domain.graph.ValidationResult

data class SaveValidationOutcome(
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
)

object FamilyMutationValidator {

    private val genealogyValidator = GenealogyValidator()

    fun validateForSave(before: GenealogyGraph, after: GenealogyGraph): SaveValidationOutcome {
        val result = genealogyValidator.validate(after, ValidationMode.Save)
        return SaveValidationOutcome(errors = result.errors, warnings = result.warnings)
    }

    fun findPartnerFamilies(graph: GenealogyGraph, personId: String): List<Family> =
        graph.familiesOf(personId)

    fun hasDuplicateSpouseFamily(graph: GenealogyGraph, a: String, b: String): Boolean =
        graph.familiesOf(a).any { fam ->
            val partners = setOfNotNull(fam.partnerAId, fam.partnerBId)
            partners.contains(a) && partners.contains(b)
        }

    fun childTargetFamilies(graph: GenealogyGraph, anchorId: String, coParentId: String?): ChildTargetResult {
        val partnerFamilies = findPartnerFamilies(graph, anchorId)
        return when {
            coParentId != null -> {
                val match = partnerFamilies.filter { coParentId in setOfNotNull(it.partnerAId, it.partnerBId) }
                when (match.size) {
                    0 -> ChildTargetResult.NeedNewFamily
                    1 -> ChildTargetResult.UseFamily(match.single().id)
                    else -> ChildTargetResult.Ambiguous
                }
            }
            partnerFamilies.size == 1 -> ChildTargetResult.UseFamily(partnerFamilies.single().id)
            partnerFamilies.isEmpty() -> ChildTargetResult.NeedNewFamily
            else -> ChildTargetResult.Ambiguous
        }
    }

    fun parentFamilyForSibling(graph: GenealogyGraph, anchorId: String): ParentFamilyResult {
        val asChild = graph.familyWhereChild(anchorId)
        if (asChild != null) return ParentFamilyResult.UseFamily(asChild.id)
        val parentIds = graph.parentsOf(anchorId).map { it.id }
        if (parentIds.isEmpty()) return ParentFamilyResult.NoParentFamily
        val families = parentIds.flatMap { graph.familiesOf(it) }.distinctBy { it.id }
            .filter { fam -> anchorId in fam.childrenIds || parentIds.any { it in setOfNotNull(fam.partnerAId, fam.partnerBId) } }
        return when (families.size) {
            0 -> ParentFamilyResult.NoParentFamily
            1 -> ParentFamilyResult.UseFamily(families.single().id)
            else -> ParentFamilyResult.Ambiguous
        }
    }

    sealed interface ChildTargetResult {
        data class UseFamily(val familyId: String) : ChildTargetResult
        data object NeedNewFamily : ChildTargetResult
        data object Ambiguous : ChildTargetResult
    }

    sealed interface ParentFamilyResult {
        data class UseFamily(val familyId: String) : ParentFamilyResult
        data object NoParentFamily : ParentFamilyResult
        data object Ambiguous : ParentFamilyResult
    }
}
