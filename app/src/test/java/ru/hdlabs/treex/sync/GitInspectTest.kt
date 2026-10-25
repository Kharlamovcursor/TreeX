package ru.hdlabs.treex.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.RemoteRefresh

class GitInspectTest {
    @Test
    fun emptyPhonePullsWhenRemoteHasPeople() {
        val result = inspectGitRefresh(
            localPeopleCount = 0,
            remotePeopleCount = 12,
            remoteFilePresent = true,
            relation = GitRelation.Diverged,
        )
        assertTrue(result is RemoteRefresh.Incoming)
        assertTrue((result as RemoteRefresh.Incoming).count == 12)
    }

    @Test
    fun emptyPhoneFailsWhenRemoteFileMissing() {
        val result = inspectGitRefresh(
            localPeopleCount = 0,
            remotePeopleCount = null,
            remoteFilePresent = false,
            relation = GitRelation.RemoteMissing,
        )
        assertTrue(result is RemoteRefresh.Failed)
    }
}
