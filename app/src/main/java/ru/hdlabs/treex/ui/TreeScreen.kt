package ru.hdlabs.treex.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import ru.hdlabs.treex.domain.addSpouseLabel
import ru.hdlabs.treex.domain.spouseNoun
import ru.hdlabs.treex.feedback.TelegramFeedback
import ru.hdlabs.treex.navigation.Routes
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdScaffold
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.components.HdTextField
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
    var gitChoice by remember { mutableStateOf<RemoteRefresh.Diverged?>(null) }
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
                    try {
                        when (val result = app.container.treeRepository.inspectGit()) {
                            is RemoteRefresh.Incoming -> updatedMessage = appliedGitMessage(
                                app.container.treeRepository.refreshFromGit(),
                            )
                            is RemoteRefresh.LocalAhead -> updatedMessage = app.container.treeRepository.pushNow()
                            is RemoteRefresh.Diverged -> gitChoice = result
                            is RemoteRefresh.Updated -> updatedMessage = "Информация обновлена. В древе ${result.count} чел."
                            RemoteRefresh.Unchanged -> updatedMessage = "Телефон и Git уже совпадают."
                            RemoteRefresh.NotConfigured -> updatedMessage =
                                "Git не настроен. Откройте настройки и укажите репозиторий и токен."
                            is RemoteRefresh.Failed -> developerMessage = result.message
                        }
                    } finally {
                        refreshing = false
                    }
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить информацию", tint = HdTextPrimary)
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = HdTextPrimary)
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
                state.people.isEmpty() -> EmptyTree { nav.navigate(Routes.edit("new")) }
                query.isNotBlank() && visible.isEmpty() -> Text("Никого не нашлось", color = HdTextSecondary)
                query.isNotBlank() -> visible.forEach { person ->
                    PersonCard(
                        person = person,
                        fresh = person.id == activeHighlight,
                        hint = "Открыть семью",
                    ) {
                        query = ""
                        focusId = person.id
                    }
                    Spacer(Modifier.height(8.dp))
                }
                focus != null -> Pedigree(
                    focus = focus,
                    people = state.people,
                    unions = state.unions,
                    freshId = activeHighlight,
                    isHome = focus.id == settings.homePersonId,
                    onFocus = { focusId = it.id },
                    onOpen = { nav.navigate(Routes.edit(it.id)) },
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
            title = { Text("Git не ответил") },
            text = { Text(message, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { developerMessage = null } },
            dismissButton = {
                HdSecondaryButton("Написать в Telegram") {
                    TelegramFeedback.open(context)
                    developerMessage = null
                }
            },
        )
    }
    gitChoice?.let { choice ->
        AlertDialog(
            onDismissRequest = { gitChoice = null },
            title = { Text("В Git другая версия") },
            text = {
                Text(
                    "В Git ${choice.count} чел., и на телефоне есть свои правки. Загрузить Git или отправить телефон?",
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton("Загрузить из Git") {
                    gitChoice = null
                    scope.launch {
                        updatedMessage = appliedGitMessage(app.container.treeRepository.refreshFromGit())
                    }
                }
            },
            dismissButton = {
                HdSecondaryButton("Отправить телефон") {
                    gitChoice = null
                    scope.launch {
                        updatedMessage = runCatching { app.container.treeRepository.pushNow() }
                            .getOrElse { it.message ?: "Не удалось отправить в Git" }
                    }
                }
            },
        )
    }
}

