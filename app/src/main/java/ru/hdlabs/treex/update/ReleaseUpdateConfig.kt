package ru.hdlabs.treex.update

import ru.hdlabs.treex.BuildConfig

object ReleaseUpdateConfig {
    val githubOwner: String = BuildConfig.GITHUB_RELEASES_OWNER.trim()
    val githubRepo: String = BuildConfig.GITHUB_RELEASES_REPO.trim()

    fun isConfigured(): Boolean = githubOwner.isNotEmpty() && githubRepo.isNotEmpty()
}
