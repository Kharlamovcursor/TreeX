package ru.hdlabs.treex.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.BuildConfig
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.data.RemoteRefresh
import ru.hdlabs.treex.data.settings.AppSettings
import ru.hdlabs.treex.ui.HistoryScreen
import ru.hdlabs.treex.ui.UserOnboardingScreen
import ru.hdlabs.treex.ui.TreeHelpScreen
import ru.hdlabs.treex.ui.UserGuideScreen
import ru.hdlabs.treex.ui.PersonDetailScreen
import ru.hdlabs.treex.ui.PersonEditorScreen
import ru.hdlabs.treex.ui.DeveloperSettingsScreen
import ru.hdlabs.treex.ui.SettingsScreen
import ru.hdlabs.treex.ui.TreeScreen
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.motion.LocalHdMotion
import ru.hdlabs.treex.ui.theme.HdTextSecondary
import ru.hdlabs.treex.update.ApkDownloadHelper
import ru.hdlabs.treex.update.InstallHelper
import ru.hdlabs.treex.update.ReleaseUpdateConfig
import ru.hdlabs.treex.update.UpdateInfo
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.util.NetworkHelper
import java.io.File

object Routes {
    const val TREE = "tree"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val TREE_HELP = "tree-help"
    const val USER_GUIDE = "user-guide"
    const val DEVELOPER_SETTINGS = "developer-settings"
    const val PERSON = "person/{id}"
    const val EDIT = "edit/{id}?role={role}&anchor={anchor}"
    fun person(id: String) = "person/$id"
    fun edit(id: String, role: String = "", anchor: String = "") = "edit/$id?role=$role&anchor=$anchor"
}

