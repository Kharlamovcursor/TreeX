package ru.hdlabs.treex.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class GitRelationTest {
    @Test
    fun sameContentDoesNotLookLikeAConflict() {
        assertEquals(GitRelation.Same, relateGit("a", "a", "sha-2", "old", "sha-1"))
    }

    @Test
    fun remoteChangeAfterLastSyncIsNewer() {
        assertEquals(
            GitRelation.RemoteNewer,
            relateGit("local", "remote", "sha-2", "local", "sha-1"),
        )
    }

    @Test
    fun phoneEditsAfterLastSyncStayLocal() {
        assertEquals(
            GitRelation.LocalNewer,
            relateGit("local", "remote", "sha-1", "remote", "sha-1"),
        )
    }

    @Test
    fun bothSidesChangedWithoutASharedMarkerDiverge() {
        assertEquals(GitRelation.Diverged, relateGit("local", "remote", "sha-2", null, null))
    }

    @Test
    fun missingFileIsNotARemoteUpdate() {
        assertEquals(GitRelation.RemoteMissing, relateGit("local", null, null, null, null))
    }
}
