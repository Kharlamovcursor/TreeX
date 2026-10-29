package ru.hdlabs.treex.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.isVacantSlot
import ru.hdlabs.treex.domain.yearsLabel
import ru.hdlabs.treex.relatives.RelativesTreeConverter
import ru.hdlabs.treex.relatives.RelativesTreeData
import ru.hdlabs.treex.relatives.RelativesTreeLayout
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

/**
 * Genealogy tree: relatives-tree layout when available; [FamilyTree] shown immediately (no blocking spinner).
 */
@Composable
fun RelativesTreeView(
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
    openLinks: List<OpenLink>,
    selectedId: String,
    freshId: String,
    centerToken: Int,
    pendingLinkId: String?,
    detachedBranchIds: Set<String>,
    homePersonId: String,
    compactChrome: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (PersonEntity) -> Unit,
    onOpenDetail: (PersonEntity) -> Unit,
    onHoldLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val engine = remember { (context.applicationContext as TreeXApplication).container.relativesTreeEngine }
    var layout by remember { mutableStateOf<RelativesTreeData?>(null) }
    val rootId = homePersonId.ifBlank { people.firstOrNull { !it.vacant }?.id.orEmpty() }
    val peopleKey = remember(people, unions) {
        people.joinToString { it.id + it.updatedAtMillis } + unions.joinToString { it.id + it.childIds }
    }

    LaunchedEffect(peopleKey, rootId, centerToken) {
        layout = null
        if (people.none { !it.vacant }) return@LaunchedEffect
        if (!engine.isAttached) return@LaunchedEffect
        layout = runCatching {
            val nodes = RelativesTreeConverter.toGraphNodes(people, unions)
            require(nodes.isNotEmpty())
            val ids = nodes.map { it.id }.toSet()
            val effectiveRoot = rootId.takeIf { it in ids } ?: nodes.first().id
            val tree = engine.calcTree(nodes, effectiveRoot)
            val onCanvas = tree.nodes.map { it.id }.toSet()
            val missing = ids.count { it !in onCanvas }
            if (missing > 0) {
                AppEventLog.info(
                    "relatives-tree",
                    "$missing чел. не в раскладке relatives-tree (корень=$effectiveRoot) — библиотека показывает только связанное с корнем поддерево",
                )
            }
            tree
        }.onFailure { error ->
            AppEventLog.error("relatives-tree", "Layout calc failed, using FamilyTree", error)
        }.getOrNull()
    }

    if (layout != null) {
        RelativesTreeCanvas(
            people = people,
            layout = layout!!,
            selectedId = selectedId,
            freshId = freshId,
            centerToken = centerToken,
            homePersonId = rootId,
            compactChrome = compactChrome,
            detachedBranchIds = detachedBranchIds,
            modifier = modifier,
            onSelect = onSelect,
            onOpenDetail = onOpenDetail,
        )
    } else {
        FamilyTree(
            people = people,
            unions = unions,
            openLinks = openLinks,
            selectedId = selectedId,
            freshId = freshId,
            centerToken = centerToken,
            pendingLinkId = pendingLinkId,
            detachedBranchIds = detachedBranchIds,
            homePersonId = homePersonId,
            compactChrome = compactChrome,
            modifier = modifier,
            onSelect = onSelect,
            onOpenDetail = onOpenDetail,
            onHoldLink = onHoldLink,
        )
    }
}

