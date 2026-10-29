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
import ru.hdlabs.treex.domain.mergeInferredParentUnions
import ru.hdlabs.treex.domain.stableUnionId
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.TreeRules
import ru.hdlabs.treex.domain.siblingsMissingParent
import ru.hdlabs.treex.domain.TreeIntegrity
import ru.hdlabs.treex.domain.UnionEr
import ru.hdlabs.treex.domain.UnionErAttach
import ru.hdlabs.treex.domain.childIdsList
import ru.hdlabs.treex.domain.partnerIds
import ru.hdlabs.treex.domain.mergePerson
import ru.hdlabs.treex.domain.mergeSpouseDrafts
import ru.hdlabs.treex.domain.spouseDraftsOf
import ru.hdlabs.treex.domain.acceptsAsParent
import ru.hdlabs.treex.domain.BranchConnectMode
import ru.hdlabs.treex.domain.capitalizePersonName
import ru.hdlabs.treex.domain.treeGitCommitMessage
import ru.hdlabs.treex.domain.userFio
import ru.hdlabs.treex.domain.planBranchConnect
import ru.hdlabs.treex.sync.GitHubTreeSync
import ru.hdlabs.treex.BuildConfig
import ru.hdlabs.treex.diagnostic.AppEventLog
import ru.hdlabs.treex.diagnostic.TreeSnapshotRenderer
import ru.hdlabs.treex.data.PhotoStore
import ru.hdlabs.treex.util.NetworkHelper
import ru.hdlabs.treex.sync.GitRelation
import ru.hdlabs.treex.sync.inspectGitRefresh
import ru.hdlabs.treex.sync.relateGit
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class TreeState(
    val people: List<PersonEntity> = emptyList(),
    val unions: List<UnionEntity> = emptyList(),
)

