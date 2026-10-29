package ru.hdlabs.treex.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.hdlabs.treex.data.TreeRepository
import ru.hdlabs.treex.data.kinship.KinshipRepository
import ru.hdlabs.treex.data.local.AppDatabase
import ru.hdlabs.treex.data.settings.SettingsRepository
import ru.hdlabs.treex.update.GitHubUpdateSource

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settingsRepository = SettingsRepository(appContext)
    private val database = AppDatabase.getInstance(appContext)
    val treeRepository = TreeRepository(
        context = appContext,
        database = database,
        settingsRepository = settingsRepository,
        scope = scope,
    )
    val kinshipRepository = KinshipRepository(
        database = database,
        onTreeChanged = { treeRepository.notifyKinshipChanged() },
    ).also { treeRepository.bindKinshipRepository(it) }

    fun updateSource(owner: String, repo: String) = GitHubUpdateSource(owner, repo)
}