private fun appliedGitMessage(result: RemoteRefresh): String = when (result) {
    is RemoteRefresh.Updated -> "Информация обновлена. В древе ${result.count} чел."
    RemoteRefresh.Unchanged -> "Телефон и Git уже совпадают."
    RemoteRefresh.NotConfigured -> "Git не настроен. Откройте настройки и укажите репозиторий и токен."
    is RemoteRefresh.Failed -> result.message
    is RemoteRefresh.Incoming -> "В Git другая версия, ${result.count} чел."
    is RemoteRefresh.LocalAhead -> "На телефоне есть правки, которых нет в Git."
    is RemoteRefresh.Diverged -> "В Git другая версия, ${result.count} чел."
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
    Text("Карточка открывает семью этого человека.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
    if (father != null || mother != null) {
        LazyRow(Modifier.height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (father != null) {
                item(key = father.id) {
                    PersonCard(father, father.id == freshId, Modifier.width(150.dp), "Отец") { onFocus(father) }
                }
            }
            if (mother != null) {
                item(key = mother.id) {
                    PersonCard(mother, mother.id == freshId, Modifier.width(150.dp), "Мать") { onFocus(mother) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (father == null) {
        HdSecondaryButton("Добавить отца") { onAdd("father", focus.id) }
        Spacer(Modifier.height(8.dp))
    }
    if (mother == null) {
        HdSecondaryButton("Добавить мать") { onAdd("mother", focus.id) }
        Spacer(Modifier.height(8.dp))
    }
    if (siblings.isNotEmpty()) {
        Text("Братья и сёстры", color = HdTextSecondary)
        Spacer(Modifier.height(8.dp))
        LazyRow(Modifier.height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(siblings, key = { it.id }) { person ->
                PersonCard(person, person.id == freshId, Modifier.width(140.dp), "Открыть") { onFocus(person) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    FocusCard(
        person = focus,
        isHome = isHome,
        fresh = focus.id == freshId,
        onEdit = { onOpen(focus) },
        onMakeHome = onMakeHome,
    )
    Spacer(Modifier.height(16.dp))
    Text(spouseNoun(focus.gender), color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
    Text("Рядом с выбранным человеком.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
    if (spouses.isNotEmpty()) {
        LazyRow(Modifier.height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(spouses, key = { it.id }) { person ->
                PersonCard(person, person.id == freshId, Modifier.width(150.dp), "Открыть") { onFocus(person) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    HdSecondaryButton(addSpouseLabel(focus.gender)) { onAdd("spouse", focus.id) }
    Spacer(Modifier.height(16.dp))
    Text("Дети", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
    Text("Ниже выбранного человека.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
    if (children.isNotEmpty()) {
        LazyRow(Modifier.height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(children, key = { it.id }) { person ->
                PersonCard(person, person.id == freshId, Modifier.width(150.dp), "Открыть") { onFocus(person) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    HdSecondaryButton("Добавить ребёнка") { onAdd("child", focus.id) }
    Spacer(Modifier.height(16.dp))
    HdSecondaryButton("Добавить человека без родства") { onAdd("", "") }
}

@Composable
private fun EmptyTree(onAdd: () -> Unit) {
    Text(
        "Начните с одного человека. Потом к нему можно добавить родителей, супруга и детей отдельными кнопками.",
        color = HdTextSecondary,
    )
    Spacer(Modifier.height(12.dp))
    HdPrimaryButton("Добавить первого человека", onClick = onAdd)
}

@Composable
private fun FocusCard(
    person: PersonEntity,
    isHome: Boolean,
    fresh: Boolean,
    onEdit: () -> Unit,
    onMakeHome: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HdPanelElevated.copy(alpha = 0.94f)),
        border = BorderStroke(2.dp, if (fresh) HdOrange else HdBlue),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isHome) "Главный человек" else "Сейчас смотрим", color = HdOrange, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            PersonAvatar(person, 96.dp)
            Spacer(Modifier.height(8.dp))
            Text(person.fullName(), color = HdTextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            val years = person.lifeLine()
            if (years.isNotBlank()) Text(years, color = HdTextSecondary, textAlign = TextAlign.Center)
            if (person.birthPlace.isNotBlank()) Text(person.birthPlace, color = HdTextSecondary, textAlign = TextAlign.Center)
            if (person.phone.isNotBlank()) Text(person.phone, color = HdTextSecondary)
            Spacer(Modifier.height(12.dp))
            HdPrimaryButton("Изменить данные", onClick = onEdit)
            if (!isHome) {
                Spacer(Modifier.height(8.dp))
                HdSecondaryButton("Сделать главным", onClick = onMakeHome)
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonEntity,
    fresh: Boolean,
    modifier: Modifier = Modifier,
    hint: String,
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
            Text(person.fullName(), color = HdTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            val years = person.lifeLine()
            if (years.isNotBlank()) Text(years, color = HdTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(hint, color = HdOrange, maxLines = 1)
        }
    }
}
