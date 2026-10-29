package ru.hdlabs.treex.ui.tree

import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyValidator
import ru.hdlabs.treex.domain.graph.SaveBlockedException
import ru.hdlabs.treex.domain.graph.ValidationIssue
import ru.hdlabs.treex.domain.graph.ValidationMode
import ru.hdlabs.treex.domain.layout.LayoutValidator
import ru.hdlabs.treex.domain.layout.SugiyamaLayout

/** Builds [TreeUiState.Ready] from an in-memory graph (shared by [TreeViewModel] and unit tests). */
object TreeReadyBuilder {

    private val graphValidator = GenealogyValidator()

    fun build(graph: GenealogyGraph): TreeUiState.Ready {
        val graphResult = graphValidator.validate(graph, ValidationMode.Read)
        val blocking = graphResult.errors.filter {
            it !is ValidationIssue.DanglingChildRef && it !is ValidationIssue.DanglingPartnerRef &&
                it !is ValidationIssue.ChildInMultipleFamilies
        }
        if (blocking.isNotEmpty()) {
            throw SaveBlockedException(blocking)
        }
        val layout = SugiyamaLayout.layout(graph)
        val layoutResult = LayoutValidator.validate(layout, graph)
        if (layoutResult.errors.isNotEmpty()) {
            throw java.lang.IllegalStateException(
                layoutResult.errors.joinToString(separator = "; ") { issue -> issue.toString() },
            )
        }
        val warnLines: List<String> =
            graphResult.warnings.map { issue -> issue.toString() } +
                layoutResult.warnings.map { issue -> issue.toString() }
        val warnText = if (warnLines.isEmpty()) null else warnLines.joinToString("\n")
        return TreeUiState.Ready(
            layout = layout,
            graph = graph,
            openLinks = OpenLinkSlots.fromLayout(graph, layout),
            error = warnText,
        )
    }
}
