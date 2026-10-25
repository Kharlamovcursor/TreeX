package ru.hdlabs.treex.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import java.io.File

@Composable
fun SettingsScreen(nav: NavHostController) {
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
            try {
                val text = TreeCodec.encode(repo.exportDocument())
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                message = "JSON сохранён"
            } catch (e: Exception) {
                message = e.message ?: "Не удалось экспортировать"
            }
        }
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val file = File(context.cacheDir, "semya-hdlabs.zip")
                val document = repo.exportDocument()
                withContext(Dispatchers.IO) { BackupZip.write(context, document, file) }
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                message = "Архив с фото сохранён"
            } catch (e: Exception) {
                message = e.message ?: "Не удалось собрать архив"
            }
        }
    }
    val exportGed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = repo.exportGedcom()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                message = "GEDCOM сохранён"
            } catch (e: Exception) {
                message = e.message ?: "Не удалось экспортировать GEDCOM"
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val document = withContext(Dispatchers.IO) { repo.parseImport(bytes) }
                pendingImport = bytes
                preview = "Людей: ${document.people.size}, связей: ${document.unions.size}. Текущее древо будет заменено. Перед заменой сохранится локальная версия."
            } catch (e: Exception) {
                message = e.message ?: "Не удалось прочитать файл"
            }
        }
    }

    HdScaffold(title = "Настройки", onBack = { nav.popBackStack() }) {
        HdSecondaryButton("Справка по древу") { nav.navigate(Routes.TREE_HELP) }
        Spacer(Modifier.height(12.dp))
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
                    "${settings.githubOwner}/${settings.githubRepo}, ветка ${settings.githubBranch}, файл ${settings.githubPath}",
                    color = HdOrange,
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
                HdPrimaryButton("Сохранить Git") {
                    scope.launch {
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
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("Отправить в Git сейчас") {
                scope.launch {
                    try {
                        message = repo.pushNow()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "Не удалось отправить в Git"
                    }
                }
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
            HdSecondaryButton("Отправить бэкап в Telegram") {
                scope.launch {
                    try {
                        val document = repo.exportDocument()
                        val file = File(context.cacheDir, "semya-hdlabs-backup.zip")
                        withContext(Dispatchers.IO) { BackupZip.write(context, document, file) }
                        val current = settings
                        if (current.telegramBotConfigured) {
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
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Обновление приложения ставится поверх текущей версии и не удаляет древо, фото и историю. Не удаляйте приложение перед обновлением.",
            color = HdTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        UpdateSection()
        Spacer(Modifier.height(16.dp))
        Text("Связь с разработчиком", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
        Text(
            "Telegram @Yakofffff или мессенджер MAX, Yakoff.",
            color = HdTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        HdSecondaryButton("Связаться в Telegram") {
            TelegramFeedback.open(context).onFailure { message = it.message ?: "Не удалось открыть Telegram" }
        }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Связаться в MAX") {
            MaxFeedback.open(context).onFailure { message = it.message ?: "Не удалось открыть MAX" }
        }
        if (message.isNotBlank()) Text(message, color = HdTextSecondary, modifier = Modifier.padding(top = 12.dp))
        Text("Версия ${BuildConfig.VERSION_NAME}", color = HdTextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
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
                            try {
                                repo.importBytes(bytes)
                                message = "Импорт выполнен"
                            } catch (e: Exception) {
                                message = e.message ?: "Не удалось импортировать"
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
    LaunchedEffect(Unit) { snapshots = repo.snapshots() }

    HdScaffold(title = "История", onBack = { nav.popBackStack() }) {
        Text("Локальные снимки", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
        Text("Снимок делается перед правкой, импортом и удалением. Хранятся последние 30.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        if (snapshots.isEmpty()) Text("Снимков пока нет", color = HdTextSecondary)
        snapshots.forEach { snapshot ->
            HdSecondaryButton("${snapshot.label} · ${snapshot.people} чел.") { pendingSnapshot = snapshot }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Показать версии Git") {
            scope.launch {
                try {
                    commits = repo.remoteCommits()
                    if (commits.isEmpty()) message = "В Git пока нет версий"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = e.message ?: "Не удалось прочитать Git"
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
private fun UpdateSection() {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }

    fun download(info: UpdateInfo) {
        scope.launch {
            checking = true
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
                message = e.message ?: "Не удалось скачать обновление"
            } finally {
                checking = false
            }
        }
    }

    HdSecondaryButton(if (checking) "Проверка…" else "Обновить приложение", enabled = !checking) {
        scope.launch {
            checking = true
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
                message = e.message ?: "Не удалось проверить обновления"
            } finally {
                checking = false
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
