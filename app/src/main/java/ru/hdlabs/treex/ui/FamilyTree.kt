package ru.hdlabs.treex.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.TreeLayout
import ru.hdlabs.treex.domain.TreePlacement
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.isVacantSlot
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
    onOpenDetail: (PersonEntity) -> Unit,
    onHoldLink: (String) -> Unit,
) {
    val placement = remember(people, unions, openLinks) { TreeLayout.place(people, unions, openLinks) }
    val byId = remember(people) { people.associateBy { it.id } }
    val density = LocalDensity.current
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    var refit by remember { mutableIntStateOf(0) }
    val ids = remember(people) { people.map { it.id }.sorted().joinToString(",") }
    val focusSpring = spring<Float>(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)

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

        suspend fun fitAll() {
            scaleAnim.snapTo(fit)
            offsetXAnim.snapTo((viewW - contentW * fit) / 2f)
            offsetYAnim.snapTo((viewH - contentH * fit) / 2f)
        }

        suspend fun focusCard(cardId: String, zoomIn: Boolean) {
            if (viewW <= 1f || viewH <= 1f) return
            val card = placement.cards.find { it.id == cardId } ?: return
            val targetScale = if (zoomIn) {
                min(fit * 1.45f, 1.7f).coerceAtLeast(fit)
            } else {
                fit
            }
            val centerX = with(density) { (card.left + TreeLayout.CARD_W / 2f).dp.toPx() }
            val centerY = with(density) { (card.top + card.height / 2f).dp.toPx() }
            val targetOffsetX = viewW / 2f - centerX * targetScale
            val targetOffsetY = viewH / 2f - centerY * targetScale
            coroutineScope {
                launch { scaleAnim.animateTo(targetScale, focusSpring) }
                launch { offsetXAnim.animateTo(targetOffsetX, focusSpring) }
                launch { offsetYAnim.animateTo(targetOffsetY, focusSpring) }
            }
        }

        LaunchedEffect(ids, viewW, viewH, refit) {
            if (viewW <= 1f || viewH <= 1f) return@LaunchedEffect
            fitAll()
        }
        LaunchedEffect(selectedId, ids, centerToken) {
            if (selectedId.isBlank()) return@LaunchedEffect
            focusCard(selectedId, zoomIn = true)
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .treeTransform { zoom, pan, centroid ->
                    val minScale = min(0.35f, fit).coerceAtLeast(0.12f)
                    val next = (scaleAnim.value * zoom).coerceIn(minScale, 2.6f)
                    val applied = if (scaleAnim.value == 0f) 1f else next / scaleAnim.value
                    val current = Offset(offsetXAnim.value, offsetYAnim.value)
                    val shifted = centroid - (centroid - current) * applied + pan
                    gestureScope.launch {
                        scaleAnim.snapTo(next)
                        offsetXAnim.snapTo(shifted.x)
                        offsetYAnim.snapTo(shifted.y)
                    }
                },
        ) {
            Box(
                Modifier.graphicsLayer {
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                TreeLinks(placement, Modifier.width(placement.width.dp).height(placement.height.dp))
                placement.cards.forEach { card ->
                    val person = byId[card.id] ?: return@forEach
                    val selected = person.id == selectedId
                    TreeNode(
                        person = person,
                        selected = selected,
                        fresh = person.id == freshId,
                        height = card.height,
                        modifier = Modifier
                            .offset(card.left.dp, card.top.dp)
                            .zIndex(if (selected) 2f else 0f),
                        onSelect = { onSelect(person) },
                        onOpenDetail = { onOpenDetail(person) },
                    )
                }
                placement.knots.forEach { knot ->
                    LinkHandle(
                        active = knot.linkId == pendingLinkId,
                        modifier = Modifier
                            .offset((knot.x - 22f).dp, (knot.y - 22f).dp)
                            .zIndex(1f),
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
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
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
            val width = if (open) 2f else 2.4.dp.toPx()
            drawPath(path, color.copy(alpha = 0.12f), style = Stroke(width + 6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                color.copy(alpha = 0.82f),
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(if (open) 10f else 18f, if (open) 14f else 16f), phase),
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
            drawCircle(HdOrange.copy(alpha = 0.85f), 4.5.dp.toPx(), Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f - lift))
        }
    }
}

@Composable
private fun LinkHandle(active: Boolean, modifier: Modifier = Modifier, onHold: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "linkPulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "linkPulseScale",
    )
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                val scale = if (active) pulse else 1f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(onHold) {
                detectTapGestures(
                    onLongPress = { onHold() },
                    onTap = { onHold() },
                )
            }
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(HdOrange.copy(alpha = if (active) 0.45f else 0.22f), radius = r + 4.dp.toPx())
                drawCircle(if (active) HdOrange else HdOrange.copy(alpha = 0.55f), radius = r)
                drawCircle(Color.White, radius = r / 4.5f)
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
    onSelect: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val lift by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "cardLift",
    )
    GenderFrame(
        gender = if (person.isVacantSlot()) "u" else person.gender,
        emphasized = selected || fresh,
        vacant = person.isVacantSlot(),
        modifier = modifier
            .width(TreeLayout.CARD_W.dp)
            .height(height.dp)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                transformOrigin = TransformOrigin.Center
            },
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .pointerInput(person.id) {
                    detectTapGestures(
                        onTap = { onSelect() },
                        onDoubleTap = { onOpenDetail() },
                    )
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (person.isVacantSlot()) {
                    HdPanelElevated.copy(alpha = 0.72f)
                } else {
                    HdPanelElevated.copy(alpha = 0.96f)
                },
            ),
            border = BorderStroke(0.dp, Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 10.dp else 2.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (person.isVacantSlot()) {
                    Text("+", color = HdOrange, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Text(
                        "Пустое место",
                        color = HdTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Тап — действия",
                        color = HdTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    PersonAvatar(person, 36.dp)
                    Text(
                        person.fullName(),
                        color = HdTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val years = person.lifeLine()
                    if (years.isNotBlank()) {
                        Text(years, color = HdTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun GenderFrame(
    gender: String,
    emphasized: Boolean,
    vacant: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val phase by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (gender == "f") 5200 else 6200, easing = LinearEasing),
        ),
        label = "phase",
    )
    val glow by shimmer.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val colors = when {
        vacant -> listOf(HdOrange.copy(alpha = 0.4f), HdOrange, HdTextSecondary.copy(alpha = 0.5f))
        gender == "f" -> listOf(
            Color(0xFFFFD6E3),
            Color(0xFFFF6B9D),
            Color(0xFFFF1744),
            Color(0xFFC2185B),
        )
        gender == "m" -> listOf(
            Color(0xFFB8D9FF),
            Color(0xFF1E88E5),
            Color(0xFF0D47A1),
            Color(0xFF1565C0),
        )
        else -> listOf(HdBlue.copy(alpha = 0.5f), HdOrange, HdBlue.copy(alpha = 0.5f))
    }
    val stroke = with(androidx.compose.ui.platform.LocalDensity.current) {
        if (emphasized) 2.8.dp.toPx() else 2.2.dp.toPx()
    }
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    Box(
        modifier = modifier.drawBehind {
            val inset = stroke / 2f
            val round = RoundRect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
                CornerRadius(corner, corner),
            )
            val path = Path().apply { addRoundRect(round) }
            val alpha = if (emphasized) glow.coerceIn(0.4f, 0.72f) else glow.coerceIn(0.2f, 0.38f)
            drawPath(
                path,
                colors[1].copy(alpha = alpha * 0.55f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val dashMain = floatArrayOf(stroke * 2.8f, stroke * 6.5f)
            drawPath(
                path,
                colors[2].copy(alpha = alpha * 0.95f),
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(dashMain, phase),
                ),
            )
            drawPath(
                path,
                colors[0].copy(alpha = alpha * 0.7f),
                style = Stroke(
                    width = stroke * 0.75f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 1.2f, stroke * 9f), phase * 1.35f),
                ),
            )
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
