package ru.hdlabs.treex.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.hdlabs.treex.domain.layout.EdgeType
import ru.hdlabs.treex.ui.tree.TreeUiState.Ready
import kotlin.math.roundToInt

@Composable
fun TreeCanvas(
    state: Ready,
    modifier: Modifier = Modifier,
    onPersonClick: (String) -> Unit,
    onSlotClick: (OpenLinkSlotState) -> Unit,
    onTransform: (scale: Float, offsetX: Float, offsetY: Float, viewW: Float, viewH: Float) -> Unit,
    paddingPx: Float,
) {
    val density = LocalDensity.current
    val nodeMap = remember(state.layout) { state.layout.nodes.associateBy { it.personId } }
    val blockMap = remember(state.layout) { state.layout.familyBlocks.associateBy { it.familyId } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        TreeTransformLayer(
            targetTransform = state.transform,
            contentBounds = state.layout.bounds,
            viewWidth = viewW,
            viewHeight = viewH,
            paddingPx = paddingPx,
            onTransformCommitted = { t ->
                onTransform(t.scale, t.offsetX, t.offsetY, viewW, viewH)
            },
            onViewportTap = { viewportPoint, transform ->
                TreeLayoutHitTest.hitOpenLinkSlot(viewportPoint, transform, state.openLinks)?.let {
                    onSlotClick(it)
                    return@TreeTransformLayer
                }
                TreeLayoutHitTest.hitPersonId(viewportPoint, transform, state.layout.nodes)?.let {
                    onPersonClick(it)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            TreeLayoutContent(
                state = state,
                nodeMap = nodeMap,
                blockMap = blockMap,
                density = density,
            )
        }
    }
}

@Composable
private fun TreeLayoutContent(
    state: Ready,
    nodeMap: Map<String, ru.hdlabs.treex.domain.layout.LayoutNode>,
    blockMap: Map<String, ru.hdlabs.treex.domain.layout.FamilyBlock>,
    density: androidx.compose.ui.unit.Density,
) {
    Canvas(Modifier.fillMaxSize()) {
        state.layout.familyBlocks.forEach { block ->
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFF6B8CAE),
                radius = 6f,
                center = Offset(block.centerX, block.bottomY),
            )
        }
        state.layout.edges.forEach { edge ->
            val points = EdgePainter.pointsForEdge(edge, nodeMap, blockMap)
            if (points.size < 2) return@forEach
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
            val dash = if (edge.type == EdgeType.LEGACY_PARENT_CHILD) {
                PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            } else {
                null
            }
            drawPath(
                path,
                color = androidx.compose.ui.graphics.Color(0xFF8FAFC8),
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = dash,
                ),
            )
        }
    }
    state.layout.nodes.forEach { node ->
        val person = state.graph.person(node.personId) ?: return@forEach
        PersonCard(
            person = person,
            selected = state.selectedPersonId == node.personId,
            modifier = Modifier
                .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
                .size(
                    with(density) { node.width.toDp() },
                    with(density) { node.height.toDp() },
                ),
        )
    }
    state.openLinks.forEach { slot ->
        OpenLinkSlot(
            slot = slot,
            modifier = Modifier
                .offset { IntOffset(slot.x.roundToInt(), slot.y.roundToInt()) }
                .size(
                    with(density) { slot.width.toDp() },
                    with(density) { slot.height.toDp() },
                ),
        )
    }
}
