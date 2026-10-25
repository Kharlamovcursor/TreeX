package ru.hdlabs.treex.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.TreeLayout
import ru.hdlabs.treex.domain.TreePlacement
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.lifeLine
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary
import kotlin.math.abs
import kotlin.math.min

@Composable
fun FamilyTree(
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
    selectedId: String,
    freshId: String,
    centerToken: Int,
    modifier: Modifier = Modifier,
    onSelect: (PersonEntity) -> Unit,
) {
    val placement = remember(people, unions) { TreeLayout.place(people, unions) }
    val byId = remember(people) { people.associateBy { it.id } }
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var refit by remember { mutableIntStateOf(0) }
    val ids = remember(people) { people.map { it.id }.sorted().joinToString(",") }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val contentW = with(density) { placement.width.dp.toPx() }
        val contentH = with(density) { placement.height.dp.toPx() }
        val fit = if (contentW <= 1f || contentH <= 1f || viewW <= 1f || viewH <= 1f) {
            1f
        } else {
            min(viewW / contentW, viewH / contentH).coerceIn(0.2f, 1f)
        }

        LaunchedEffect(ids, viewW, viewH, refit) {
            if (viewW <= 1f || viewH <= 1f) return@LaunchedEffect
            scale = fit
            offset = Offset((viewW - contentW * fit) / 2f, (viewH - contentH * fit) / 2f)
        }
        LaunchedEffect(centerToken) {
            if (centerToken == 0) return@LaunchedEffect
            val card = placement.cards.find { it.id == selectedId } ?: return@LaunchedEffect
            val next = 1f.coerceAtLeast(fit).coerceAtMost(1.4f)
            val centerX = with(density) { (card.left + TreeLayout.CARD_W / 2f).dp.toPx() }
            val centerY = with(density) { (card.top + TreeLayout.CARD_H / 2f).dp.toPx() }
            scale = next
            offset = Offset(viewW / 2f - centerX * next, viewH / 2f - centerY * next)
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .treeTransform { zoom, pan, centroid ->
                    val minScale = min(0.35f, fit).coerceAtLeast(0.12f)
                    val next = (scale * zoom).coerceIn(minScale, 2.6f)
                    val applied = if (scale == 0f) 1f else next / scale
                    offset = centroid - (centroid - offset) * applied + pan
                    scale = next
                },
        ) {
            Box(
                Modifier.graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                TreeLinks(placement, Modifier.width(placement.width.dp).height(placement.height.dp))
                placement.cards.forEach { card ->
                    val person = byId[card.id] ?: return@forEach
                    TreeNode(
                        person = person,
                        selected = person.id == selectedId,
                        fresh = person.id == freshId,
                        modifier = Modifier.offset(card.left.dp, card.top.dp),
                        onClick = { onSelect(person) },
                    )
                }
            }
        }
        TextButton(
            onClick = { refit += 1 },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text("Всё древо", color = HdOrange, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TreeLinks(placement: TreePlacement, modifier: Modifier = Modifier) {
    val cards = placement.cards.associateBy { it.id }
    Canvas(modifier) {
        val cardW = TreeLayout.CARD_W.dp.toPx()
        val cardH = TreeLayout.CARD_H.dp.toPx()
        fun rectOf(id: String): Rect? {
            val card = cards[id] ?: return null
            val left = card.left.dp.toPx()
            val top = card.top.dp.toPx()
            return Rect(left, top, left + cardW, top + cardH)
        }
        val childStroke = 3.5.dp.toPx()
        val spouseStroke = 4.5.dp.toPx()
        placement.childGroups.forEach { group ->
            val parents = group.parentIds.mapNotNull { rectOf(it) }
            val children = group.childIds.mapNotNull { rectOf(it) }
            if (parents.isEmpty() || children.isEmpty()) return@forEach
            val originY = parents.maxOf { it.bottom }
            val originX = parents.map { it.center.x }.average().toFloat()
            parents.forEach { parent ->
                drawLine(
                    HdBlue,
                    Offset(parent.center.x, parent.bottom),
                    Offset(parent.center.x, originY),
                    childStroke,
                    StrokeCap.Round,
                )
            }
            if (parents.size > 1) {
                drawLine(
                    HdBlue,
                    Offset(parents.minOf { it.center.x }, originY),
                    Offset(parents.maxOf { it.center.x }, originY),
                    childStroke,
                    StrokeCap.Round,
                )
            }
            val railY = originY + (children.minOf { it.top } - originY) * 0.5f
            drawLine(HdBlue, Offset(originX, originY), Offset(originX, railY), childStroke, StrokeCap.Round)
            drawLine(
                HdBlue,
                Offset(children.minOf { it.center.x }, railY),
                Offset(children.maxOf { it.center.x }, railY),
                childStroke,
                StrokeCap.Round,
            )
            children.forEach { child ->
                drawLine(
                    HdBlue,
                    Offset(child.center.x, railY),
                    Offset(child.center.x, child.top),
                    childStroke,
                    StrokeCap.Round,
                )
            }
        }
        placement.spouseLinks.forEach { (first, second) ->
            val start = rectOf(first) ?: return@forEach
            val end = rectOf(second) ?: return@forEach
            val left = if (start.left <= end.left) start else end
            val right = if (start.left <= end.left) end else start
            val gap = right.left - left.right
            val sameRow = abs(left.top - right.top) < 2.dp.toPx()
            if (sameRow && gap < cardW * 0.5f) {
                val y = (left.center.y + right.center.y) / 2f
                drawLine(HdOrange, Offset(left.right, y), Offset(right.left, y), spouseStroke, StrokeCap.Round)
                drawCircle(HdOrange, 6.dp.toPx(), Offset((left.right + right.left) / 2f, y))
            } else {
                val y = minOf(left.top, right.top) - 16.dp.toPx()
                drawLine(HdOrange, Offset(left.center.x, left.top), Offset(left.center.x, y), spouseStroke, StrokeCap.Round)
                drawLine(HdOrange, Offset(left.center.x, y), Offset(right.center.x, y), spouseStroke, StrokeCap.Round)
                drawLine(HdOrange, Offset(right.center.x, y), Offset(right.center.x, right.top), spouseStroke, StrokeCap.Round)
                drawCircle(HdOrange, 6.dp.toPx(), Offset((left.center.x + right.center.x) / 2f, y))
            }
        }
    }
}

@Composable
private fun TreeNode(
    person: PersonEntity,
    selected: Boolean,
    fresh: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .width(TreeLayout.CARD_W.dp)
            .height(TreeLayout.CARD_H.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.96f)),
        border = BorderStroke(
            if (selected || fresh) 2.dp else 1.dp,
            if (fresh) HdOrange else if (selected) HdBlue else HdBlue.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PersonAvatar(person, 36.dp)
            Text(
                person.fullName(),
                color = HdTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            val years = person.lifeLine()
            if (years.isNotBlank()) {
                Text(
                    years,
                    color = HdTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun Modifier.treeTransform(
    onGesture: (zoom: Float, pan: Offset, centroid: Offset) -> Unit,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        var panning = false
        var accumulated = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid()
                if (abs(zoom - 1f) > 0.001f || pan.getDistance() > 0.5f) {
                    onGesture(zoom, pan, centroid)
                    event.changes.forEach { change -> change.consume() }
                }
                panning = true
            } else {
                val change = pressed.first()
                val delta = change.position - change.previousPosition
                if (!panning) {
                    accumulated += delta
                    if (accumulated.getDistance() > slop) {
                        panning = true
                        onGesture(1f, accumulated, change.position)
                        change.consume()
                    }
                } else {
                    onGesture(1f, delta, change.position)
                    change.consume()
                }
            }
        }
    }
}
