package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hdlabs.treex.data.settings.AppSettings

class UserProfileTest {
    @Test
    fun formatUserFioSkipsBlankParts() {
        assertEquals("Иванов Иван", formatUserFio("Иванов", "Иван", ""))
        assertEquals("Иванов Иван Петрович", formatUserFio("Иванов", "Иван", "Петрович"))
    }

    @Test
    fun treeGitCommitMessageIncludesAuthorWhenPresent() {
        assertEquals("Древо: 5 чел.", treeGitCommitMessage("", 5))
        assertEquals("Иванов Иван: Древо: 5 чел.", treeGitCommitMessage("Иванов Иван", 5))
    }

    @Test
    fun appSettingsUserFio() {
        val settings = AppSettings(userLastName = "Иванов", userFirstName = "Иван", userMiddleName = "П.")
        assertEquals("Иванов Иван П.", settings.userFio())
        assertEquals(true, settings.userProfileComplete)
        assertEquals(false, AppSettings(userLastName = "Иванов").userProfileComplete)
    }
}
