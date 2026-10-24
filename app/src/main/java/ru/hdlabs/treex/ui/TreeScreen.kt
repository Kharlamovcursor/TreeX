package ru.hdlabs.treex.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.data.RemoteRefresh
import ru.hdlabs.treex.data.TreeState
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.data.settings.AppSettings
import ru.hdlabs.treex.domain.TreeLayout
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.lifeLine
import ru.hdlabs.treex.domain.matchesQuery
import ru.hdlabs.treex.domain.spouseIdsOf
import ru.hdlabs.treex.feedback.TelegramFeedback
import ru.hdlabs.treex.navigation.Routes
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdScaffold
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.components.HdTextField
import ru.hdlabs.treex.ui.theme.HdBlack
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanelElevated
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary

@Composable
fun TreeScreen(nav: NavHostController, highlight: String) {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val state by app.container.treeRepository.observe().collectAsStateWithLifecycle(TreeState())
    val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    val syncNote by app.container.treeRepository.syncNote.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var focusId by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var updatedMessage by remember { mutableStateOf<String?>(null) }
    var developerMessage by remember { mutableStateOf<String?>(null) }
    var activeHighlight by remember { mutableStateOf(highlight) }

    LaunchedEffect(highlight) {
        activeHighlight = highlight
        if (highlight.isNotBlank()) {
            focusId = highlight
            kotlinx.coroutines.delay(2600)
            activeHighlight = ""
            nav.currentBackStackEntry?.savedStateHandle?.set("highlight", "")
        }
    }
    LaunchedEffect(state.people, settings.homePersonId) {
        val ids = state.people.map { it.id }.toSet()
        if (focusId in ids) return@LaunchedEffect
        focusId = settings.homePersonId.takeIf { it in ids }
            ?: TreeLayout.bands(state.people, state.unions).firstOrNull()?.firstOrNull()?.id
            ?: ""
    }

    val visible = state.people.filter { person -> person.matchesQuery(query) }
    val focus = state.people.find { it.id == focusId }

    HdScaffold(
        title = "Древо",
        scroll = false,
        actions = {
            IconButton(onClick = {
                if (refreshing) return@IconButton
                scope.launch {
                    refreshing = true
                    when (val result = app.container.treeRepository.refreshFromGit()) {
                        is RemoteRefresh.Updated -> updatedMessage = "Информация обновлена. В древе ${result.count} чел."
                        RemoteRefresh.Unchanged -> developerMessage =
                            "Новой информации в Git нет. Если вы ожидали изменения, свяжитесь с разработчиком."
                        RemoteRefresh.NotConfigured -> developerMessage =
                            "Git не настроен, новую информацию взять неоткуда. Если данные должны были обновиться, свяжитесь с разработчиком."
                        is RemoteRefresh.Failed -> developerMessage =
                            "${result.message} Если это повторяется, свяжитесь с разработчиком."
                    }
                    refreshing = false
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить информацию", tint = HdTextPrimary)
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = HdTextPrimary)
            }
        },
        floating = {
            FloatingActionButton(
                onClick = { nav.navigate(Routes.edit("new")) },
                containerColor = HdBlue,
                contentColor = HdBlack,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить человека")
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                if (state.people.isEmpty()) "Семейное древо" else "В древе ${state.people.size} чел.",
                color = HdTextSecondary,
            )
            if (syncNote != null) {
                Text(syncNote.orEmpty(), color = HdOrange, modifier = Modifier.padding(top = 6.dp))
            }
            if (state.people.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HdTextField(query, { query = it }, "Поиск по имени, телефону, почте")
            }
            Spacer(Modifier.height(12.dp))
            when {
                state.people.isEmpty() -> EmptyTree()
                query.isNotBlank() && visible.isEmpty() -> Text("Никого не нашлось", color = HdTextSecondary)
                query.isNotBlank() -> visible.forEach { person ->
                    PersonCard(person, person.id == activeHighlight) { nav.navigate(Routes.person(person.id)) }
                    Spacer(Modifier.height(8.dp))
                }
                focus != null -> Pedigree(
                    focus = focus,
                    people = state.people,
                    unions = state.unions,
                    freshId = activeHighlight,
                    isHome = focus.id == settings.homePersonId,
                    onFocus = { focusId = it.id },
                    onOpen = { nav.navigate(Routes.person(it.id)) },
                    onAdd = { role, anchor -> nav.navigate(Routes.edit("new", role, anchor)) },
                    onMakeHome = {
                        scope.launch {
                            app.container.settingsRepository.update { it.copy(homePersonId = focus.id) }
                        }
                    },
                )
            }
            Spacer(Modifier.height(88.dp))
        }
    }

    updatedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updatedMessage = null },
            title = { Text("Готово") },
            text = { Text(message, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { updatedMessage = null } },
        )
    }
    developerMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { developerMessage = null },
            title = { Text("Информация не обновилась") },
            text = { Text(message, color = HdTextSecondary) },
            confirmButton = {
                HdPrimaryButton("Написать в Telegram") {
                    TelegramFeedback.open(context)
                    developerMessage = null
                }
            },
            dismissButton = { HdSecondaryButton("Закрыть") { developerMessage = null } },
        )
    }
}