@Composable
fun TreeNavHost() {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val nav = rememberNavController()
    val motion = LocalHdMotion.current
    val scope = rememberCoroutineScope()
    val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var pendingGit by remember { mutableStateOf<RemoteRefresh?>(null) }
    var gitNotice by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var askUpload by remember { mutableStateOf(false) }
    var closeAfterUpload by remember { mutableStateOf(false) }
    val pendingUpload by app.container.treeRepository.pendingUpload.collectAsStateWithLifecycle()
    val activity = context as? android.app.Activity

    if (!settings.userProfileComplete) {
        UserOnboardingScreen()
        return
    }

    LaunchedEffect(Unit) {
        app.leaveHint.collect {
            if (app.container.treeRepository.pendingUpload.value) {
                closeAfterUpload = false
                askUpload = true
            }
        }
    }
    BackHandler(enabled = nav.previousBackStackEntry == null && pendingUpload && !askUpload) {
        closeAfterUpload = true
        askUpload = true
    }

    LaunchedEffect(settings.userProfileComplete) {
        if (!settings.userProfileComplete) return@LaunchedEffect
        launch {
            repeat(4) { attempt ->
                if (attempt > 0) delay(2_500L)
                if (!ReleaseUpdateConfig.isConfigured()) return@launch
                if (!NetworkHelper.isOnline(context) && attempt < 3) return@repeat
                val info = runCatching {
                    withContext(Dispatchers.IO) {
                        app.container.updateSource(ReleaseUpdateConfig.githubOwner, ReleaseUpdateConfig.githubRepo)
                            .checkForUpdate(BuildConfig.VERSION_CODE)
                    }
                }.getOrNull()
                if (info != null) {
                    AppEventLog.info("update", "Доступна версия ${info.versionName}")
                    pendingUpdate = info
                    return@launch
                }
            }
        }
        launch {
            runCatching { app.container.treeRepository.repairLivingKinship() }
            val issues = runCatching { app.container.treeRepository.validateTree() }.getOrDefault(emptyList())
            if (issues.isNotEmpty()) {
                gitNotice = "Обнаружено ${issues.size} проблем в данных. Откройте карточку или настройки."
                AppEventLog.info("integrity", issues.first().message)
            }
        }
        launch {
            val git = runCatching { app.container.treeRepository.syncTreeFromGitIfNeeded() }
                .onFailure { error ->
                    AppEventLog.error("git", "Сбой синхронизации при запуске", error)
                }
                .getOrNull()
            when (git) {
                is RemoteRefresh.Diverged -> pendingGit = git
                is RemoteRefresh.Updated -> {
                    AppEventLog.info("git", "Автообновление при запуске: ${git.count} чел.")
                    gitNotice = "Древо загружено из Git: ${git.count} чел."
                }
                is RemoteRefresh.Failed -> {
                    if (!NetworkHelper.looksLikeNetworkIssue(git.message)) {
                        gitNotice = git.message
                    }
                }
                else -> Unit
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.TREE,
        enterTransition = {
            fadeIn(tween(motion.screenEnterMs)) + slideInHorizontally(tween(motion.screenEnterMs)) { width ->
                width / motion.screenSlideDivisor
            }
        },
        exitTransition = { fadeOut(tween(motion.screenExitMs)) },
        popEnterTransition = {
            fadeIn(tween(motion.screenEnterMs)) + slideInHorizontally(tween(motion.screenEnterMs)) { width ->
                -width / motion.screenSlideDivisor
            }
        },
        popExitTransition = {
            fadeOut(tween(motion.screenExitMs)) + slideOutHorizontally(tween(motion.screenExitMs)) { width ->
                width / motion.screenSlideDivisor
            }
        },
    ) {
        composable(Routes.TREE) { entry ->
            val highlight by entry.savedStateHandle.getStateFlow("highlight", "").collectAsStateWithLifecycle()
            TreeScreen(nav, highlight)
        }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.DEVELOPER_SETTINGS) { DeveloperSettingsScreen(nav) }
        composable(Routes.HISTORY) { HistoryScreen(nav) }
        composable(Routes.TREE_HELP) { TreeHelpScreen(nav) }
        composable(Routes.USER_GUIDE) { UserGuideScreen(nav) }
        composable(
            Routes.PERSON,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = {
                fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(340, easing = FastOutSlowInEasing),
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.35f),
                    )
            },
            exitTransition = { fadeOut(tween(220)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = {
                fadeOut(tween(260, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.35f),
                    )
            },
        ) { entry ->
            PersonDetailScreen(entry.arguments?.getString("id").orEmpty(), nav)
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType; defaultValue = "" },
                navArgument("anchor") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            PersonEditorScreen(
                routeId = entry.arguments?.getString("id").orEmpty(),
                role = entry.arguments?.getString("role").orEmpty(),
                anchor = entry.arguments?.getString("anchor").orEmpty(),
                nav = nav,
            )
        }
    }

    pendingUpdate?.let { info ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text("Доступно обновление") },
            text = {
                Text(
                    "Версия ${info.versionName}. Установить её поверх текущей? Древо, фото и история останутся.",
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton(
                    if (updateDownloading) "Загрузка…" else "Обновить",
                    enabled = !updateDownloading,
                ) {
                    scope.launch {
                        updateDownloading = true
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val target = File(context.cacheDir, info.apkFileName)
                                ApkDownloadHelper.download(info.apkUrl, target)
                                target
                            }
                            pendingUpdate = null
                            InstallHelper.promptInstall(context, file)
                        } catch (e: Exception) {
                            updateError = NetworkHelper.toUserMessage(e)
                                ?: (e.message ?: "Не удалось скачать обновление")
                        } finally {
                            updateDownloading = false
                        }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Не сейчас") { pendingUpdate = null } },
        )
    }
    pendingGit?.let { git ->
        val count = when (git) {
            is RemoteRefresh.Incoming -> git.count
            is RemoteRefresh.Diverged -> git.count
            else -> 0
        }
        val diverged = git is RemoteRefresh.Diverged
        AlertDialog(
            onDismissRequest = { pendingGit = null },
            title = { Text(if (diverged) "В Git другая версия" else "В Git есть обновление") },
            text = {
                Text(
                    if (diverged) {
                        "В Git $count чел., и на телефоне есть свои правки. Загрузить Git или отправить телефон?"
                    } else {
                        "В Git более новое древо, $count чел. Загрузить его на телефон?"
                    },
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton("Загрузить") {
                    pendingGit = null
                    scope.launch {
                        gitNotice = when (val result = app.container.treeRepository.refreshFromGit()) {
                            is RemoteRefresh.Updated -> "Информация обновлена. В древе ${result.count} чел."
                            is RemoteRefresh.Failed -> result.message
                            RemoteRefresh.Unchanged -> "Телефон и Git уже совпадают."
                            else -> "Не удалось загрузить древо из Git"
                        }
                    }
                }
            },
            dismissButton = {
                HdSecondaryButton("Не сейчас") { pendingGit = null }
            },
        )
    }
    if (askUpload) {
        AlertDialog(
            onDismissRequest = { askUpload = false },
            title = { Text("Отправить изменения в Git?") },
            text = {
                Text(
                    "На телефоне есть правки, которых ещё нет на сервере.",
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton("Отправить") {
                    askUpload = false
                    scope.launch {
                        gitNotice = runCatching {
                            when (val status = app.container.treeRepository.inspectGit()) {
                                is RemoteRefresh.LocalAhead -> app.container.treeRepository.pushNow()
                                is RemoteRefresh.Incoming, is RemoteRefresh.Diverged -> when (
                                    val loaded = app.container.treeRepository.refreshFromGit()
                                ) {
                                    is RemoteRefresh.Updated ->
                                        "На сервере была другая версия. Телефон обновлён из Git: ${loaded.count} чел."
                                    is RemoteRefresh.Failed -> loaded.message
                                    else -> "На сервере была другая версия. Телефон обновлён из Git."
                                }
                                RemoteRefresh.Unchanged -> "Телефон и Git уже совпадают."
                                is RemoteRefresh.Failed -> status.message
                                RemoteRefresh.NotConfigured -> "Git не настроен."
                                else -> "Не удалось отправить изменения."
                            }
                        }.getOrElse { it.message ?: "Не удалось отправить в Git" }
                        if (closeAfterUpload) activity?.finish()
                    }
                }
            },
            dismissButton = {
                HdSecondaryButton("Не сейчас") {
                    askUpload = false
                    if (closeAfterUpload) activity?.finish()
                }
            },
        )
    }
    gitNotice?.let { text ->
        AlertDialog(
            onDismissRequest = { gitNotice = null },
            title = { Text("Git") },
            text = { Text(text, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { gitNotice = null } },
        )
    }
    updateError?.let { text ->
        AlertDialog(
            onDismissRequest = { updateError = null },
            title = { Text("Обновление") },
            text = { Text(text, color = HdTextSecondary) },
            confirmButton = { HdPrimaryButton("Понятно") { updateError = null } },
        )
    }
}
