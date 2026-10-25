package ru.hdlabs.treex.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import ru.hdlabs.treex.data.settings.AppSettings
import ru.hdlabs.treex.domain.TreeLayout
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.isVacantSlot
import ru.hdlabs.treex.domain.lifeLine
import ru.hdlabs.treex.domain.matchesQuery
import ru.hdlabs.treex.domain.acceptsAsParent
import ru.hdlabs.treex.domain.addSpouseLabel
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.describeOpenLink
import ru.hdlabs.treex.feedback.MaxFeedback
import ru.hdlabs.treex.feedback.TelegramFeedback
import ru.hdlabs.treex.navigation.Routes
import ru.hdlabs.treex.ui.components.HdCard
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdLabsBrandTitle
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
    val openLinks by app.container.treeRepository.openLinks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var focusId by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var updatedMessage by remember { mutableStateOf<String?>(null) }
    var developerMessage by remember { mutableStateOf<String?>(null) }
    var gitChoice by remember { mutableStateOf<RemoteRefresh.Diverged?>(null) }
    var activeHighlight by remember { mutableStateOf(highlight) }
    var centerToken by remember { mutableIntStateOf(0) }
    var pendingLinkId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showOpenLinks by remember { mutableStateOf(false) }
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(highlight) {
        activeHighlight = highlight
        if (highlight.isNotBlank()) {
            focusId = highlight
            actionsExpanded = false
            kotlinx.coroutines.delay(2600)
            activeHighlight = ""
            nav.currentBackStackEntry?.savedStateHandle?.set("highlight", "")
        }
    }
    LaunchedEffect(state.people) {
        val ids = state.people.map { it.id }.toSet()
        if (focusId.isNotBlank() && focusId !in ids) {
            focusId = ""
            actionsExpanded = false
        }
    }

    val livingCount = state.people.count { !it.vacant }
    val visible = state.people.filter { person -> person.matchesQuery(query) }
    val focus = state.people.find { it.id == focusId }

    BackHandler(enabled = focusId.isNotBlank() || actionsExpanded) {
        when {
            actionsExpanded -> actionsExpanded = false
            focusId.isNotBlank() -> {
                focusId = ""
                actionsExpanded = false
            }
        }
    }

    HdScaffold(
        title = "Древо by HDLabs",
        titleContent = { HdLabsBrandTitle() },
        scroll = false,
        progressMessage = if (refreshing) "Обновление из Git…" else null,
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
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = HdOrange,
                    )
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить информацию", tint = HdTextPrimary)
                }
            }
            IconButton(onClick = { nav.navigate(Routes.TREE_HELP) }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Справка", tint = HdTextPrimary)
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = HdTextPrimary)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.people.isNotEmpty()) {
                Text(
                    if (livingCount == state.people.size) {
                        "В древе $livingCount чел."
                    } else {
                        "В древе $livingCount чел., пустых мест ${state.people.size - livingCount}"
                    },
                    color = HdTextSecondary,
                )
            }
            if (syncNote != null) {
                Text(syncNote.orEmpty(), color = HdOrange, modifier = Modifier.padding(top = 4.dp))
            }
            if (state.people.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HdTextField(query, { query = it }, "Поиск")
            }
            Spacer(Modifier.height(4.dp))
            when {
                state.people.isEmpty() -> EmptyTree { nav.navigate(Routes.edit("new")) }
                query.isNotBlank() && visible.isEmpty() -> Text("Никого не нашлось", color = HdTextSecondary)
                query.isNotBlank() -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    visible.forEach { person ->
                        PersonCard(
                            person = person,
                            fresh = person.id == activeHighlight,
                            hint = "Показать в древе",
                        ) {
                            query = ""
                            focusId = person.id
                            actionsExpanded = false
                            centerToken += 1
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                else -> {
                    FamilyTree(
                        people = state.people,
                        unions = state.unions,
                        openLinks = openLinks,
                        selectedId = focusId,
                        freshId = activeHighlight,
                        centerToken = centerToken,
                        pendingLinkId = pendingLinkId,
                        modifier = Modifier.weight(1f),
                        onSelect = { person ->
                            val linkId = pendingLinkId
                            if (linkId != null) {
                                val link = openLinks.find { it.id == linkId }
                                if (link != null && link.slot in setOf("father", "mother") && !acceptsAsParent(link.slot, person)) {
                                    updatedMessage = if (link.slot == "father") {
                                        "Для отца выберите мужскую карточку."
                                    } else {
                                        "Для матери выберите женскую карточку."
                                    }
                                    return@FamilyTree
                                }
                                pendingLinkId = null
                                scope.launch {
                                    runCatching {
                                        app.container.treeRepository.attachOpenLink(linkId, person.id)
                                    }.onFailure { error ->
                                        updatedMessage = error.message ?: "Не удалось прикрепить связь"
                                    }
                                }
                                if (person.isVacantSlot()) {
                                    nav.navigate(Routes.edit(person.id))
                                }
                            } else {
                                focusId = person.id
                                actionsExpanded = false
                                centerToken += 1
                            }
                        },
                        onOpenDetail = { nav.navigate(Routes.person(it.id)) },
                        onHoldLink = { pendingLinkId = it },
                    )
                    if (focus != null) {
                        if (actionsExpanded) {
                            FocusActionPanel(
                                focus = focus,
                                people = state.people,
                                openLinks = openLinks,
                                pendingLinkId = pendingLinkId,
                                isHome = focus.id == settings.homePersonId,
                                onOpenEditor = { nav.navigate(Routes.edit(focus.id)) },
                                onOpenDetail = { nav.navigate(Routes.person(focus.id)) },
                                onAdd = { role, anchor -> nav.navigate(Routes.edit("new", role, anchor)) },
                                onDelete = { confirmDelete = true },
                                onMakeHome = {
                                    scope.launch {
                                        app.container.settingsRepository.update { it.copy(homePersonId = focus.id) }
                                    }
                                },
                                onShowOpenLinks = { showOpenLinks = true },
                                onCancelLinkPick = { pendingLinkId = null },
                                onCollapse = { actionsExpanded = false },
                            )
                        } else {
                            CompactFocusBar(
                                focus = focus,
                                onExpand = { actionsExpanded = true },
                            )
                        }
                    }
                }
            }
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
                Column {
                    HdSecondaryButton("Telegram") {
                        TelegramFeedback.open(context)
                        developerMessage = null
                    }
                    Spacer(Modifier.height(8.dp))
                    HdSecondaryButton("MAX") {
                        MaxFeedback.open(context)
                        developerMessage = null
                    }
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
    if (showOpenLinks && openLinks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showOpenLinks = false },
            title = { Text("Свободные связи") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "После удаления карточки линия остаётся «висящей». Выберите связь и нажмите «Прикрепить», затем тапните нужную карточку на древе. " +
                            "Или нажмите оранжевую точку прямо на линии.",
                        color = HdTextSecondary,
                    )
                    openLinks.forEach { link ->
                        Column {
                            Text(describeOpenLink(link, state.people), color = HdTextPrimary)
                            HdSecondaryButton("Прикрепить к карточке") {
                                pendingLinkId = link.id
                                showOpenLinks = false
                            }
                        }
                    }
                }
            },
            confirmButton = { HdPrimaryButton("Закрыть") { showOpenLinks = false } },
        )
    }
    if (confirmDelete && focus != null) {
        val vacant = focus.isVacantSlot()
        val name = focus.fullName()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (vacant) "Убрать пустое место?" else "Удалить карточку?") },
            text = {
                Text(
                    if (vacant) {
                        "Место исчезнет с древа, связи перестроятся. При необходимости появятся свободные линии для переноса."
                    } else {
                        "$name станет пустым местом на древе: связи и расположение сохранятся. Там можно создать новую карточку или изменить связи."
                    },
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton(if (vacant) "Убрать" else "Удалить") {
                    val id = focus.id
                    confirmDelete = false
                    pendingLinkId = null
                    actionsExpanded = false
                    scope.launch {
                        if (settings.homePersonId == id) {
                            app.container.settingsRepository.update { it.copy(homePersonId = "") }
                        }
                        app.container.treeRepository.deletePerson(id)
                        if (vacant) focusId = ""
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Отмена") { confirmDelete = false } },
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
private fun CompactFocusBar(focus: PersonEntity, onExpand: () -> Unit) {
    HdCard(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable { onExpand() },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                focus.fullName(),
                color = HdTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Действия", tint = HdOrange)
        }
    }
}

@Composable
private fun FocusActionPanel(
    focus: PersonEntity,
    people: List<PersonEntity>,
    openLinks: List<OpenLink>,
    pendingLinkId: String?,
    isHome: Boolean,
    onOpenEditor: () -> Unit,
    onOpenDetail: () -> Unit,
    onAdd: (role: String, anchor: String) -> Unit,
    onDelete: () -> Unit,
    onMakeHome: () -> Unit,
    onShowOpenLinks: () -> Unit,
    onCancelLinkPick: () -> Unit,
    onCollapse: () -> Unit,
) {
    val known = people.map { it.id }.toSet()
    val vacant = focus.isVacantSlot()
    HdCard(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onCollapse() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                focus.fullName(),
                color = HdTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Свернуть", color = HdOrange, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        if (vacant) {
            HdPrimaryButton("Создать карточку") { onOpenEditor() }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("Изменить связи") { onOpenEditor() }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton(if (vacant) "Убрать место" else "Удалить") { onDelete() }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { HdPrimaryButton("Изменить") { onOpenEditor() } }
                Box(Modifier.weight(1f)) { HdSecondaryButton("Карточка") { onOpenDetail() } }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    HdSecondaryButton("Удалить") { onDelete() }
                }
                if (!isHome) {
                    Box(Modifier.weight(1f)) {
                        HdSecondaryButton("Главный") { onMakeHome() }
                    }
                }
            }
        }
        if (pendingLinkId != null) {
            Spacer(Modifier.height(8.dp))
            Text("Тапните карточку на древе, к которой прикрепить связь.", color = HdOrange)
            HdSecondaryButton("Отменить перенос") { onCancelLinkPick() }
        } else if (openLinks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("Свободные связи (${openLinks.size})") { onShowOpenLinks() }
        }
        if (!vacant) {
            Spacer(Modifier.height(8.dp))
            Text("Добавить родственника", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (focus.fatherId !in known) TreeAction("Отец") { onAdd("father", focus.id) }
                if (focus.motherId !in known) TreeAction("Мать") { onAdd("mother", focus.id) }
                TreeAction(addSpouseLabel(focus.gender)) { onAdd("spouse", focus.id) }
                TreeAction("Ребёнок") { onAdd("child", focus.id) }
                TreeAction("Без родства") { onAdd("", "") }
            }
        }
    }
}

@Composable
private fun TreeAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) HdBlue.copy(alpha = 0.28f) else HdPanelElevated,
            contentColor = HdTextPrimary,
        ),
        border = BorderStroke(1.dp, if (primary) HdBlue else HdOrange.copy(alpha = 0.75f)),
    ) {
        Text(text, maxLines = 1)
    }
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
            Text(person.fullName(), color = HdTextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            val years = person.lifeLine()
            if (years.isNotBlank()) Text(years, color = HdTextSecondary, textAlign = TextAlign.Center)
            Text(hint, color = HdOrange, maxLines = 1)
        }
    }
}
