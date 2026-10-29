package ru.hdlabs.treex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
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
            engine.calcTree(nodes, effectiveRoot)
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
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .verticalScroll(vScroll)
            .horizontalScroll(hScroll),
    ) {
        Box(Modifier.size(widthDp.coerceAtLeast(maxWidth), heightDp.coerceAtLeast(maxHeight))) {
            Canvas(Modifier.fillMaxSize()) {
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
            layout.nodes.forEach { node ->
                val person = byId[node.id] ?: return@forEach
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
                        .height(cardDp),
                    onSelect = { onSelect(person) },
                    onOpenDetail = { onOpenDetail(person) },
                )
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
        modifier = modifier
            .padding(RelativesTreeLayout.CARD_GAP_PX.dp / 4f)
            .pointerInput(person.id) {
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
