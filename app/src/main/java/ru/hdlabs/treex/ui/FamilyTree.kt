package ru.hdlabs.treex.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.OpenLink
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
    openLinks: List<OpenLink>,
    selectedId: String,
    freshId: String,
    centerToken: Int,
    pendingLinkId: String?,
    modifier: Modifier = Modifier,
    onSelect: (PersonEntity) -> Unit,
    onHoldLink: (String) -> Unit,
) {
    val placement = remember(people, unions, openLinks) { TreeLayout.place(people, unions, openLinks) }
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
            val centerY = with(density) { (card.top + card.height / 2f).dp.toPx() }
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
                        height = card.height,
                        modifier = Modifier.offset(card.left.dp, card.top.dp),
                        onClick = { onSelect(person) },
                    )
                }
                placement.knots.forEach { knot ->
                    LinkHandle(
                        active = knot.linkId == pendingLinkId,
                        modifier = Modifier.offset((knot.x - 18f).dp, (knot.y - 18f).dp),
                        onHold = { onHoldLink(knot.linkId) },
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
    val phase by rememberInfiniteTransition(label = "links").animateFloat(
        initialValue = 0f,
        targetValue = 64f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "dash",
    )
    Canvas(modifier) {
        val cardW = TreeLayout.CARD_W.dp.toPx()
        fun rectOf(id: String): Rect? {
            val card = cards[id] ?: return null
            val left = card.left.dp.toPx()
            val top = card.top.dp.toPx()
            return Rect(left, top, left + cardW, top + card.height.dp.toPx())
        }
        fun drawElegant(path: Path, color: Color, open: Boolean) {
            val width = if (open) 2.2.dp.toPx() else 2.8.dp.toPx()
            drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width + 7.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                color,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(if (open) 8f else 16f, if (open) 12f else 14f), phase),
                ),
            )
        }
        placement.childGroups.forEach { group ->
            val parents = group.parentIds.mapNotNull { rectOf(it) }
            val children = group.childIds.mapNotNull { rectOf(it) }
            if (parents.isEmpty() || children.isEmpty()) return@forEach
            val origin = Offset(parents.map { it.center.x }.average().toFloat(), parents.maxOf { it.bottom })
            parents.forEach { parent ->
                val path = Path().apply {
                    moveTo(parent.center.x, parent.bottom)
                    quadraticBezierTo(parent.center.x, origin.y, origin.x, origin.y)
                }
                drawElegant(path, if (group.open) HdOrange else HdBlue, group.open)
            }
            children.forEach { child ->
                val path = Path().apply {
                    moveTo(origin.x, origin.y)
                    val bend = (child.top - origin.y).coerceAtLeast(24.dp.toPx())
                    cubicTo(origin.x, origin.y + bend * 0.45f, child.center.x, child.top - bend * 0.45f, child.center.x, child.top)
                }
                drawElegant(path, if (group.open) HdOrange else HdBlue, group.open)
            }
        }
        placement.spouseLinks.forEach { (first, second) ->
            val start = rectOf(first) ?: return@forEach
            val end = rectOf(second) ?: return@forEach
            val left = if (start.left <= end.left) start else end
            val right = if (start.left <= end.left) end else start
            val from = Offset(left.right, left.center.y)
            val to = Offset(right.left, right.center.y)
            val lift = if (abs(left.top - right.top) < 2.dp.toPx() && right.left - left.right < cardW) 18.dp.toPx() else 0f
            val path = Path().apply {
                moveTo(from.x, from.y)
                quadraticBezierTo((from.x + to.x) / 2f, (from.y + to.y) / 2f - lift, to.x, to.y)
            }
            drawElegant(path, HdOrange, open = false)
            drawCircle(HdOrange, 5.dp.toPx(), Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f - lift))
        }
    }
}

@Composable
private fun LinkHandle(active: Boolean, modifier: Modifier = Modifier, onHold: () -> Unit) {
    Box(
        modifier
            .size(36.dp)
            .pointerInput(onHold) { detectTapGestures(onLongPress = { onHold() }, onTap = { onHold() }) }
            .drawBehind {
                drawCircle(if (active) HdOrange else HdOrange.copy(alpha = 0.35f), radius = size.minDimension / 2f)
                drawCircle(Color.White, radius = size.minDimension / 5f)
            },
    )
}

@Composable
private fun TreeNode(
    person: PersonEntity,
    selected: Boolean,
    fresh: Boolean,
    height: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GenderFrame(person.gender, selected || fresh, modifier.width(TreeLayout.CARD_W.dp).height(height.dp)) {
        Card(
            modifier = Modifier.fillMaxSize().padding(3.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.96f)),
            border = BorderStroke(0.dp, Color.Transparent),
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
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
                val years = person.lifeLine()
                if (years.isNotBlank()) {
                    Text(years, color = HdTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun GenderFrame(
    gender: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val turn by rememberInfiniteTransition(label = "frame").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (gender == "f") 2600 else 3200, easing = LinearEasing)),
        label = "turn",
    )
    val colors = when (gender) {
        "f" -> listOf(Color(0xFFFFB3C7), Color(0xFFFF4D8D), Color(0xFFFF1744), Color(0xFFE91E63), Color(0xFFFFB3C7))
        "m" -> listOf(Color(0xFFB3D4FF), Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF00B4FF), Color(0xFFB3D4FF))
        else -> listOf(HdBlue, HdOrange, HdBlue)
    }
    Box(
        modifier.drawBehind {
            rotate(turn, pivot = center) {
                drawRoundRect(
                    brush = Brush.sweepGradient(colors),
                    style = Stroke(width = if (emphasized) 4.dp.toPx() else 3.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                )
            }
        },
    ) {
        content()
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
