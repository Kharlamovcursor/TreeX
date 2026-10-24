package ru.hdlabs.treex.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import ru.hdlabs.treex.ui.HistoryScreen
import ru.hdlabs.treex.ui.PersonDetailScreen
import ru.hdlabs.treex.ui.PersonEditorScreen
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
import java.io.File

object Routes {
    const val TREE = "tree"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
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
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(5_000)
        if (!ReleaseUpdateConfig.isConfigured()) return@LaunchedEffect
        val info = runCatching {
            withContext(Dispatchers.IO) {
                app.container.updateSource(ReleaseUpdateConfig.githubOwner, ReleaseUpdateConfig.githubRepo)
                    .checkForUpdate(BuildConfig.VERSION_CODE)
            }
        }.getOrNull()
        if (info != null) pendingUpdate = info
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
        composable(Routes.HISTORY) { HistoryScreen(nav) }
        composable(
            Routes.PERSON,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
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
                HdPrimaryButton("Обновить") {
                    pendingUpdate = null
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val target = File(context.cacheDir, info.apkFileName)
                                ApkDownloadHelper.download(info.apkUrl, target)
                                target
                            }
                            InstallHelper.promptInstall(context, file)
                        } catch (e: Exception) {
                            updateError = e.message ?: "Не удалось скачать обновление"
                        }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Не сейчас") { pendingUpdate = null } },
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
