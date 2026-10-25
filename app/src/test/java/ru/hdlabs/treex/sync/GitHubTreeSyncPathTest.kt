package ru.hdlabs.treex.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GitHubTreeSyncPathTest {
    @Test
    fun commitsPathKeepsSlashesBetweenSegments() {
        val value = GitHubTreeSync.commitsPathQueryValue("family/tree.json")
        assertEquals("family/tree.json", value)
        assertFalse(value.contains("%2F"))
    }
}
