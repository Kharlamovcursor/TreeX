package ru.hdlabs.treex.sync

enum class GitRelation {
    Same,
    RemoteNewer,
    LocalNewer,
    Diverged,
    RemoteMissing,
}

fun relateGit(
    localHash: String,
    remoteHash: String?,
    remoteSha: String?,
    markerHash: String?,
    markerSha: String?,
): GitRelation {
    if (remoteHash.isNullOrBlank()) return GitRelation.RemoteMissing
    if (remoteHash == localHash) return GitRelation.Same
    val localMatches = !markerHash.isNullOrBlank() && markerHash == localHash
    val remoteMatches = (!markerSha.isNullOrBlank() && markerSha == remoteSha) ||
        (!markerHash.isNullOrBlank() && markerHash == remoteHash)
    return when {
        localMatches && !remoteMatches -> GitRelation.RemoteNewer
        !localMatches && remoteMatches -> GitRelation.LocalNewer
        else -> GitRelation.Diverged
    }
}