@Composable
private fun RelativesTreeCanvas(
    people: List<PersonEntity>,
    layout: RelativesTreeData,
    selectedId: String,
    freshId: String,
    centerToken: Int,
    homePersonId: String,
    compactChrome: Boolean,
    detachedBranchIds: Set<String>,
    modifier: Modifier = Modifier,
    onSelect: (PersonEntity) -> Unit,
    onOpenDetail: (PersonEntity) -> Unit,
) {
    val byId = remember(people) { people.associateBy { it.id } }
    val density = LocalDensity.current
    val grid = RelativesTreeLayout.GRID_PX
    val cardPx = RelativesTreeLayout.cardSizePx
    val widthPx = layout.canvas.width * grid
    val heightPx = layout.canvas.height * grid
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val cardDp = with(density) { cardPx.toDp() }
    val nodesById = remember(layout) {
        layout.nodes.groupBy { it.id }.mapValues { (_, list) -> list.first() }
    }
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    var refit by remember { mutableIntStateOf(0) }
    val focusSpring = spring<Float>(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val contentW = with(density) { widthDp.toPx() }
        val contentH = with(density) { heightDp.toPx() }
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
            val node = nodesById[cardId] ?: return
            val targetScale = if (zoomIn) {
                min(fit * 1.45f, 1.7f).coerceAtLeast(fit)
            } else {
                fit
            }
            val centerX = node.left * grid + cardPx / 2f
            val centerY = node.top * grid + cardPx / 2f
            val targetOffsetX = viewW / 2f - centerX * targetScale
            val targetOffsetY = viewH / 2f - centerY * targetScale
            coroutineScope {
                launch { scaleAnim.animateTo(targetScale, focusSpring) }
                launch { offsetXAnim.animateTo(targetOffsetX, focusSpring) }
                launch { offsetYAnim.animateTo(targetOffsetY, focusSpring) }
            }
        }

        LaunchedEffect(layout, viewW, viewH, refit) {
            if (viewW <= 1f || viewH <= 1f) return@LaunchedEffect
            fitAll()
        }
        val focusId = selectedId.ifBlank { homePersonId }
        LaunchedEffect(focusId, layout, centerToken) {
            if (focusId.isBlank()) return@LaunchedEffect
            focusCard(focusId, zoomIn = selectedId.isNotBlank())
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .treeViewportTransform { zoom, pan, centroid ->
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
                Modifier
                    .graphicsLayer {
                        translationX = offsetXAnim.value
                        translationY = offsetYAnim.value
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .width(widthDp)
                    .height(heightDp),
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .zIndex(0f),
                ) {
                    layout.connectors.forEach { connector ->
                        if (connector.size < 4) return@forEach
                        val x1 = connector[0].toFloat() * grid
                        val y1 = connector[1].toFloat() * grid
                        val x2 = connector[2].toFloat() * grid
                        val y2 = connector[3].toFloat() * grid
                        drawLine(
                            color = Color(0xFF6B8CAE),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                nodesById.forEach { (nodeId, node) ->
                    val person = byId[nodeId] ?: return@forEach
                    val leftDp = with(density) { (node.left * grid).toDp() }
                    val topDp = with(density) { (node.top * grid).toDp() }
                    RelativesPersonCard(
                        person = person,
                        selected = person.id == selectedId,
                        fresh = person.id == freshId,
                        detached = person.id in detachedBranchIds,
                        modifier = Modifier
                            .offset(leftDp, topDp)
                            .width(cardDp)
                            .height(cardDp)
                            .zIndex(if (person.id == selectedId) 2f else 1f),
                        onSelect = { onSelect(person) },
                        onOpenDetail = { onOpenDetail(person) },
                    )
                }
            }
        }
        if (!compactChrome) {
            TextButton(
                onClick = { refit += 1 },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text("Всё древо", color = HdOrange, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RelativesPersonCard(
    person: PersonEntity,
    selected: Boolean,
    fresh: Boolean,
    detached: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val bg = when (person.gender) {
        "f" -> Color(0xFF3A2230)
        "m" -> Color(0xFF1A2A44)
        else -> HdPanelElevated
    }
    val border = when {
        fresh -> HdOrange
        selected -> HdBlue
        person.gender == "f" -> Color(0xFFA85A72)
        person.gender == "m" -> Color(0xFF3D6EA8)
        else -> Color(0xFF556677)
    }
    Card(
        modifier = modifier.pointerInput(person.id) {
                detectTapGestures(onTap = { onSelect() }, onDoubleTap = { onOpenDetail() })
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.95f)),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (person.isVacantSlot()) {
                Text("+", color = HdOrange, fontWeight = FontWeight.Light)
                Text("Пусто", color = HdTextSecondary, fontSize = 11.sp)
            } else {
                Text(
                    person.fullName(),
                    color = HdTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val years = person.yearsLabel()
                if (years.isNotBlank()) {
                    Text(years, color = HdTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                if (detached) {
                    Text("отд. ветка", color = HdOrange, fontSize = 9.sp)
                }
            }
        }
    }
}