data class SnapshotInfo(
    val fileName: String,
    val label: String,
    val people: Int,
    val author: String? = null,
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
    companion object {
        const val DEBUG_TREE_SNAPSHOT_PATH = "family/debug/tree-layout.png"
    }
    private val peopleDao = database.personDao()
    private val unionDao = database.unionDao()
    private val mutex = Mutex()
    private val gitMutex = Mutex()
    private var generation = 0
    private var pushJob: Job? = null
    private val syncNoteState = MutableStateFlow<String?>(null)
    val syncNote: StateFlow<String?> = syncNoteState
    private val pendingUploadState = MutableStateFlow(false)
    val pendingUpload: StateFlow<Boolean> = pendingUploadState
    private val linkJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val openLinksState = MutableStateFlow(loadOpenLinks())
    val openLinks: StateFlow<List<OpenLink>> = openLinksState

    fun observe(): Flow<TreeState> = combine(peopleDao.observeAll(), unionDao.observeAll()) { people, unions ->
        TreeState(people, unions)
    }

    suspend fun person(id: String): PersonEntity? = peopleDao.get(id)

    /** Добавляет союзы между отцом и матерью, если у ребёнка указаны оба родителя, а брака в базе нет. */
    suspend fun reconcileParentUnions() {
        mutex.withLock {
            database.withTransaction {
                val people = peopleDao.all()
                val current = unionDao.all()
                val merged = UnionEr.compactUnions(people, mergeInferredParentUnions(current, people))
                if (merged != current) {
                    unionDao.deleteAll()
                    unionDao.upsertAll(merged)
                    generation += 1
                    schedulePush()
                }
            }
        }
    }

    /** Clears broken parent links and unions tied to vacant slots (after messy deletes). */
    suspend fun repairLivingKinship() {
        var changed = false
        mutex.withLock {
            database.withTransaction {
                var people = peopleDao.all()
                while (true) {
                    val vacant = people.find { it.vacant } ?: break
                    purgePersonLocked(vacant, people)
                    changed = true
                    people = peopleDao.all()
                }
                val fixes = TreeIntegrity.repairParentPointers(people)
                fixes.forEach { peopleDao.upsert(it) }
                if (fixes.isNotEmpty()) changed = true
                val unions = unionDao.all()
                val compact = UnionEr.compactUnions(people, unions)
                val kept = TreeIntegrity.livingUnions(compact, people)
                if (kept.sortedBy { it.id } != unions.sortedBy { it.id }) {
                    unionDao.deleteAll()
                    unionDao.upsertAll(kept)
                    changed = true
                }
            }
        }
        if (changed) {
            generation += 1
            schedulePush()
        }
    }

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
                val baseClean = merged.copy(
                    lastName = capitalizePersonName(merged.lastName),
                    birthLastName = if (merged.gender == "f") {
                        capitalizePersonName(merged.birthLastName)
                    } else {
                        ""
                    },
                    firstName = capitalizePersonName(merged.firstName),
                    middleName = capitalizePersonName(merged.middleName),
                    fatherId = fatherId,
                    motherId = motherId,
                    vacant = merged.vacant && !named,
                )
                val anchor = attach?.anchorId?.takeIf { it in knownIds && it != baseClean.id }?.let { id ->
                    existing.find { it.id == id }
                }
                val linkRole = attach?.role?.takeIf { anchor != null }
                val clean = when (linkRole) {
                    "sister", "brother" -> baseClean.copy(
                        gender = if (linkRole == "sister") "f" else "m",
                        fatherId = null,
                        motherId = null,
                    )
                    else -> baseClean.copy(fatherId = null, motherId = null)
                }
                val unionSnapshot = UnionEr.reconcileChildMembership(existing, unionDao.all())
                val projectedUnions = if (linkRole != null && anchor != null) {
                    UnionErAttach.applyRelativeAttach(existing, unionSnapshot, anchor.id, linkRole, clean)
                } else {
                    unionSnapshot
                }
                val parentMap = UnionEr.parentPairsMap(existing + clean, projectedUnions)
                val pair = parentMap[clean.id] ?: (null to null)
                if (TreeRules.createsCycle(parentMap, clean.id, pair.first, pair.second)) {
                    throw IllegalArgumentException("Такая связь замыкает круг в древе")
                }
                if (linkRole == "father" && anchor != null) {
                    val anchorPair = parentMap[anchor.id] ?: (null to null)
                    if (TreeRules.createsCycle(parentMap, anchor.id, clean.id, anchorPair.second)) {
                        throw IllegalArgumentException("Такая связь замыкает круг в древе")
                    }
                }
                if (linkRole == "mother" && anchor != null) {
                    val anchorPair = parentMap[anchor.id] ?: (null to null)
                    if (TreeRules.createsCycle(parentMap, anchor.id, anchorPair.first, clean.id)) {
                        throw IllegalArgumentException("Такая связь замыкает круг в древе")
                    }
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
                    "father", "mother" -> {
                        val childUnion = anchor?.let { UnionEr.findParentUnionForChild(projectedUnions, it.id) }
                        childUnion?.partnerIds()?.contains(clean.id) == true
                    }
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
                    val kept = unionDao.all().filter {
                        it.partnerAId != clean.id && it.partnerBId != clean.id
                    }
                    val created = nextSpouses.map { spouse ->
                        val a = minOf(clean.id, spouse.personId)
                        val b = maxOf(clean.id, spouse.personId)
                        UnionEntity(id = stableUnionId(a, b), partnerAId = a, partnerBId = b, marriedOn = spouse.marriedOn.trim())
                    }
                    val afterAttach = if (linkRole != null && anchor != null) {
                        UnionErAttach.applyRelativeAttach(peopleDao.all(), kept + created, anchor.id, linkRole, clean)
                    } else {
                        kept + created
                    }
                    val unionsWithParents = mergeInferredParentUnions(afterAttach, peopleDao.all())
                    unionDao.deleteAll()
                    unionDao.upsertAll(unionsWithParents)
                    generation += 1
                    if (snapshot) snapshotLocked(readLocked())
                    true
                }
            }
            }
        }
        if (changed) {
            val settings = settingsRepository.settings.first()
            if (settings.homePersonId.isBlank() && !person.vacant) {
                val livingCount = mutex.withLock { peopleDao.all().count { !it.vacant } }
                if (livingCount == 1) {
                    settingsRepository.update { it.copy(homePersonId = person.id) }
                }
            }
            schedulePush()
        }
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
        val people = peopleDao.all()
        people.filter { it.fatherId == removed.id || it.motherId == removed.id }.forEach { child ->
            peopleDao.upsert(
                child.copy(
                    fatherId = child.fatherId?.takeIf { it != removed.id },
                    motherId = child.motherId?.takeIf { it != removed.id },
                    updatedAtMillis = now,
                ),
            )
        }
        peopleDao.upsert(
            removed.copy(
                vacant = true,
                fatherId = null,
                motherId = null,
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
            val partners = union.partnerIds().toSet()
            if (removed.id !in partners) return@mapNotNull null
            val other = partners.first { it != removed.id }
            OpenLink(
                id = java.util.UUID.randomUUID().toString(),
                slot = "vacancy",
                ownerId = removed.id,
                anchorId = other,
            )
        }
        val bridgeLinks = linksAfterRemoval(removed, people, unionDao.all())
        val kept = openLinksState.value.filter { link ->
            link.ownerId != removed.id && link.anchorId != removed.id && removed.id !in link.childIds
        }
        writeOpenLinks(kept + vacancyLinks + bridgeLinks)
        val keptUnions = TreeIntegrity.livingUnions(unionDao.all(), peopleDao.all())
        unionDao.deleteAll()
        unionDao.upsertAll(keptUnions)
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
                if (link.slot in setOf("father", "mother") && !acceptsAsParent(link.slot, target)) {
                    throw IllegalStateException(
                        if (link.slot == "father") {
                            "Для отца можно выбрать только мужскую карточку."
                        } else {
                            "Для матери можно выбрать только женскую карточку."
                        },
                    )
                }
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

    suspend fun connectBranchToTree(subjectId: String, targetId: String, mode: BranchConnectMode) {
        mutex.withLock {
            database.withTransaction {
                val people = peopleDao.all()
                val unions = unionDao.all()
                val subject = people.find { it.id == subjectId }
                    ?: throw IllegalArgumentException("Карточка не найдена")
                val target = people.find { it.id == targetId }
                    ?: throw IllegalArgumentException("Карточка не найдена")
                val plan = planBranchConnect(subject, target, mode, people, unions)
                if (plan.peopleUpdates.isEmpty() && plan.unionPartners == null) return@withTransaction
                snapshotLocked(readLocked())
                plan.peopleUpdates.forEach { peopleDao.upsert(it) }
                plan.unionPartners?.let { (first, second) ->
                    val a = minOf(first, second)
                    val b = maxOf(first, second)
                    val known = unions.any { setOf(it.partnerAId, it.partnerBId) == setOf(a, b) }
                    if (!known) {
                        unionDao.upsertAll(listOf(UnionEntity(stableUnionId(a, b), a, b)))
                    }
                }
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
                val author = snapshotAuthorFile(file).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
                SnapshotInfo(file.name, SnapshotNames.label(file.name), count, author)
            }
    }

    suspend fun restoreSnapshot(fileName: String) {
        require(SnapshotNames.isSafe(fileName)) { "Некорректный снимок" }
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

    suspend fun syncTreeFromGitIfNeeded(): RemoteRefresh = gitMutex.withLock {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) return@withLock RemoteRefresh.NotConfigured
        if (settings.pauseGitAutoSync) return@withLock RemoteRefresh.Unchanged
        val empty = mutex.withLock { readLocked().people.isEmpty() }
        pullFromGitOnLaunch(forceEmptyLocal = empty) ?: RemoteRefresh.Unchanged
    }

    private suspend fun pullFromGitOnLaunch(forceEmptyLocal: Boolean = false): RemoteRefresh? {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) return null
        repeat(5) { attempt ->
            if (!NetworkHelper.isOnline(context)) {
                delay(1_500L * (attempt + 1))
                return@repeat
            }
            when (val status = inspectGitUnlocked()) {
                is RemoteRefresh.Incoming -> {
                    AppEventLog.info("git", "При запуске найдена новая версия в Git")
                    return refreshFromGitUnlocked()
                }
                is RemoteRefresh.Diverged -> {
                    if (status.count > 0) {
                        AppEventLog.info("git", "В Git другая версия, загрузка (${status.count} чел.)")
                        return refreshFromGitUnlocked()
                    }
                    return status
                }
                is RemoteRefresh.Failed -> {
                    if (NetworkHelper.looksLikeNetworkIssue(status.message) && attempt < 4) {
                        delay(2_000L)
                        return@repeat
                    }
                    return status
                }
                is RemoteRefresh.Unchanged -> {
                    if (forceEmptyLocal && mutex.withLock { readLocked().people.isEmpty() }) {
                        return refreshFromGitUnlocked()
                    }
                    return status
                }
                else -> return status
            }
        }
        return null
    }

    suspend fun inspectGit(): RemoteRefresh = gitMutex.withLock { inspectGitUnlocked() }

    private suspend fun inspectGitUnlocked(): RemoteRefresh {
        val look = lookAtGit() ?: return RemoteRefresh.NotConfigured
        return look.fold(
            onSuccess = { snapshot ->
                val relation = relateGit(
                    snapshot.localHash,
                    snapshot.remoteHash,
                    snapshot.remote?.sha,
                    snapshot.marker?.hash,
                    snapshot.marker?.sha,
                )
                val result = inspectGitRefresh(
                    localPeopleCount = snapshot.local.people.size,
                    remotePeopleCount = snapshot.remoteDoc?.people?.size,
                    remoteFilePresent = snapshot.remote != null,
                    relation = relation,
                )
                if (result is RemoteRefresh.Unchanged && relation == GitRelation.Same) {
                    snapshot.remote?.let { writeMarker(it.sha, snapshot.localHash) }
                }
                result
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = NetworkHelper.toUserMessage(error) ?: "Не удалось получить данные из Git"
                AppEventLog.error("git", message, error)
                RemoteRefresh.Failed(message)
            },
        )
    }

    suspend fun refreshFromGit(): RemoteRefresh = gitMutex.withLock { refreshFromGitUnlocked() }

    private suspend fun refreshFromGitUnlocked(): RemoteRefresh {
        val settings = settingsRepository.settings.first()
        val look = lookAtGit() ?: return RemoteRefresh.NotConfigured
        return look.fold(
            onSuccess = { snapshot ->
                val remote = snapshot.remote
                    ?: return RemoteRefresh.Failed(
                        "В Git нет файла «${settings.githubPath}» (${settings.githubOwner}/${settings.githubRepo}). " +
                            "Если репозиторий только создан — на телефоне с древом нажмите «Отправить в Git сейчас». " +
                            "Иначе проверьте интернет и токен.",
                    )
                val remoteDoc = snapshot.remoteDoc
                    ?: return RemoteRefresh.Failed("Файл в Git не удалось прочитать")
                val localEmpty = snapshot.local.people.isEmpty()
                val remoteHasPeople = remoteDoc.people.isNotEmpty()
                if (localEmpty && remoteHasPeople) {
                    applyDocumentFromGit(remoteDoc, remote.sha)
                } else if (snapshot.remoteHash == snapshot.localHash) {
                    writeMarker(remote.sha, snapshot.localHash)
                    RemoteRefresh.Unchanged
                } else {
                    applyDocumentFromGit(remoteDoc, remote.sha)
                }
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = NetworkHelper.toUserMessage(error) ?: "Не удалось получить данные из Git"
                AppEventLog.error("git", message, error)
                RemoteRefresh.Failed(message)
            },
        )
    }

    private suspend fun applyDocumentFromGit(remoteDoc: TreeDocument, remoteSha: String): RemoteRefresh {
        pushJob?.cancel()
        pushJob = null
        return try {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    snapshotLocked(readLocked())
                    replaceLocked(remoteDoc)
                    generation += 1
                }
            }
            writeMarker(remoteSha, TreeCodec.contentHash(remoteDoc))
            pendingUploadState.value = false
            AppEventLog.info("git", "Древо загружено из Git: ${remoteDoc.people.size} чел.")
            RemoteRefresh.Updated(remoteDoc.people.size)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val message = NetworkHelper.toUserMessage(error) ?: (error.message ?: "Не удалось применить древо из Git")
            AppEventLog.error("git", message, error)
            RemoteRefresh.Failed(message)
        }
    }

    suspend fun pushNow(): String = gitMutex.withLock { pushNowUnlocked() }

    private suspend fun pushNowUnlocked(): String {
        pushJob?.cancel()
        val settings = settingsRepository.settings.first()
        val sync = syncFor(settings)
        return when (val result = pushToGit(force = true)) {
            GitPushResult.Uploaded -> {
                withContext(Dispatchers.IO) {
                    val remote = sync.fetchForPush()
                        ?: throw IllegalStateException(
                            "GitHub не показал файл «${settings.githubPath}» после отправки. " +
                                "Проверьте токен (право contents) и репозиторий ${settings.githubOwner}/${settings.githubRepo}.",
                        )
                    val people = TreeCodec.decode(remote.text).people.size
                    AppEventLog.info(
                        "git",
                        "Push подтверждён: ${settings.githubOwner}/${settings.githubRepo}, $people чел., sha ${remote.sha.take(7)}",
                    )
                }
                "Древо отправлено в Git: ${settings.githubOwner}/${settings.githubRepo}, файл ${settings.githubPath}"
            }
            GitPushResult.AlreadySame ->
                "Git уже совпадает с телефоном — повторная отправка не нужна."
            GitPushResult.DeferredBecauseLocalChanged ->
                throw IllegalStateException(
                    "Древо изменилось во время отправки. Подождите пару секунд и нажмите «Отправить в Git сейчас» ещё раз.",
                )
        }
    }

    /** PNG всего древа в Git (для разбора ошибок компоновки). Не трогает family/tree.json. */
    suspend fun pushTreeSnapshotToGit(): String = gitMutex.withLock {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) {
            throw IllegalStateException("Git не настроен — снимок некуда отправить.")
        }
        val snapshotData = mutex.withLock {
            Triple(peopleDao.all(), unionDao.all(), openLinksState.value)
        }
        val (people, unions, links) = snapshotData
        val living = people.count { !it.vacant }
        if (living == 0) throw IllegalStateException("Древо пустое — снимать нечего.")
        val home = settings.homePersonId
        val png = withContext(Dispatchers.Default) {
            TreeSnapshotRenderer.renderPng(context, people, unions, links, home)
        }
        val sync = syncFor(settings)
        val message = "TreeX layout snapshot ${BuildConfig.VERSION_NAME}, $living people"
        withContext(Dispatchers.IO) {
            val sha = sync.fetchShaAt(DEBUG_TREE_SNAPSHOT_PATH)
            sync.pushBytesAt(DEBUG_TREE_SNAPSHOT_PATH, png, sha, message)
        }
        AppEventLog.info("debug", "Layout snapshot → Git ($living people)")
        "Снимок отправлен: ${settings.githubOwner}/${settings.githubRepo}, $DEBUG_TREE_SNAPSHOT_PATH"
    }

    /** Удаляет все «пустые места» (остатки после удаления карточек). */
    suspend fun purgeAllVacantSlots(): Int {
        var removed = 0
        mutex.withLock {
            database.withTransaction {
                snapshotLocked(readLocked())
                var people = peopleDao.all()
                while (true) {
                    val vacant = people.find { it.vacant } ?: break
                    purgePersonLocked(vacant, people)
                    removed++
                    people = peopleDao.all()
                }
            }
        }
        if (removed > 0) schedulePush()
        return removed
    }

    /** Временная очистка: снимок в истории, затем удаляются все люди и союзы. */
    suspend fun wipeTree(): String {
        mutex.withLock {
            database.withTransaction {
                snapshotLocked(readLocked())
                peopleDao.all().forEach { person ->
                    if (person.photoFile.isNotBlank()) {
                        PhotoStore.delete(context, person.photoFile)
                    }
                }
                peopleDao.deleteAll()
                unionDao.deleteAll()
                writeOpenLinks(emptyList())
                generation += 1
            }
        }
        markerFile().delete()
        settingsRepository.update { it.copy(pauseGitAutoSync = true, homePersonId = "") }
        return "Древо очищено. Автозагрузка из Git отключена — включите в настройках, когда закончите отладку."
    }

    suspend fun remoteCommits(): List<GitHubTreeSync.RemoteCommit> {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) {
            throw IllegalStateException(
                "Git не настроен. Укажите репозиторий в настройках разработчика или соберите приложение с family Git в local.properties.",
            )
        }
        return withContext(Dispatchers.IO) {
            val sync = syncFor(settings)
            val remote = runCatching { sync.fetch() }.getOrElse { error ->
                if (error is IllegalStateException) throw error
                throw IllegalStateException(error.message ?: "Не удалось прочитать Git")
            }
            if (remote == null) {
                throw IllegalStateException(
                    "В ${settings.githubOwner}/${settings.githubRepo} ещё нет файла «${settings.githubPath}». " +
                        "В настройках разработчика нажмите «Отправить в Git сейчас».",
                )
            }
            val commits = runCatching { sync.listCommits() }.getOrElse { emptyList() }
            commits.ifEmpty {
                listOf(
                    GitHubTreeSync.RemoteCommit(
                        sha = remote.sha,
                        message = "Текущая версия в Git",
                        date = "",
                    ),
                )
            }
        }
    }

    suspend fun restoreRemote(sha: String) {
        require(sha.matches(Regex("[0-9a-fA-F]{7,64}"))) { "Некорректная версия" }
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) throw IllegalStateException("Git не настроен")
        val remote = withContext(Dispatchers.IO) { syncFor(settings).fetchExact(sha) }
        val document = TreeCodec.decode(remote.text)
        pushJob?.cancel()
        pushJob = null
        withContext(Dispatchers.IO) {
            mutex.withLock {
                snapshotLocked(readLocked())
                replaceLocked(document)
                generation += 1
            }
        }
        schedulePush()
    }

    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = null
        pendingUploadState.value = true
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

    private enum class GitPushResult {
        Uploaded,
        AlreadySame,
        DeferredBecauseLocalChanged,
    }

    private suspend fun pushToGit(force: Boolean): GitPushResult {
        val settings = settingsRepository.settings.first()
        if (!settings.gitConfigured) throw IllegalStateException("Git не настроен")
        val captured = mutex.withLock { generation to TreeCodec.encode(readLocked()) }
        val localDoc = TreeCodec.decode(captured.second)
        val localCount = localDoc.people.size
        if (localCount == 0) {
            throw IllegalStateException("Пустое древо нельзя отправлять в Git. Сначала загрузите древо из Git.")
        }
        val sync = syncFor(settings)
        val remote = withContext(Dispatchers.IO) { sync.fetchForPush() }
        val localHash = TreeCodec.contentHash(localDoc)
        val marker = readMarker()
        if (remote != null) {
            val remoteDoc = runCatching { TreeCodec.decode(remote.text) }.getOrNull()
            if (remoteDoc == null) {
                if (!force) {
                    throw IllegalStateException(
                        "В Git лежит файл «${settings.githubPath}» не формата HDLabs. " +
                            "В настройках разработчика нажмите «Отправить в Git сейчас», чтобы заменить его.",
                    )
                }
            } else {
                val remoteCount = remoteDoc.people.size
                if (remoteCount > 0 && localCount < remoteCount && !force) {
                    throw IllegalStateException(
                        "В Git $remoteCount чел., на телефоне $localCount. Сначала загрузите древо из Git — иначе можно стереть семейные данные.",
                    )
                }
                val remoteHash = TreeCodec.contentHash(remoteDoc)
                if (remoteHash == localHash) {
                    writeMarker(remote.sha, localHash)
                    pendingUploadState.value = false
                    return GitPushResult.AlreadySame
                }
                val relation = relateGit(localHash, remoteHash, remote.sha, marker?.hash, marker?.sha)
                if (!force && (relation == GitRelation.RemoteNewer || relation == GitRelation.Diverged)) {
                    throw IllegalStateException(
                        "В Git другая версия. На главном экране нажмите обновление и выберите: загрузить Git или отправить телефон.",
                    )
                }
            }
        } else if (!force) {
            throw IllegalStateException(
                "Не удалось прочитать древо из Git. Автоотправка отменена, чтобы не затереть семейное древо.",
            )
        }
        val stillCurrent = mutex.withLock { generation == captured.first }
        if (!stillCurrent) {
            schedulePush()
            return GitPushResult.DeferredBecauseLocalChanged
        }
        val count = TreeCodec.decode(captured.second).people.size
        val commitMessage = treeGitCommitMessage(settings.userFio(), count)
        val sha = withContext(Dispatchers.IO) {
            sync.push(captured.second, remote?.sha, commitMessage)
        }
        writeMarker(sha, localHash)
        pendingUploadState.value = false
        val changedDuringUpload = mutex.withLock { generation != captured.first }
        if (changedDuringUpload) schedulePush()
        return GitPushResult.Uploaded
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
        unions = unionDao.all().map {
            UnionRecord(
                id = it.id,
                partnerAId = it.partnerAId,
                partnerBId = it.partnerBId,
                marriedOn = it.marriedOn,
                childIds = it.childIdsList(),
                unionType = it.unionType,
            )
        },
    )

    private suspend fun replaceLocked(document: TreeDocument) {
        val normalized = TreeCodec.normalize(document)
        database.withTransaction {
            peopleDao.deleteAll()
            unionDao.deleteAll()
            peopleDao.upsertAll(normalized.people.map { it.toEntity() })
            val entities = normalized.people.map { it.toEntity() }
            val rawUnions = normalized.unions.map {
                UnionEntity(
                    id = it.id,
                    partnerAId = it.partnerAId,
                    partnerBId = it.partnerBId,
                    marriedOn = it.marriedOn,
                    childIds = it.childIds.joinToString(","),
                    unionType = it.unionType,
                )
            }
            unionDao.upsertAll(UnionEr.reconcileChildMembership(entities, rawUnions))
        }
        pruneOpenLinks(normalized.people.map { it.id }.toSet())
    }

    private suspend fun snapshotLocked(document: TreeDocument) {
        if (document.people.isEmpty() && document.unions.isEmpty()) return
        val text = TreeCodec.encode(document)
        val dir = historyDir()
        val latest = dir.listFiles { file -> file.extension == "json" }?.maxByOrNull { it.name }
        if (latest != null && latest.readText() == text) return
        val name = SnapshotNames.fileName(Instant.now(), ZoneId.systemDefault())
        File(dir, name).writeText(text)
        val author = settingsRepository.settings.first().userFio()
        if (author.isNotBlank()) {
            snapshotAuthorFile(File(dir, name)).writeText(author)
        }
        val files = dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
        if (files.size > 30) {
            files.take(files.size - 30).forEach { file ->
                file.delete()
                snapshotAuthorFile(file).delete()
            }
        }
    }

    private fun snapshotAuthorFile(snapshotJson: File): File =
        File(snapshotJson.parentFile, snapshotJson.nameWithoutExtension + ".author")

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
