package ru.hdlabs.treex.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.BuildConfig
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.backup.BackupZip
import ru.hdlabs.treex.backup.TreeCodec
import ru.hdlabs.treex.data.SnapshotInfo
import ru.hdlabs.treex.data.settings.AppSettings
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.diagnostic.DiagnosticReport
import ru.hdlabs.treex.diagnostic.DiagnosticShare
import ru.hdlabs.treex.feedback.MaxFeedback
import ru.hdlabs.treex.feedback.TelegramFeedback
import ru.hdlabs.treex.sync.FamilyGitConfig
import ru.hdlabs.treex.navigation.Routes
import ru.hdlabs.treex.sync.GitHubTreeSync
import ru.hdlabs.treex.sync.TelegramBackup
import ru.hdlabs.treex.ui.components.HdCard
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdScaffold
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.components.HdTextField
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary
import ru.hdlabs.treex.update.ApkDownloadHelper
import ru.hdlabs.treex.update.InstallHelper
import ru.hdlabs.treex.update.ReleaseUpdateConfig
import ru.hdlabs.treex.update.UpdateInfo
import ru.hdlabs.treex.util.NetworkHelper
import java.io.File

private const val DEVELOPER_SETTINGS_PASSWORD = "86712"

@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val repo = app.container.treeRepository
    val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<String?>(null) }
    var reportMessage by remember { mutableStateOf<String?>(null) }
    var reportLoading by remember { mutableStateOf(false) }
    var snapshotLoading by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var wipeStep by remember { mutableStateOf(0) }
    var wipeLoading by remember { mutableStateOf(false) }
    var purgeVacantLoading by remember { mutableStateOf(false) }

    HdScaffold(title = "Настройки", onBack = { nav.popBackStack() }, progressMessage = progress) {
        HdCard {
            Text("Оформление", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Светлая тема", color = HdTextPrimary)
                    Text(
                        "Тёмный космический фон или светлый спокойный интерфейс.",
                        color = HdTextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = settings.lightTheme,
                    onCheckedChange = { light ->
                        scope.launch {
                            app.container.settingsRepository.update { it.copy(lightTheme = light) }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HdCard {
            Text("Приложение", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Версия ${BuildConfig.VERSION_NAME}",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            Text(
                "Обновление ставится поверх текущей версии: древо, фото и история сохраняются. Не удаляйте приложение перед обновлением.",
                color = HdTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            UpdateSection(onProgress = { progress = it })
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton(
                "Отправить отчёт о проблеме",
                loading = reportLoading,
                loadingText = "Сбор отчёта…",
            ) {
                scope.launch {
                    reportLoading = true
                    progress = "Подготовка отчёта…"
                    try {
                        val text = DiagnosticReport.buildText(context)
                        val file = File(context.cacheDir, "treex-diagnostic.txt")
                        withContext(Dispatchers.IO) { file.writeText(text) }
                        AppEventLog.info("report", "Пользователь отправил диагностический отчёт")
                        DiagnosticShare.share(context, file)
                    } catch (e: Exception) {
                        reportMessage = e.message ?: "Не удалось собрать отчёт"
                    } finally {
                        reportLoading = false
                        progress = null
                    }
                }
            }
            Text(
                "Файл с версией и последними ошибками (без древа). Отправьте его вместе с сообщением разработчику.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HdCard {
            Text("Связь с разработчиком", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Если древо отображается неправильно или что-то сломалось — напишите Якову (Telegram @Yakofffff или MAX, Yakoff). " +
                    "Можно приложить отчёт о проблеме и снимок древа в Git.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            HdPrimaryButton("Написать в Telegram") {
                TelegramFeedback.open(context).onFailure { feedbackMessage = it.message ?: "Не удалось открыть Telegram" }
            }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("Написать в MAX") {
                MaxFeedback.open(context).onFailure { feedbackMessage = it.message ?: "Не удалось открыть MAX" }
            }
            Spacer(Modifier.height(12.dp))
            HdSecondaryButton(
                "Отправить снимок древа в Git",
                loading = snapshotLoading,
                loadingText = "Рисуем и отправляем…",
                enabled = settings.gitConfigured,
            ) {
                scope.launch {
                    snapshotLoading = true
                    progress = "Снимок всего древа…"
                    try {
                        val result = repo.pushTreeSnapshotToGit()
                        feedbackMessage = result
                        AppEventLog.info("debug", "Пользователь отправил снимок древа в Git")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        feedbackMessage = e.message ?: "Не удалось отправить снимок"
                        AppEventLog.error("debug", feedbackMessage ?: "snapshot", e)
                    } finally {
                        snapshotLoading = false
                        progress = null
                    }
                }
            }
            Text(
                if (settings.gitConfigured) {
                    "PNG со всеми карточками и линиями (файл family/debug/tree-layout.png в репозитории). " +
                        "JSON древа не меняется."
                } else {
                    "Снимок в Git доступен, когда настроена синхронизация (встроенный Git в семейной сборке)."
                },
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HdCard {
            Text("Git и отладка", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Не подгружать древо из Git", color = HdTextPrimary)
                    Text(
                        "Пока включено — после очистки или при пустом древе Git не подтягивается сам. " +
                            "Загрузка вручную с экрана древа остаётся.",
                        color = HdTextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = settings.pauseGitAutoSync,
                    onCheckedChange = { paused ->
                        scope.launch {
                            app.container.settingsRepository.update { it.copy(pauseGitAutoSync = paused) }
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            HdSecondaryButton(
                "Удалить все пустые места",
                loading = purgeVacantLoading,
                loadingText = "…",
            ) {
                scope.launch {
                    purgeVacantLoading = true
                    progress = "Удаление пустых мест…"
                    try {
                        val n = repo.purgeAllVacantSlots()
                        feedbackMessage = if (n > 0) {
                            "Удалено пустых мест: $n."
                        } else {
                            "Пустых мест не было."
                        }
                    } catch (e: Exception) {
                        feedbackMessage = e.message ?: "Не удалось удалить пустые места"
                    } finally {
                        purgeVacantLoading = false
                        progress = null
                    }
                }
            }
            Text(
                "«Пустое место» — карточка после удаления человека; они не считаются в «чел.», но остаются в базе, пока не убрать.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HdCard {
            Text("Очистить древо", color = HdOrange, fontWeight = FontWeight.SemiBold)
            Text(
                "Удалить всех людей и связи на этом телефоне. Перед очисткой сохраняется снимок в «История». " +
                    "После очистки автозагрузка из Git отключается — включите выше, когда закончите отладку.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            HdSecondaryButton(
                "Стереть всё древо на телефоне",
                loading = wipeLoading,
                loadingText = "Очистка…",
            ) { wipeStep = 1 }
        }
        Spacer(Modifier.height(12.dp))
        HdSecondaryButton("Инструкция") { nav.navigate(Routes.USER_GUIDE) }
        Spacer(Modifier.height(12.dp))
        HdSecondaryButton("Настройки разработчика") { nav.navigate(Routes.DEVELOPER_SETTINGS) }
    }

    reportMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { reportMessage = null },
            title = { Text("Отчёт") },
            text = { Text(text, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { reportMessage = null } },
        )
    }
    feedbackMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { feedbackMessage = null },
            title = { Text("Обратная связь") },
            text = { Text(text, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { feedbackMessage = null } },
        )
    }
    if (wipeStep == 1) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text("Стереть древо?") },
            text = {
                Text(
                    "Все карточки на этом телефоне будут удалены. Снимок перед удалением попадёт в историю.",
                    color = HdTextSecondary,
                )
            },
            confirmButton = { HdPrimaryButton("Продолжить") { wipeStep = 2 } },
            dismissButton = { HdSecondaryButton("Отмена") { wipeStep = 0 } },
        )
    }
    if (wipeStep == 2) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text("Подтвердите") },
            text = {
                Text(
                    "После очистки Git не подтянется сам — можно собрать древо с нуля. " +
                        "Автозагрузку из Git можно снова включить в настройках.",
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton("Стереть", loading = wipeLoading, loadingText = "…") {
                    scope.launch {
                        wipeLoading = true
                        progress = "Очистка древа…"
                        try {
                            feedbackMessage = repo.wipeTree()
                            wipeStep = 0
                        } catch (e: Exception) {
                            feedbackMessage = e.message ?: "Не удалось очистить"
                            wipeStep = 0
                        } finally {
                            wipeLoading = false
                            progress = null
                        }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Отмена") { wipeStep = 0 } },
        )
    }
}

@Composable
fun DeveloperSettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val repo = app.container.treeRepository
    val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var path by remember { mutableStateOf("family/tree.json") }
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var hydrated by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var gitPushLoading by remember { mutableStateOf(false) }
    var gitPushDialog by remember { mutableStateOf<String?>(null) }
    var gitPushResult by remember { mutableStateOf<String?>(null) }
    var telegramBackupLoading by remember { mutableStateOf(false) }
    var gitSaveLoading by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var gatePassword by remember { mutableStateOf("") }
    var gateError by remember { mutableStateOf(false) }
    var wipeStep by remember { mutableStateOf(0) }
    var wipeLoading by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!hydrated) {
            token = settings.githubToken
            owner = settings.githubOwner
            repoName = settings.githubRepo
            branch = settings.githubBranch
            path = settings.githubPath
            botToken = settings.telegramBotToken
            chatId = settings.telegramChatId
            hydrated = true
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            progress = "Экспорт JSON…"
            try {
                val text = TreeCodec.encode(repo.exportDocument())
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                message = "JSON сохранён"
            } catch (e: Exception) {
                message = e.message ?: "Не удалось экспортировать"
            } finally {
                progress = null
            }
        }
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            progress = "Сборка ZIP с фото…"
            try {
                val file = File(context.cacheDir, "semya-hdlabs.zip")
                val document = repo.exportDocument()
                withContext(Dispatchers.IO) { BackupZip.write(context, document, file) }
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                message = "Архив с фото сохранён"
            } catch (e: Exception) {
                message = e.message ?: "Не удалось собрать архив"
            } finally {
                progress = null
            }
        }
    }
    val exportGed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            progress = "Экспорт GEDCOM…"
            try {
                val text = repo.exportGedcom()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                message = "GEDCOM сохранён"
            } catch (e: Exception) {
                message = e.message ?: "Не удалось экспортировать GEDCOM"
            } finally {
                progress = null
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            progress = "Чтение файла…"
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val document = withContext(Dispatchers.IO) { repo.parseImport(bytes) }
                pendingImport = bytes
                preview = "Людей: ${document.people.size}, связей: ${document.unions.size}. Текущее древо будет заменено. Перед заменой сохранится локальная версия."
            } catch (e: Exception) {
                message = e.message ?: "Не удалось прочитать файл"
            } finally {
                progress = null
            }
        }
    }

    val gitOwner = settings.githubOwner.ifBlank { FamilyGitConfig.embeddedOwner }
    val gitRepo = settings.githubRepo.ifBlank { FamilyGitConfig.embeddedRepo }
    val gitBranch = settings.githubBranch.ifBlank { FamilyGitConfig.embeddedBranch }
    val gitPath = settings.githubPath.ifBlank { FamilyGitConfig.embeddedPath }

    HdScaffold(title = "Настройки разработчика", onBack = { nav.popBackStack() }, progressMessage = progress) {
        if (!unlocked) {
            HdCard {
                Text("Доступ", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Служебные функции: экспорт, Git, бэкапы и обновления.",
                    color = HdTextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                HdTextField(
                    gatePassword,
                    { gatePassword = it; gateError = false },
                    "Пароль",
                    keyboardType = KeyboardType.Number,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (gateError) {
                    Text("Неверный пароль", color = HdOrange, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                HdPrimaryButton("Войти") {
                    if (gatePassword == DEVELOPER_SETTINGS_PASSWORD) {
                        unlocked = true
                        gateError = false
                    } else {
                        gateError = true
                    }
                }
            }
            return@HdScaffold
        }
        HdCard {
            Text("Формат древа", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "На телефоне древо хранится в JSON HDLabs версии 1. Обновление приложения ставится поверх и не стирает карточки: новые поля можно добавлять, старые остаются. " +
                    "GEDCOM 5.5.1 нужен для FamilySearch, Ancestry, MyHeritage, Geni, Gramps, «Древа жизни» и webtrees. ZIP — полный бэкап вместе с фото.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        HdSecondaryButton("Экспорт JSON") { exportJson.launch("semya-hdlabs.json") }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Экспорт ZIP с фото") { exportZip.launch("semya-hdlabs.zip") }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Экспорт GEDCOM") { exportGed.launch("semya-hdlabs.ged") }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Импорт") { importFile.launch(arrayOf("*/*")) }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("История и откат") { nav.navigate(Routes.HISTORY) }
        Spacer(Modifier.height(16.dp))
        HdCard {
            Text("Git", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            if (FamilyGitConfig.isEmbedded()) {
                Text(
                    "Семейный Git уже встроен в приложение. Родственникам ничего вводить не нужно: древо само уходит в ваш репозиторий и оттуда обновляется. Токен в телефон не сохраняется отдельно.",
                    color = HdTextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                Text(
                    "$gitOwner/$gitRepo, ветка $gitBranch, файл $gitPath",
                    color = HdOrange,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Если Git пишет «не найден» — сначала нажмите «Отправить в Git сейчас», чтобы создать файл в репозитории.",
                    color = HdTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Text(
                    "Приватный репозиторий GitHub хранит версии JSON без фото. Для сборки только для семьи токен можно вшить в local.properties — см. local.properties.example.",
                    color = HdTextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                HdTextField(token, { token = it }, "Токен GitHub", visualTransformation = PasswordVisualTransformation())
                HdTextField(owner, { owner = it }, "Владелец")
                HdTextField(repoName, { repoName = it }, "Репозиторий")
                HdTextField(branch, { branch = it }, "Ветка")
                HdTextField(path, { path = it }, "Путь к файлу")
                Spacer(Modifier.height(8.dp))
                HdPrimaryButton(
                    "Сохранить Git",
                    loading = gitSaveLoading,
                    loadingText = "Сохранение…",
                ) {
                    scope.launch {
                        gitSaveLoading = true
                        progress = "Сохранение настроек Git…"
                        try {
                            app.container.settingsRepository.update {
                                it.copy(
                                    githubToken = token,
                                    githubOwner = owner,
                                    githubRepo = repoName,
                                    githubBranch = branch,
                                    githubPath = path,
                                )
                            }
                            message = "Настройки Git сохранены"
                        } finally {
                            gitSaveLoading = false
                            progress = null
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton(
                "Отправить в Git сейчас",
                loading = gitPushLoading,
                loadingText = "Отправка в Git…",
                enabled = settings.gitConfigured,
            ) {
                scope.launch {
                    gitPushLoading = true
                    progress = "Отправка древа в Git…"
                    try {
                        val result = repo.pushNow()
                        message = result
                        gitPushResult = result
                        gitPushDialog = result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val text = e.message ?: "Не удалось отправить в Git"
                        message = text
                        gitPushResult = text
                        gitPushDialog = text
                        AppEventLog.error("git", text, e)
                    } finally {
                        gitPushLoading = false
                        progress = null
                    }
                }
            }
            gitPushResult?.let { text ->
                Text(text, color = HdOrange, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        HdCard {
            Text("Бэкап в Telegram", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Если указаны токен бота и chat id, архив уйдёт вам сразу. Иначе откроется Telegram: выберите чат с разработчиком и отправьте файл.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            HdTextField(botToken, { botToken = it }, "Токен бота", visualTransformation = PasswordVisualTransformation())
            HdTextField(chatId, { chatId = it }, "Chat id", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            HdPrimaryButton("Сохранить Telegram") {
                scope.launch {
                    app.container.settingsRepository.update {
                        it.copy(telegramBotToken = botToken, telegramChatId = chatId)
                    }
                    message = "Настройки Telegram сохранены"
                }
            }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton(
                "Отправить бэкап в Telegram",
                loading = telegramBackupLoading,
                loadingText = "Подготовка бэкапа…",
            ) {
                scope.launch {
                    telegramBackupLoading = true
                    progress = "Подготовка бэкапа для Telegram…"
                    try {
                        val document = repo.exportDocument()
                        val file = File(context.cacheDir, "semya-hdlabs-backup.zip")
                        withContext(Dispatchers.IO) { BackupZip.write(context, document, file) }
                        val current = settings
                        if (current.telegramBotConfigured) {
                            progress = "Отправка в Telegram…"
                            withContext(Dispatchers.IO) {
                                TelegramBackup.sendToBot(
                                    current.telegramBotToken,
                                    current.telegramChatId,
                                    file,
                                    "Бэкап древа HDLabs, ${document.people.size} чел.",
                                )
                            }
                            message = "Бэкап отправлен в Telegram"
                        } else {
                            TelegramBackup.share(context, file)
                            message = "Выберите в Telegram чат и отправьте архив"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "Не удалось отправить бэкап"
                    } finally {
                        telegramBackupLoading = false
                        progress = null
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HdCard {
            Text("Стереть древо (временно)", color = HdOrange, fontWeight = FontWeight.SemiBold)
            Text(
                "Удаляет всех людей и связи на этом телефоне. Перед очисткой сохраняется снимок в «История». " +
                    "Для отладки пустого древа или повторного импорта.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            HdSecondaryButton(
                "Стереть всё древо",
                loading = wipeLoading,
                loadingText = "Очистка…",
            ) { wipeStep = 1 }
        }
        if (message.isNotBlank()) Text(message, color = HdTextSecondary, modifier = Modifier.padding(top = 12.dp))
        Text("Версия ${BuildConfig.VERSION_NAME}", color = HdTextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
    }

    gitPushDialog?.let { text ->
        AlertDialog(
            onDismissRequest = { gitPushDialog = null },
            title = { Text("Git") },
            text = { Text(text, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { gitPushDialog = null } },
        )
    }

    if (wipeStep == 1) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text("Стереть древо?") },
            text = {
                Text(
                    "Все карточки и связи на телефоне будут удалены. Снимок перед удалением попадёт в «История и откат».",
                    color = HdTextSecondary,
                )
            },
            confirmButton = { HdPrimaryButton("Продолжить") { wipeStep = 2 } },
            dismissButton = { HdSecondaryButton("Отмена") { wipeStep = 0 } },
        )
    }
    if (wipeStep == 2) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text("Подтвердите очистку") },
            text = { Text("Это необратимо на телефоне (в Git останется старая версия, пока не отправите снова).", color = HdTextSecondary) },
            confirmButton = {
                HdPrimaryButton("Стереть", loading = wipeLoading, loadingText = "…") {
                    scope.launch {
                        wipeLoading = true
                        progress = "Очистка древа…"
                        try {
                            message = repo.wipeTree()
                            wipeStep = 0
                        } catch (e: Exception) {
                            message = e.message ?: "Не удалось очистить"
                        } finally {
                            wipeLoading = false
                            progress = null
                        }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Отмена") { wipeStep = 0 } },
        )
    }

    if (preview.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { preview = ""; pendingImport = null },
            title = { Text("Импорт древа") },
            text = { Text(preview, color = HdTextSecondary) },
            confirmButton = {
                HdPrimaryButton("Заменить древо") {
                    val bytes = pendingImport
                    preview = ""
                    pendingImport = null
                    if (bytes != null) {
                        scope.launch {
                            progress = "Импорт древа…"
                            try {
                                repo.importBytes(bytes)
                                message = "Импорт выполнен"
                            } catch (e: Exception) {
                                message = e.message ?: "Не удалось импортировать"
                            } finally {
                                progress = null
                            }
                        }
                    }
                }
            },
            dismissButton = {
                HdSecondaryButton("Отмена") { preview = ""; pendingImport = null }
            },
        )
    }
}

@Composable
fun HistoryScreen(nav: NavHostController) {
    val context = LocalContext.current
    val repo = (context.applicationContext as TreeXApplication).container.treeRepository
    val scope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf<List<SnapshotInfo>>(emptyList()) }
    var commits by remember { mutableStateOf<List<GitHubTreeSync.RemoteCommit>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var pendingSnapshot by remember { mutableStateOf<SnapshotInfo?>(null) }
    var pendingCommit by remember { mutableStateOf<GitHubTreeSync.RemoteCommit?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var gitLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { snapshots = repo.snapshots() }

    HdScaffold(title = "История", onBack = { nav.popBackStack() }, progressMessage = progress) {
        Text("Локальные снимки", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
        Text("Снимок делается перед правкой, импортом и удалением. Хранятся последние 30.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        if (snapshots.isEmpty()) Text("Снимков пока нет", color = HdTextSecondary)
        snapshots.forEach { snapshot ->
            val who = snapshot.author?.let { "$it · " } ?: ""
            HdSecondaryButton("$who${snapshot.label} · ${snapshot.people} чел.") { pendingSnapshot = snapshot }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton(
            "Показать версии Git",
            loading = gitLoading,
            loadingText = "Чтение Git…",
        ) {
            scope.launch {
                gitLoading = true
                progress = "Чтение версий в Git…"
                try {
                    commits = repo.remoteCommits()
                    message = ""
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = e.message ?: "Не удалось прочитать Git"
                } finally {
                    gitLoading = false
                    progress = null
                }
            }
        }
        commits.forEach { commit ->
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("${commit.date} ${commit.message}".take(80)) { pendingCommit = commit }
        }
        if (message.isNotBlank()) Text(message, color = HdTextSecondary, modifier = Modifier.padding(top = 12.dp))
    }

    pendingSnapshot?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingSnapshot = null },
            title = { Text("Откатить к снимку?") },
            text = { Text("Текущее древо тоже сохранится в истории.", color = HdTextSecondary) },
            confirmButton = {
                HdPrimaryButton("Откатить") {
                    pendingSnapshot = null
                    scope.launch {
                        try {
                            repo.restoreSnapshot(snapshot.fileName)
                            snapshots = repo.snapshots()
                            message = "Древо восстановлено"
                        } catch (e: Exception) {
                            message = e.message ?: "Не удалось откатить"
                        }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Отмена") { pendingSnapshot = null } },
        )
    }
    pendingCommit?.let { commit ->
        AlertDialog(
            onDismissRequest = { pendingCommit = null },
            title = { Text("Откатить к версии Git?") },
            text = { Text(commit.message, color = HdTextSecondary) },
            confirmButton = {
                HdPrimaryButton("Откатить") {
                    pendingCommit = null
                    scope.launch {
                        try {
                            repo.restoreRemote(commit.sha)
                            snapshots = repo.snapshots()
                            message = "Версия из Git восстановлена"
                        } catch (e: Exception) {
                            message = e.message ?: "Не удалось откатить"
                        }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Отмена") { pendingCommit = null } },
        )
    }
}

@Composable
private fun UpdateSection(onProgress: (String?) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }

    fun download(info: UpdateInfo) {
        scope.launch {
            checking = true
            onProgress("Загрузка обновления…")
            try {
                val file = withContext(Dispatchers.IO) {
                    val target = File(context.cacheDir, info.apkFileName)
                    ApkDownloadHelper.download(info.apkUrl, target)
                    target
                }
                InstallHelper.promptInstall(context, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = NetworkHelper.toUserMessage(e) ?: (e.message ?: "Не удалось скачать обновление")
            } finally {
                checking = false
                onProgress(null)
            }
        }
    }

    HdSecondaryButton(
        "Обновить приложение",
        enabled = !checking,
        loading = checking,
        loadingText = "Проверка обновлений…",
    ) {
        scope.launch {
            checking = true
            onProgress("Проверка обновлений…")
            try {
                if (!ReleaseUpdateConfig.isConfigured()) {
                    message = "Репозиторий обновлений не настроен"
                    return@launch
                }
                val info = withContext(Dispatchers.IO) {
                    app.container.updateSource(ReleaseUpdateConfig.githubOwner, ReleaseUpdateConfig.githubRepo)
                        .checkForUpdate(BuildConfig.VERSION_CODE)
                }
                if (info == null) message = "У вас последняя версия" else pending = info
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = NetworkHelper.toUserMessage(e) ?: (e.message ?: "Не удалось проверить обновления")
            } finally {
                checking = false
                onProgress(null)
            }
        }
    }
    pending?.let { info ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Доступно обновление") },
            text = { Text("Версия ${info.versionName}. Установить поверх текущей? Древо сохранится.", color = HdTextSecondary) },
            confirmButton = {
                HdPrimaryButton("Обновить") {
                    pending = null
                    download(info)
                }
            },
            dismissButton = { HdSecondaryButton("Не сейчас") { pending = null } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Обновление") },
            text = { Text(text, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { message = null } },
        )
    }
}
