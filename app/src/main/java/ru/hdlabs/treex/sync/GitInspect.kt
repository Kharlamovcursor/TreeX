package ru.hdlabs.treex.sync

import ru.hdlabs.treex.data.RemoteRefresh

fun inspectGitRefresh(
    localPeopleCount: Int,
    remotePeopleCount: Int?,
    remoteFilePresent: Boolean,
    relation: GitRelation,
): RemoteRefresh {
    val localEmpty = localPeopleCount == 0
    val remoteCount = remotePeopleCount ?: 0
    if (localEmpty) {
        if (remoteCount > 0) return RemoteRefresh.Incoming(remoteCount)
        if (!remoteFilePresent) {
            return RemoteRefresh.Failed(
                "На телефоне древо пустое, а файл в Git не найден. " +
                    "На основном телефоне откройте настройки и отправьте древо в Git.",
            )
        }
        return RemoteRefresh.Unchanged
    }
    return when (relation) {
        GitRelation.Same -> RemoteRefresh.Unchanged
        GitRelation.RemoteNewer -> RemoteRefresh.Incoming(remoteCount)
        GitRelation.LocalNewer, GitRelation.RemoteMissing -> RemoteRefresh.LocalAhead(localPeopleCount)
        GitRelation.Diverged -> RemoteRefresh.Diverged(remoteCount)
    }
}
