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
                "В Git пока нет файла древа. " +
                    "На телефоне, где древо уже заполнено: «Настройки» → разработчик → «Отправить в Git сейчас».",
            )
        }
        return RemoteRefresh.Unchanged
    }
    return when (relation) {
        GitRelation.Same -> RemoteRefresh.Unchanged
        GitRelation.RemoteNewer -> RemoteRefresh.Incoming(remoteCount)
        GitRelation.RemoteMissing -> {
            if (localPeopleCount == 0) {
                RemoteRefresh.Failed(
                    "Не удалось прочитать древо из Git. Проверьте интернет и доступ к репозиторию.",
                )
            } else {
                RemoteRefresh.Failed(
                    "Git недоступен. Отправка отменена, чтобы не затереть семейное древо на сервере.",
                )
            }
        }
        GitRelation.LocalNewer -> {
            if (remoteFilePresent && remoteCount > localPeopleCount) {
                RemoteRefresh.Incoming(remoteCount)
            } else {
                RemoteRefresh.LocalAhead(localPeopleCount)
            }
        }
        GitRelation.Diverged -> RemoteRefresh.Diverged(remoteCount)
    }
}
