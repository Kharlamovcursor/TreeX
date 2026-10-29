package ru.hdlabs.treex.ui.tree

import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.layout.TreeLayout

sealed interface TreeUiState {
    data object Loading : TreeUiState

    data class Ready(
        val layout: TreeLayout,
        val graph: GenealogyGraph,
        val selectedPersonId: String? = null,
        val openLinks: List<OpenLinkSlotState> = emptyList(),
        val transform: CanvasTransform = CanvasTransform(),
        val sheet: BottomSheetState? = null,
        val error: String? = null,
    ) : TreeUiState

    data class Error(val message: String, val recoverable: Boolean = true) : TreeUiState
}

data class CanvasTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

sealed interface BottomSheetState {
    data class PersonDetails(val personId: String) : BottomSheetState
    data class AddRelative(val anchorPersonId: String, val kind: RelativeKind) : BottomSheetState
}

enum class RelativeKind { PARENT, SPOUSE, CHILD }
