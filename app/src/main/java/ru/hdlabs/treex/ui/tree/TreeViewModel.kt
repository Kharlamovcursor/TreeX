package ru.hdlabs.treex.ui.tree

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hdlabs.treex.data.TreeRepository
import ru.hdlabs.treex.domain.graph.SaveBlockedException
import java.io.File

class TreeViewModel(
    private val treeRepository: TreeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TreeUiState>(TreeUiState.Loading)
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            treeRepository.observe().collect { loadInternal(preserveTransform = true) }
        }
    }

    fun reload() {
        viewModelScope.launch { loadInternal(preserveTransform = false) }
    }

    private suspend fun loadInternal(preserveTransform: Boolean) {
        val prevTransform = (_state.value as? TreeUiState.Ready)?.transform
        if (!preserveTransform) {
            _state.value = TreeUiState.Loading
    /** Writes current [state] to app-private storage (debug builds). */
    fun dumpDiagnostics(context: Context): File =
        TreeDiagnosticsDump.dumpDiagnostics(context, _state.value)

        }
        try {
            val ready = TreeReadyBuilder.build(treeRepository.buildGraphSnapshot())
            _state.value = ready.copy(
                transform = if (preserveTransform && prevTransform != null) prevTransform else ready.transform,
            )
        } catch (e: SaveBlockedException) {
            _state.value = TreeUiState.Error(
                message = e.issues.joinToString { it.toString() },
                recoverable = true,
            )
        } catch (e: Exception) {
            _state.value = TreeUiState.Error(message = e.message ?: "Не удалось построить древо")
        }
    }

    fun onPersonClick(id: String) {
        updateReady {
            it.copy(
                selectedPersonId = id,
                sheet = BottomSheetState.PersonDetails(id),
            )
        }
    }

    fun onSlotClick(slot: OpenLinkSlotState) {
        updateReady {
            it.copy(
                sheet = BottomSheetState.AddRelative(slot.anchorPersonId, slot.kind),
            )
        }
    }

    fun dismissSheet() {
        updateReady { it.copy(sheet = null) }
    }

    fun onTransform(scale: Float, offsetX: Float, offsetY: Float, viewW: Float, viewH: Float) {
        updateReady { ready ->
            val t = Gestures.clampTransform(
                CanvasTransform(scale, offsetX, offsetY),
                ready.layout.bounds,
                viewW,
                viewH,
            )
            ready.copy(transform = t)
        }
    }

    fun fitToScreen(viewW: Float, viewH: Float) {
        updateReady { ready ->
            ready.copy(transform = Gestures.fitToScreen(ready.layout.bounds, viewW, viewH))
        }
    }

    fun requestAddRelative(anchorPersonId: String, kind: RelativeKind) {
        updateReady {
            it.copy(sheet = BottomSheetState.AddRelative(anchorPersonId, kind))
        }
    }

    private inline fun updateReady(block: (TreeUiState.Ready) -> TreeUiState.Ready) {
        val cur = _state.value
        if (cur is TreeUiState.Ready) {
            _state.update { block(cur) }
        }
    }
}
