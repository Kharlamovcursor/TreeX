package ru.hdlabs.treex.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.hdlabs.treex.data.TreeRepository
import ru.hdlabs.treex.data.local.AppDatabase
import ru.hdlabs.treex.data.settings.SettingsRepository
import ru.hdlabs.treex.relatives.RelativesTreeJsEngine
import ru.hdlabs.treex.update.GitHubUpdateSource

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val relativesTreeEngine = RelativesTreeJsEngine()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settingsRepository = SettingsRepository(appContext)
    val treeRepository = TreeRepository(
        context = appContext,
        database = AppDatabase.getInstance(appContext),
        settingsRepository = settingsRepository,
        scope = scope,
    )

    fun updateSource(owner: String, repo: String) = GitHubUpdateSource(owner, repo)
}
