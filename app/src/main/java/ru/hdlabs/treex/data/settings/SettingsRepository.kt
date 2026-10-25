package ru.hdlabs.treex.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.hdlabs.treex.sync.FamilyGitConfig

private val Context.settingsStore by preferencesDataStore("treex-settings")

data class AppSettings(
    val githubToken: String = "",
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val githubPath: String = "family/tree.json",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val homePersonId: String = "",
) {
    val gitConfigured: Boolean
        get() = githubToken.isNotBlank() && githubOwner.isNotBlank() && githubRepo.isNotBlank()

    val telegramBotConfigured: Boolean
        get() = telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private val tokenKey = stringPreferencesKey("github_token")
    private val ownerKey = stringPreferencesKey("github_owner")
    private val repoKey = stringPreferencesKey("github_repo")
    private val branchKey = stringPreferencesKey("github_branch")
    private val pathKey = stringPreferencesKey("github_path")
    private val botTokenKey = stringPreferencesKey("telegram_bot_token")
    private val chatKey = stringPreferencesKey("telegram_chat_id")
    private val homeKey = stringPreferencesKey("home_person_id")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        FamilyGitConfig.resolve(
            AppSettings(
                githubToken = prefs[tokenKey].orEmpty(),
                githubOwner = prefs[ownerKey].orEmpty(),
                githubRepo = prefs[repoKey].orEmpty(),
                githubBranch = prefs[branchKey].orEmpty().ifBlank { "main" },
                githubPath = prefs[pathKey].orEmpty().ifBlank { "family/tree.json" },
                telegramBotToken = prefs[botTokenKey].orEmpty(),
                telegramChatId = prefs[chatKey].orEmpty(),
                homePersonId = prefs[homeKey].orEmpty(),
            ),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val current = AppSettings(
                githubToken = prefs[tokenKey].orEmpty(),
                githubOwner = prefs[ownerKey].orEmpty(),
                githubRepo = prefs[repoKey].orEmpty(),
                githubBranch = prefs[branchKey].orEmpty().ifBlank { "main" },
                githubPath = prefs[pathKey].orEmpty().ifBlank { "family/tree.json" },
                telegramBotToken = prefs[botTokenKey].orEmpty(),
                telegramChatId = prefs[chatKey].orEmpty(),
                homePersonId = prefs[homeKey].orEmpty(),
            )
            val next = transform(current)
            prefs[tokenKey] = next.githubToken.trim()
            prefs[ownerKey] = next.githubOwner.trim()
            prefs[repoKey] = next.githubRepo.trim()
            prefs[branchKey] = next.githubBranch.trim().ifBlank { "main" }
            prefs[pathKey] = next.githubPath.trim().ifBlank { "family/tree.json" }
            prefs[botTokenKey] = next.telegramBotToken.trim()
            prefs[chatKey] = next.telegramChatId.trim()
            prefs[homeKey] = next.homePersonId.trim()
        }
    }
}
