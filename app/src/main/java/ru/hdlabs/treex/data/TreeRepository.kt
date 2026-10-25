package ru.hdlabs.treex.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.backup.BackupZip
import ru.hdlabs.treex.backup.GedcomCodec
import ru.hdlabs.treex.backup.PersonRecord
import ru.hdlabs.treex.backup.TreeCodec
import ru.hdlabs.treex.backup.TreeDocument
import ru.hdlabs.treex.backup.UnionRecord
import ru.hdlabs.treex.data.local.AppDatabase
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.data.settings.SettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.hdlabs.treex.domain.OpenLink
import ru.hdlabs.treex.domain.RelativeAttach
import ru.hdlabs.treex.domain.linksAfterRemoval
import ru.hdlabs.treex.domain.reattachPeople
import ru.hdlabs.treex.domain.reattachSpouse
import ru.hdlabs.treex.domain.transferVacantSlot
import ru.hdlabs.treex.domain.stableUnionId
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.TreeRules
import ru.hdlabs.treex.domain.mergePerson
import ru.hdlabs.treex.domain.mergeSpouseDrafts
import ru.hdlabs.treex.domain.spouseDraftsOf
import ru.hdlabs.treex.sync.GitHubTreeSync
import ru.hdlabs.treex.sync.GitRelation
import ru.hdlabs.treex.sync.relateGit
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TreeState(
    val people: List<PersonEntity> = emptyList(),
    val unions: List<UnionEntity> = emptyList(),
)

data class SnapshotInfo(
    val fileName: String,
    val label: String,
    val people: Int,
)

sealed interface RemoteRefresh {
    data class Updated(val count: Int) : RemoteRefresh
    data class Incoming(val count: Int) : RemoteRefresh
    data class LocalAhead(val count: Int) : RemoteRefresh
    data class Diverged(val count: Int) : RemoteRefresh
    data object Unchanged : RemoteRefresh
    data class Failed(val message: String) : RemoteRefresh
    data object NotConfigured : RemoteRefresh
}

class TreeRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val peopleDao = database.personDao()
    private val unionDao = database.unionDao()
    private val mutex = Mutex()
    private var generation = 0
    private var pushJob: Job? = null
    private val syncNoteState = MutableStateFlow<String?>(null)
    val syncNote: StateFlow<String?> = syncNoteState
    private val linkJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val openLinksState = MutableStateFlow(loadOpenLinks())
    val openLinks: StateFlow<List<OpenLink>> = openLinksState

    fun observe(): Flow<TreeState> = combine(peopleDao.observeAll(), unionDao.observeAll()) { people, unions ->
        TreeState(people, unions)
    }

    suspend fun person(id: String): PersonEntity? = peopleDao.get(id)

    suspend fun savePerson(
        person: PersonEntity,
        spouses: List<SpouseDraft>,
        snapshot: Boolean,
        attach: RelativeAttach? = null,
        preserveUntouched: Boolean = false,
        touched: Set<String> = emptySet(),
    ): Boolean {
        // Уход в настройки отменяет корутину экрана. Запись нельзя откатывать вместе с ней.
        val changed = withContext(NonCancellable) {
            mutex.withLock {
            database.withTransaction {
                val existing = peopleDao.all()
                val knownIds = existing.map { it.id }.toSet() + person.id
                val previous = peopleDao.get(person.id)
                val merged = if (previous != null && preserveUntouched) mergePerson(previous, person, touched) else person
                val fatherId = merged.fatherId?.takeIf { it in knownIds && it != merged.id }
                val motherId = merged.motherId?.takeIf { it in knownIds && it != merged.id && it != fatherId }
                val named = merged.firstName.isNotBlank() || merged.lastName.isNotBlank() || merged.middleName.isNotBlank()
                val clean = merged.copy(
                    fatherId = fatherId,
                    motherId = motherId,
                    vacant = merged.vacant && !named,
                )
                val parents = existing.associate { it.id to (it.fatherId to it.motherId) }.toMutableMap()
                parents[clean.id] = clean.fatherId to clean.motherId
                if (TreeRules.createsCycle(parents, clean.id, clean.fatherId, clean.motherId)) {
                    throw IllegalArgumentException("Такая связь замыкает круг в древе")
                }
                val anchor = attach?.anchorId?.takeIf { it in knownIds && it != clean.id }?.let { id ->
                    existing.find { it.id == id }
                }
                val linkRole = attach?.role?.takeIf { anchor != null }
                if (linkRole == "father" && anchor != null &&
                    TreeRules.createsCycle(parents, anchor.id, clean.id, anchor.motherId)
                ) {
                    throw IllegalArgumentException("Такая связь замыкает круг в древе")
                }
                if (linkRole == "mother" && anchor != null &&
                    TreeRules.createsCycle(parents, anchor.id, anchor.fatherId, clean.id)
                ) {
                    throw IllegalArgumentException("Такая связь замыкает круг в древе")
                }
                val previousSpouses = spouseDraftsOf(unionDao.all(), clean.id).sortedBy { it.personId }
                val formSpouses = (spouses + listOfNotNull(
                    if (linkRole == "spouse" && anchor != null) SpouseDraft(anchor.id, spouses.find { it.personId == anchor.id }?.marriedOn.orEmpty()) else null,
                )).filter { it.personId.isNotBlank() && it.personId != clean.id && it.personId in knownIds }
                    .distinctBy { it.personId }
                val nextSpouses = mergeSpouseDrafts(
                    previous = previousSpouses,
                    incoming = formSpouses,
                    replace = previous == null || !preserveUntouched || "spouses" in touched,
                ).sortedBy { it.personId }
                val anchorAlreadyLinked = when (linkRole) {
                    "father" -> anchor?.fatherId == clean.id
                    "mother" -> anchor?.motherId == clean.id
                    else -> true
                }
                if (previous != null && sameContent(previous, clean) && previousSpouses == nextSpouses && anchorAlreadyLinked) {
                    false
                } else {
                    val now = System.currentTimeMillis()
                    peopleDao.upsert(
                        clean.copy(
                            gender = if (clean.gender == "m" || clean.gender == "f") clean.gender else "u",
                            createdAtMillis = previous?.createdAtMillis?.takeIf { it > 0 }
                                ?: clean.createdAtMillis.takeIf { it > 0 }
                                ?: now,
                            updatedAtMillis = now,
                        ),
                    )
                    if (linkRole == "father" && anchor != null) {
                        peopleDao.upsert(anchor.copy(fatherId = clean.id, updatedAtMillis = now))
                    }
                    if (linkRole == "mother" && anchor != null) {
                        peopleDao.upsert(anchor.copy(motherId = clean.id, updatedAtMillis = now))
                    }
                    val kept = unionDao.all().filter { it.partnerAId != clean.id && it.partnerBId != clean.id }
                    val created = nextSpouses.map { spouse ->
                        val a = minOf(clean.id, spouse.personId)
                        val b = maxOf(clean.id, spouse.personId)
                        UnionEntity("u-$a-$b", a, b, spouse.marriedOn.trim())
                    }
                    unionDao.deleteAll()
                    unionDao.upsertAll(kept + created)
                    generation += 1
                    if (snapshot) snapshotLocked(readLocked())
                    true
                }
            }
            }
        }
        if (changed) schedulePush()
        return changed
    }

    suspend fun deletePerson(id: String) {
        mutex.withLock {
            database.withTransaction {
                snapshotLocked(readLocked())
                val people = peopleDao.all()
                val removed = people.find { it.id == id } ?: return@withTransaction
                if (removed.vacant) {
                    purgePersonLocked(removed, people)
                } else {
                    vacatePersonLocked(removed)
                }
            }
        }
        schedulePush()
    }

    private suspend fun vacatePersonLocked(removed: PersonEntity) {
        if (removed.photoFile.isNotBlank()) {
            PhotoStore.delete(context, removed.photoFile)
        }
        val now = System.currentTimeMillis()
        peopleDao.upsert(
            removed.copy(
                vacant = true,
                lastName = "",
                birthLastName = "",
                firstName = "",
                middleName = "",
                birthDate = "",
                deathDate = "",
                birthPlace = "",
                email = "",
                phone = "",
                photoFile = "",
                notes = "",
                updatedAtMillis = now,
            ),
        )
        val vacancyLinks = unionDao.all().mapNotNull { union ->
            val partners = setOf(union.partnerAId, union.partnerBId)
            if (removed.id !in partners) return@mapNotNull null
            val other = partners.first { it != removed.id }
            OpenLink(
                id = java.util.UUID.randomUUID().toString(),
                slot = "vacancy",
                ownerId = removed.id,
                anchorId = other,
            )
        }
        if (vacancyLinks.isNotEmpty()) {
            val kept = openLinksState.value.filter { link ->
                link.ownerId != removed.id && link.anchorId != removed.id
            }
            writeOpenLinks(kept + vacancyLinks)
        }
        generation += 1
    }

    private suspend fun purgePersonLocked(removed: PersonEntity, people: List<PersonEntity>) {
        val fresh = linksAfterRemoval(removed, people, unionDao.all())
        people.filter { it.fatherId == removed.id || it.motherId == removed.id }.forEach { child ->
            peopleDao.upsert(
                child.copy(
                    fatherId = child.fatherId?.takeIf { it != removed.id },
                    motherId = child.motherId?.takeIf { it != removed.id },
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        val photo = removed.photoFile
        unionDao.deleteInvolving(removed.id)
        peopleDao.delete(removed.id)
        if (photo.isNotBlank()) PhotoStore.delete(context, photo)
        generation += 1
        val kept = openLinksState.value.filter { link ->
            link.ownerId != removed.id && link.anchorId != removed.id && removed.id !in link.childIds
        }
        writeOpenLinks(kept + fresh)
    }

    suspend fun attachOpenLink(linkId: String, targetId: String) {
        mutex.withLock {
            database.withTransaction {
                val link = openLinksState.value.find { it.id == linkId } ?: return@withTransaction
                val people = peopleDao.all()
                val target = people.find { it.id == targetId } ?: return@withTransaction
                val vacant = target.vacant
                val vacantFill = if (link.slot == "vacancy" && vacant && targetId == link.ownerId) {
                    true
                } else {
                    false
                }
                val transfer = if (link.slot == "vacancy" && vacant && targetId != link.ownerId) {
                    transferVacantSlot(link.ownerId, targetId, people, unionDao.all())
                } else {
                    null
                }
                val updates = when {
                    transfer != null -> transfer.first
                    vacantFill -> emptyList()
                    else -> reattachPeople(link, target, people)
                }
                val spouse = if (transfer != null) {
                    reattachSpouse(link, targetId)
                } else if (!vacantFill) {
                    reattachSpouse(link, targetId)
                } else {
                    null
                }
                val alreadyLinked = when (link.slot) {
                    "father" -> people.find { it.id == link.ownerId }?.fatherId == targetId
                    "mother" -> people.find { it.id == link.ownerId }?.motherId == targetId
                    "spouse" -> unionDao.all().any {
                        setOf(it.partnerAId, it.partnerBId) == setOf(link.ownerId, targetId)
                    }
                    "vacancy" -> vacantFill || transfer != null
                    else -> false
                }
                if (updates.isEmpty() && spouse == null && transfer == null && !alreadyLinked && !vacantFill) {
                    return@withTransaction
                }
                snapshotLocked(readLocked())
                updates.forEach { peopleDao.upsert(it) }
                if (transfer != null) {
                    val vacantId = link.ownerId
                    val keptUnions = unionDao.all().filter { vacantId !in setOf(it.partnerAId, it.partnerBId) }
                    unionDao.deleteAll()
                    unionDao.upsertAll(keptUnions + transfer.second)
                    peopleDao.delete(vacantId)
                } else if (spouse != null) {
                    val (first, second) = spouse
                    val known = unionDao.all().any { setOf(it.partnerAId, it.partnerBId) == setOf(first, second) }
                    if (!known && first != second) {
                        unionDao.upsertAll(listOf(UnionEntity(stableUnionId(first, second), first, second)))
                    }
                }
                generation += 1
                writeOpenLinks(openLinksState.value.filter { it.id != linkId })
            }
        }
        schedulePush()
    }

    suspend fun snapshotNow() {
        mutex.withLock { snapshotLocked(readLocked()) }
    }

    suspend fun snapshots(): List<SnapshotInfo> = withContext(Dispatchers.IO) {
        historyDir().listFiles { file -> file.extension == "json" }.orEmpty()
            .sortedByDescending { it.name }
            .map { file ->
                val count = runCatching { TreeCodec.decode(file.readText()).people.size }.getOrDefault(0)
                SnapshotInfo(file.name, file.name.removeSuffix(".json"), count)
            }
    }

    suspend fun restoreSnapshot(fileName: String) {
        require(fileName.matches(Regex("[0-9-]+\\.json"))) { "Некорректный снимок" }
        mutex.withLock {
            val file = File(historyDir(), fileName)
            require(file.isFile && file.parentFile == historyDir()) { "Снимок не найден" }
            val document = TreeCodec.decode(file.readText())
            snapshotLocked(readLocked())
            replaceLocked(document)
            generation += 1
        }
        schedulePush()
    }

    suspend fun exportDocument(): TreeDocument = mutex.withLock {
        readLocked().copy(exportedAtMillis = System.currentTimeMillis())
    }

    suspend fun exportGedcom(): String = GedcomCodec.export(exportDocument())

    fun parseImport(bytes: ByteArray): TreeDocument {
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return BackupZip.readDocument(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8).trim().removePrefix("\uFEFF")
        return when {
            text.startsWith("{") -> TreeCodec.decode(text)
            text.startsWith("0 HEAD") || text.contains("\n0 ") || text.contains("\r\n0 ") -> GedcomCodec.import(text)
            else -> throw IllegalArgumentException("Нужен GEDCOM (.ged), JSON HDLabs или ZIP с фото")
        }
    }

    suspend fun importBytes(bytes: ByteArray) {
        val document = parseImport(bytes)
        val zip = bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        mutex.withLock {
            snapshotLocked(readLocked())
            if (zip) BackupZip.copyPhotos(context, bytes)
            replaceLocked(document)
            generation += 1
        }
        schedulePush()
    }

    suspend fun inspectGit(): RemoteRefresh {
        val look = lookAtGit() ?: return RemoteRefresh.NotConfigured
        return look.fold(
            onSuccess = { snapshot ->
                when (
                    relateGit(
                        snapshot.localHash,
                        snapshot.remoteHash,
                        snapshot.remote?.sha,
                        snapshot.marker?.hash,
                        snapshot.marker?.sha,
                    )
                ) {
                    GitRelation.Same -> {
                        snapshot.remote?.let { writeMarker(it.sha, snapshot.localHash) }
                        RemoteRefresh.Unchanged
                    }
                    GitRelation.RemoteNewer -> RemoteRefresh.Incoming(snapshot.remoteDoc?.people?.size ?: 0)
                    GitRelation.LocalNewer, GitRelation.RemoteMissing ->
                        if (snapshot.local.people.isEmpty() && snapshot.remote == null) {
                            RemoteRefresh.Unchanged
                        } else {
                            RemoteRefresh.LocalAhead(snapshot.local.people.size)
                        }
                    GitRelation.Diverged -> RemoteRefresh.Diverged(snapshot.remoteDoc?.people?.size ?: 0)
                }
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                RemoteRefresh.Failed(error.message ?: "Не удалось получить данные из Git")
            },
        )
    }

    suspend fun refreshFromGit(): RemoteRefresh {
        val look = lookAtGit() ?: return RemoteRefresh.NotConfigured
        return look.fold(
            onSuccess = { snapshot ->
                val remote = snapshot.remote
                    ?: return RemoteRefresh.Failed("В репозитории ещё нет файла древа. Отправьте древо кнопкой в настройках.")
                val remoteDoc = snapshot.remoteDoc
                    ?: return RemoteRefresh.Failed("Файл в Git не удалось прочитать")
                if (snapshot.remoteHash == snapshot.localHash) {
                    writeMarker(remote.sha, snapshot.localHash)
                    RemoteRefresh.Unchanged
                } else {
                    mutex.withLock {
                        snapshotLocked(readLocked())
                        replaceLocked(remoteDoc)
                        generation += 1
                    }
                    writeMarker(remote.sha, TreeCodec.contentHash(remoteDoc))
                    RemoteRefresh.Updated(remoteDoc.people.size)
                }
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                RemoteRefresh.Failed(error.message ?: "Не удалось получить данные из Git")
            },
        )
    }

    suspend fun pushNow(): String {
        pushJob?.cancel()
        return if (pushToGit(force = true)) "Древо отправлено в Git" else "Git уже совпадает с телефоном"
    }

    suspend fun remoteCommits(): List<GitHubTreeSync.RemoteCommit> {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) throw IllegalStateException("Git не настроен")
        return withContext(Dispatchers.IO) { syncFor(settings).listCommits() }
    }

    suspend fun restoreRemote(sha: String) {
        require(sha.matches(Regex("[0-9a-fA-F]{7,64}"))) { "Некорректная версия" }
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) throw IllegalStateException("Git не настроен")
        val remote = withContext(Dispatchers.IO) { syncFor(settings).fetchExact(sha) }
        val document = TreeCodec.decode(remote.text)
        mutex.withLock {
            snapshotLocked(readLocked())
            replaceLocked(document)
            generation += 1
        }
        schedulePush()
    }

    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(10_000)
            val settings = settingsRepository.settings.first()
            if (!settings.gitConfigured) return@launch
            runCatching { pushToGit(force = false) }
                .onSuccess { syncNoteState.value = null }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    syncNoteState.value = error.message
                }
        }
    }

    private suspend fun lookAtGit(): Result<GitSnapshot>? {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) return null
        return runCatching {
            val remote = withContext(Dispatchers.IO) { syncFor(settings).fetch() }
            val remoteDoc = remote?.let { TreeCodec.decode(it.text) }
            val local = mutex.withLock { readLocked() }
            GitSnapshot(
                remote = remote,
                remoteDoc = remoteDoc,
                local = local,
                localHash = TreeCodec.contentHash(local),
                remoteHash = remoteDoc?.let { TreeCodec.contentHash(it) },
                marker = readMarker(),
            )
        }
    }

    private suspend fun pushToGit(force: Boolean): Boolean {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) throw IllegalStateException("Git не настроен")
        val captured = mutex.withLock { generation to TreeCodec.encode(readLocked()) }
        val sync = syncFor(settings)
        val remote = withContext(Dispatchers.IO) { sync.fetch() }
        val localHash = TreeCodec.contentHash(TreeCodec.decode(captured.second))
        val marker = readMarker()
        if (remote != null) {
            val remoteHash = TreeCodec.contentHash(TreeCodec.decode(remote.text))
            if (remoteHash == localHash) {
                writeMarker(remote.sha, localHash)
                return false
            }
            val relation = relateGit(localHash, remoteHash, remote.sha, marker?.hash, marker?.sha)
            if (!force && (relation == GitRelation.RemoteNewer || relation == GitRelation.Diverged)) {
                throw IllegalStateException("В Git другая версия. На главном экране нажмите обновление и выберите: загрузить Git или отправить телефон.")
            }
        }
        val stillCurrent = mutex.withLock { generation == captured.first }
        if (!stillCurrent) {
            schedulePush()
            return false
        }
        val count = TreeCodec.decode(captured.second).people.size
        val sha = withContext(Dispatchers.IO) {
            sync.push(captured.second, remote?.sha, "Древо: $count чел.")
        }
        writeMarker(sha, localHash)
        val changedDuringUpload = mutex.withLock { generation != captured.first }
        if (changedDuringUpload) schedulePush()
        return true
    }

    private fun syncFor(settings: ru.hdlabs.treex.data.settings.AppSettings) = GitHubTreeSync(
        owner = settings.githubOwner.trim(),
        repo = settings.githubRepo.trim(),
        branch = settings.githubBranch.ifBlank { "main" },
        path = settings.githubPath.ifBlank { "family/tree.json" },
        token = settings.githubToken.trim(),
    )

    private suspend fun readLocked(): TreeDocument = TreeDocument(
        people = peopleDao.all().map { it.toRecord() },
        unions = unionDao.all().map { UnionRecord(it.id, it.partnerAId, it.partnerBId, it.marriedOn) },
    )

    private suspend fun replaceLocked(document: TreeDocument) {
        val normalized = TreeCodec.normalize(document)
        peopleDao.deleteAll()
        unionDao.deleteAll()
        peopleDao.upsertAll(normalized.people.map { it.toEntity() })
        unionDao.upsertAll(normalized.unions.map { UnionEntity(it.id, it.partnerAId, it.partnerBId, it.marriedOn) })
        pruneOpenLinks(normalized.people.map { it.id }.toSet())
    }

    private suspend fun snapshotLocked(document: TreeDocument) {
        if (document.people.isEmpty() && document.unions.isEmpty()) return
        val text = TreeCodec.encode(document)
        val dir = historyDir()
        val latest = dir.listFiles { file -> file.extension == "json" }?.maxByOrNull { it.name }
        if (latest != null && latest.readText() == text) return
        val name = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now()) + ".json"
        File(dir, name).writeText(text)
        val files = dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
        if (files.size > 30) files.take(files.size - 30).forEach { it.delete() }
    }

    private fun historyDir(): File = File(context.filesDir, "history").apply { mkdirs() }

    private fun markerFile(): File = File(context.filesDir, "git-sync.txt")

    private fun openLinksFile(): File = File(context.filesDir, "open-links.json")

    private fun loadOpenLinks(): List<OpenLink> = runCatching {
        val file = openLinksFile()
        if (!file.isFile) emptyList() else linkJson.decodeFromString<List<OpenLink>>(file.readText())
    }.getOrDefault(emptyList())

    private fun writeOpenLinks(links: List<OpenLink>) {
        openLinksState.value = links
        runCatching {
            if (links.isEmpty()) openLinksFile().delete()
            else openLinksFile().writeText(linkJson.encodeToString(links))
        }
    }

    private fun pruneOpenLinks(valid: Set<String>) {
        val next = openLinksState.value.filter { link ->
            link.ownerId in valid && link.anchorId in valid && link.childIds.all { it in valid }
        }
        if (next.size != openLinksState.value.size) writeOpenLinks(next)
    }

    private fun readMarker(): GitMarker? {
        val lines = markerFile().takeIf { it.isFile }?.readLines().orEmpty()
        if (lines.size < 2) return null
        return GitMarker(lines[0].trim(), lines[1].trim())
    }

    private fun writeMarker(sha: String, hash: String) {
        markerFile().writeText(sha.trim() + "\n" + hash.trim())
    }

    private fun sameContent(first: PersonEntity, second: PersonEntity): Boolean =
        first.copy(createdAtMillis = 0, updatedAtMillis = 0) == second.copy(createdAtMillis = 0, updatedAtMillis = 0)

    private data class GitMarker(val sha: String, val hash: String)

    private data class GitSnapshot(
        val remote: GitHubTreeSync.RemoteFile?,
        val remoteDoc: TreeDocument?,
        val local: TreeDocument,
        val localHash: String,
        val remoteHash: String?,
        val marker: GitMarker?,
    )
}

private fun PersonEntity.toRecord() = PersonRecord(
    id = id,
    lastName = lastName,
    birthLastName = birthLastName,
    firstName = firstName,
    middleName = middleName,
    gender = gender,
    birthDate = birthDate,
    deathDate = deathDate,
    birthPlace = birthPlace,
    email = email,
    phone = phone,
    photoFile = photoFile,
    notes = notes,
    fatherId = fatherId,
    motherId = motherId,
    vacant = vacant,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

private fun PersonRecord.toEntity() = PersonEntity(
    id = id,
    lastName = lastName,
    birthLastName = birthLastName,
    firstName = firstName,
    middleName = middleName,
    gender = gender,
    birthDate = birthDate,
    deathDate = deathDate,
    birthPlace = birthPlace,
    email = email,
    phone = phone,
    photoFile = photoFile,
    notes = notes,
    fatherId = fatherId,
    motherId = motherId,
    vacant = vacant,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
