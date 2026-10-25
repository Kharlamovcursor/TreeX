package ru.hdlabs.treex.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hdlabs.treex.data.settings.AppSettings

class FamilyGitConfigTest {
    @Test
    fun resolveFillsOnlyBlankFieldsWhenEmbedded() {
        if (!FamilyGitConfig.isEmbedded()) return
        val resolved = FamilyGitConfig.resolve(AppSettings())
        assertEquals(FamilyGitConfig.embeddedOwner, resolved.githubOwner)
        assertEquals(FamilyGitConfig.embeddedRepo, resolved.githubRepo)
        assertEquals(FamilyGitConfig.embeddedToken, resolved.githubToken)
    }

    @Test
    fun embeddedBuildIgnoresStaleDatastoreOverrides() {
        if (!FamilyGitConfig.isEmbedded()) return
        val resolved = FamilyGitConfig.resolve(
            AppSettings(githubOwner = "wrong", githubRepo = "missing", githubToken = "bad"),
        )
        assertEquals(FamilyGitConfig.embeddedOwner, resolved.githubOwner)
        assertEquals(FamilyGitConfig.embeddedRepo, resolved.githubRepo)
        assertEquals(FamilyGitConfig.embeddedToken, resolved.githubToken)
    }

    @Test
    fun manualSettingsStayWhenNotEmbedded() {
        if (FamilyGitConfig.isEmbedded()) return
        val resolved = FamilyGitConfig.resolve(
            AppSettings(githubOwner = "custom", githubRepo = "tree", githubToken = "user-token"),
        )
        assertEquals("custom", resolved.githubOwner)
        assertEquals("tree", resolved.githubRepo)
        assertEquals("user-token", resolved.githubToken)
    }
}
