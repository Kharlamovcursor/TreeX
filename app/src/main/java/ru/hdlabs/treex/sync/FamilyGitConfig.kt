package ru.hdlabs.treex.sync

import ru.hdlabs.treex.BuildConfig
import ru.hdlabs.treex.data.settings.AppSettings

object FamilyGitConfig {
    val embeddedToken: String = BuildConfig.GITHUB_FAMILY_TOKEN.trim()
    val embeddedOwner: String = BuildConfig.GITHUB_FAMILY_OWNER.trim()
    val embeddedRepo: String = BuildConfig.GITHUB_FAMILY_REPO.trim()
    val embeddedBranch: String = BuildConfig.GITHUB_FAMILY_BRANCH.trim().ifBlank { "main" }
    val embeddedPath: String = BuildConfig.GITHUB_FAMILY_PATH.trim().ifBlank { "family/tree.json" }

    fun isEmbedded(): Boolean =
        embeddedToken.isNotBlank() && embeddedOwner.isNotBlank() && embeddedRepo.isNotBlank()

    fun resolve(settings: AppSettings): AppSettings {
        if (!isEmbedded()) return settings
        // Семейная сборка: всегда встроенный репозиторий, чтобы старые значения из DataStore не ломали Git.
        return settings.copy(
            githubToken = embeddedToken,
            githubOwner = embeddedOwner,
            githubRepo = embeddedRepo,
            githubBranch = embeddedBranch,
            githubPath = embeddedPath,
        )
    }
}