@Composable
private fun Pedigree(
    focus: PersonEntity,
    people: List<PersonEntity>,
    unions: List<UnionEntity>,
    freshId: String,
    isHome: Boolean,
    onFocus: (PersonEntity) -> Unit,
    onOpen: (PersonEntity) -> Unit,
    onAdd: (role: String, anchor: String) -> Unit,
    onMakeHome: () -> Unit,
) {
    val byId = people.associateBy { it.id }
    val father = byId[focus.fatherId]
    val mother = byId[focus.motherId]
    val spouses = spouseIdsOf(unions, focus.id).mapNotNull { byId[it] }
    val children = people.filter { it.fatherId == focus.id || it.motherId == focus.id }
        .sortedBy { it.birthDate }
    val siblings = people.filter { other ->
        other.id != focus.id && (
            (focus.fatherId != null && other.fatherId == focus.fatherId) ||
                (focus.motherId != null && other.motherId == focus.motherId)
            )
    }.sortedBy { it.birthDate }

    Text("Родители", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    LazyRow(Modifier.height(190.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (father != null) {
            item(key = father.id) {
                PersonCard(father, father.id == freshId, Modifier.width(150.dp)) { onFocus(father) }
            }
        } else {
            item(key = "add-father") { AddSlot("Отец") { onAdd("father", focus.id) } }
        }
        if (mother != null) {
            item(key = mother.id) {
                PersonCard(mother, mother.id == freshId, Modifier.width(150.dp)) { onFocus(mother) }
            }
        } else {
            item(key = "add-mother") { AddSlot("Мать") { onAdd("mother", focus.id) } }
        }
    }
    if (siblings.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Братья и сёстры", color = HdTextSecondary)
        LazyRow(Modifier.height(168.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(siblings, key = { it.id }) { person ->
                PersonCard(person, person.id == freshId, Modifier.width(140.dp)) { onFocus(person) }
            }
        }
    }
    LinkDown()
    Text(if (isHome) "Это главная карточка" else "Сейчас в центре", color = HdTextSecondary)
    Spacer(Modifier.height(8.dp))
    LazyRow(Modifier.height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(spouses, key = { it.id }) { person ->
            PersonCard(person, person.id == freshId, Modifier.width(140.dp)) { onFocus(person) }
        }
        item(key = "focus-${focus.id}") {
            PersonCard(focus, true, Modifier.width(168.dp)) { onOpen(focus) }
        }
        item(key = "add-spouse") { AddSlot("Супруг") { onAdd("spouse", focus.id) } }
    }
    Spacer(Modifier.height(8.dp))
    HdSecondaryButton("Открыть карточку") { onOpen(focus) }
    Spacer(Modifier.height(8.dp))
    if (!isHome) {
        HdSecondaryButton("Сделать главной") { onMakeHome() }
        Spacer(Modifier.height(8.dp))
    }
    LinkDown()
    Text("Дети", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    LazyRow(Modifier.height(190.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(children, key = { it.id }) { person ->
            PersonCard(person, person.id == freshId, Modifier.width(150.dp)) { onFocus(person) }
        }
        item(key = "add-child") { AddSlot("Ребёнок") { onAdd("child", focus.id) } }
    }
    Text(
        "Нажатие на родственника переносит его в центр. Пустая ячейка создаёт карточку и сама проставляет фамилию и отчество.",
        color = HdTextSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LinkDown() {
    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(2.dp).height(18.dp).background(HdBlue.copy(alpha = 0.45f)))
    }
}

@Composable
private fun AddSlot(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(132.dp).height(168.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, HdBlue.copy(alpha = 0.45f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("＋ $label", color = HdBlue, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyTree() {
    Text(
        "Добавьте первого человека. Карточка появится в древе плавно и сразу сохранится на телефоне.",
        color = HdTextSecondary,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun PersonCard(
    person: PersonEntity,
    fresh: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var shown by remember(person.id) { mutableStateOf(!fresh) }
    LaunchedEffect(person.id, fresh) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.72f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale",
    )
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "cardAlpha")
    Card(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.94f)),
        border = BorderStroke(if (fresh) 2.dp else 1.dp, if (fresh) HdOrange else HdBlue.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PersonAvatar(person, 64.dp)
            Spacer(Modifier.height(8.dp))
            Text(person.fullName(), color = HdTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            val years = person.lifeLine()
            if (years.isNotBlank()) Text(years, color = HdTextSecondary, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}
