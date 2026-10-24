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
import ru.hdlabs.treex.domain.RelativeAttach
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.TreeRules
import ru.hdlabs.treex.domain.spouseDraftsOf
import ru.hdlabs.treex.sync.GitHubTreeSync
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

    fun observe(): Flow<TreeState> = combine(peopleDao.observeAll(), unionDao.observeAll()) { people, unions ->
        TreeState(people, unions)
    }

    suspend fun person(id: String): PersonEntity? = peopleDao.get(id)

    suspend fun savePerson(
        person: PersonEntity,
        spouses: List<SpouseDraft>,
        snapshot: Boolean,
        attach: RelativeAttach? = null,
    ): Boolean {
        val changed = mutex.withLock {
            database.withTransaction {
                val existing = peopleDao.all()
                val knownIds = existing.map { it.id }.toSet() + person.id
                val fatherId = person.fatherId?.takeIf { it in knownIds && it != person.id }
                val motherId = person.motherId?.takeIf { it in knownIds && it != person.id && it != fatherId }
                val clean = person.copy(fatherId = fatherId, motherId = motherId)
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
                val previous = peopleDao.get(clean.id)
                val previousSpouses = spouseDraftsOf(unionDao.all(), clean.id).sortedBy { it.personId }
                val nextSpouses = (spouses + listOfNotNull(
                    if (linkRole == "spouse" && anchor != null) SpouseDraft(anchor.id, spouses.find { it.personId == anchor.id }?.marriedOn.orEmpty()) else null,
                )).filter { it.personId.isNotBlank() && it.personId != clean.id && it.personId in knownIds }
                    .distinctBy { it.personId }
                    .sortedBy { it.personId }
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
        if (changed) schedulePush()
        return changed
    }

    suspend fun deletePerson(id: String) {
        mutex.withLock {
            database.withTransaction {
                snapshotLocked(readLocked())
                peopleDao.all().filter { it.fatherId == id || it.motherId == id }.forEach { child ->
                    peopleDao.upsert(
                        child.copy(
                            fatherId = child.fatherId?.takeIf { it != id },
                            motherId = child.motherId?.takeIf { it != id },
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                val photo = peopleDao.get(id)?.photoFile.orEmpty()
                unionDao.deleteInvolving(id)
                peopleDao.delete(id)
                PhotoStore.delete(context, photo)
                generation += 1
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

    suspend fun refreshFromGit(): RemoteRefresh {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) return RemoteRefresh.NotConfigured
        return try {
            val sync = syncFor(settings)
            val remote = withContext(Dispatchers.IO) { sync.fetch() }
                ?: return RemoteRefresh.Failed("В репозитории ещё нет файла древа. Если он должен там быть, свяжитесь с разработчиком.")
            val local = mutex.withLock { readLocked() }
            val remoteDoc = TreeCodec.decode(remote.text)
            if (TreeCodec.contentHash(remoteDoc) == TreeCodec.contentHash(local)) {
                writeMarker(remote.sha, TreeCodec.contentHash(remoteDoc))
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteRefresh.Failed(e.message ?: "Не удалось получить данные из Git")
        }
    }

    suspend fun pushNow(): String {
        pushJob?.cancel()
        return if (pushToGit()) "Древо отправлено в Git" else "Git уже совпадает с телефоном"
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
            runCatching { pushToGit() }
                .onSuccess { syncNoteState.value = null }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    syncNoteState.value = error.message
                }
        }
    }

    private suspend fun pushToGit(): Boolean {
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
            val remoteMoved = marker == null || (marker.sha != remote.sha && marker.hash != remoteHash)
            if (remoteMoved) {
                throw IllegalStateException("В Git есть версия, которой нет на телефоне. Сначала нажмите «Обновить информацию».")
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
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
