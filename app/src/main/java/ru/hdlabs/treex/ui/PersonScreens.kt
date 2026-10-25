package ru.hdlabs.treex.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.data.PhotoStore
import ru.hdlabs.treex.data.TreeState
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.acceptsAsParent
import ru.hdlabs.treex.domain.acceptsAsSpouse
import ru.hdlabs.treex.domain.addSpouseLabel
import ru.hdlabs.treex.domain.RelativeAttach
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.formatSpouseDrafts
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.isVacantSlot
import ru.hdlabs.treex.domain.lifeLine
import ru.hdlabs.treex.domain.parseSpouseDrafts
import ru.hdlabs.treex.domain.spouseDraftsOf
import ru.hdlabs.treex.domain.spouseIdsOf
import ru.hdlabs.treex.domain.halfSiblingCaption
import ru.hdlabs.treex.domain.siblingGroupsOf
import ru.hdlabs.treex.domain.spouseNoun
import ru.hdlabs.treex.domain.suggestPatronymic
import ru.hdlabs.treex.navigation.Routes
import ru.hdlabs.treex.ui.components.HdCard
import ru.hdlabs.treex.ui.components.HdPrimaryButton
import ru.hdlabs.treex.ui.components.HdScaffold
import ru.hdlabs.treex.ui.components.HdSecondaryButton
import ru.hdlabs.treex.ui.components.HdTextField
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdDanger
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.HdPanel
import ru.hdlabs.treex.ui.theme.HdTextPrimary
import ru.hdlabs.treex.ui.theme.HdTextSecondary
import java.util.UUID

@Composable
fun PersonEditorScreen(routeId: String, role: String, anchor: String, nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val repo = app.container.treeRepository
    val creating = routeId == "new"
    val personId = rememberSaveable(routeId) { if (creating) UUID.randomUUID().toString() else routeId }
    val tree by repo.observe().collectAsStateWithLifecycle(TreeState())
    var ready by rememberSaveable(personId) { mutableStateOf(creating) }
    var lastName by rememberSaveable(personId) { mutableStateOf("") }
    var birthLastName by rememberSaveable(personId) { mutableStateOf("") }
    var firstName by rememberSaveable(personId) { mutableStateOf("") }
    var middleName by rememberSaveable(personId) { mutableStateOf("") }
    var gender by rememberSaveable(personId) { mutableStateOf("u") }
    var birthDate by rememberSaveable(personId) { mutableStateOf("") }
    var deathDate by rememberSaveable(personId) { mutableStateOf("") }
    var birthPlace by rememberSaveable(personId) { mutableStateOf("") }
    var email by rememberSaveable(personId) { mutableStateOf("") }
    var phone by rememberSaveable(personId) { mutableStateOf("") }
    var photoFile by rememberSaveable(personId) { mutableStateOf("") }
    var notes by rememberSaveable(personId) { mutableStateOf("") }
    var fatherId by rememberSaveable(personId) { mutableStateOf("") }
    var motherId by rememberSaveable(personId) { mutableStateOf("") }
    var spouseRaw by rememberSaveable(personId) { mutableStateOf("") }
    var createdAt by rememberSaveable(personId) { mutableLongStateOf(System.currentTimeMillis()) }
    var status by rememberSaveable(personId) { mutableStateOf("Карточка сохранится сама через пару секунд после ввода") }
    var shaped by rememberSaveable(personId) { mutableStateOf(false) }
    var middleTouched by rememberSaveable(personId) { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var appeared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val spouseDrafts = parseSpouseDrafts(spouseRaw)
    val spouseIds = spouseDrafts.map { it.personId }
    val attach = if (creating && anchor.isNotBlank() && role in setOf("father", "mother", "spouse")) {
        RelativeAttach(anchor, role)
    } else {
        null
    }
    val draft = PersonEntity(
        id = personId,
        lastName = lastName.trim(),
        birthLastName = birthLastName.trim(),
        firstName = firstName.trim(),
        middleName = middleName.trim(),
        gender = gender,
        birthDate = birthDate.trim(),
        deathDate = deathDate.trim(),
        birthPlace = birthPlace.trim(),
        email = email.trim(),
        phone = phone.trim(),
        photoFile = photoFile,
        notes = notes.trim(),
        fatherId = fatherId.ifBlank { null },
        motherId = motherId.ifBlank { null },
        createdAtMillis = createdAt,
    )
    val hasName = draft.lastName.isNotBlank() || draft.firstName.isNotBlank() || draft.middleName.isNotBlank()
    val signature = listOf(lastName, birthLastName, firstName, middleName, gender, birthDate, deathDate, birthPlace, email, phone, photoFile, notes, fatherId, motherId, spouseRaw).joinToString("|")
    val buffer = remember(personId) { EditorBuffer() }
    val saveGate = remember(personId) { Mutex() }
    var closing by remember(personId) { mutableStateOf(false) }
    buffer.person = draft
    buffer.spouses = spouseDrafts
    buffer.attach = attach
    buffer.hasName = hasName
    buffer.ready = ready
    val saveScope = app.container.scope

    suspend fun flush(snapshot: Boolean) {
        saveGate.withLock {
            while (true) {
                if (!buffer.ready || !buffer.hasName || buffer.touched.isEmpty()) return@withLock
                val epoch = buffer.epoch
                repo.savePerson(
                    buffer.person,
                    buffer.spouses,
                    snapshot = snapshot,
                    attach = buffer.attach,
                    preserveUntouched = true,
                    touched = buffer.touched,
                )
                if (buffer.epoch == epoch) return@withLock
            }
        }
    }

    LaunchedEffect(personId) {
        appeared = true
        repo.snapshotNow()
        if (!creating && !ready) {
            val current = repo.observe().first()
            val person = current.people.find { it.id == personId }
            if (person == null) {
                status = "Карточка не найдена"
                return@LaunchedEffect
            }
            lastName = person.lastName
            birthLastName = person.birthLastName
            firstName = person.firstName
            middleName = person.middleName
            gender = person.gender
            birthDate = person.birthDate
            deathDate = person.deathDate
            birthPlace = person.birthPlace
            email = person.email
            phone = person.phone
            photoFile = person.photoFile
            notes = person.notes
            fatherId = person.fatherId.orEmpty()
            motherId = person.motherId.orEmpty()
            spouseRaw = formatSpouseDrafts(spouseDraftsOf(current.unions, person.id))
            createdAt = person.createdAtMillis
            ready = true
        }
    }
    LaunchedEffect(creating, anchor, role, tree.people.size) {
        if (!creating || shaped || anchor.isBlank()) return@LaunchedEffect
        val current = repo.observe().first()
        val host = current.people.find { it.id == anchor } ?: return@LaunchedEffect
        val hostSpouses = spouseIdsOf(current.unions, host.id)
        when (role) {
            "father" -> {
                if (lastName.isBlank()) {
                    lastName = host.lastName
                    buffer.mark("lastName")
                }
                if (gender == "u") {
                    gender = "m"
                    buffer.mark("gender")
                }
            }
            "mother" -> {
                if (lastName.isBlank()) {
                    lastName = host.lastName
                    buffer.mark("lastName")
                }
                if (gender == "u") {
                    gender = "f"
                    buffer.mark("gender")
                }
            }
            "spouse" -> {
                if (gender == "u") {
                    gender = when (host.gender) {
                        "m" -> "f"
                        "f" -> "m"
                        else -> "u"
                    }
                    buffer.mark("gender")
                }
                if (spouseRaw.isBlank()) {
                    spouseRaw = host.id
                    buffer.mark("spouses")
                }
            }
            "child" -> {
                val father = if (host.gender == "m") {
                    host.id
                } else {
                    hostSpouses.firstOrNull { id -> current.people.find { it.id == id }?.gender == "m" }.orEmpty()
                }
                val mother = if (host.gender == "f") {
                    host.id
                } else {
                    hostSpouses.firstOrNull { id -> current.people.find { it.id == id }?.gender == "f" }.orEmpty()
                }
                if (fatherId.isBlank() && father.isNotBlank()) {
                    fatherId = father
                    buffer.mark("father")
                }
                if (motherId.isBlank() && mother.isNotBlank()) {
                    motherId = mother
                    buffer.mark("mother")
                }
                if (lastName.isBlank()) {
                    lastName = current.people.find { it.id == father }?.lastName ?: host.lastName
                    buffer.mark("lastName")
                }
            }
        }
        shaped = true
    }
    LaunchedEffect(role, gender, fatherId, shaped, middleTouched) {
        if (role != "child" || !shaped || middleTouched || middleName.isNotBlank()) return@LaunchedEffect
        val father = tree.people.find { it.id == fatherId } ?: return@LaunchedEffect
        val suggested = suggestPatronymic(father.firstName, gender)
        if (suggested.isNotBlank()) {
            middleName = suggested
            buffer.mark("middleName")
        }
    }
    LaunchedEffect(signature, ready) {
        if (!ready || !hasName || buffer.touched.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500)
        try {
            flush(snapshot = false)
            status = "Сохранено в приложении"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = e.message ?: "Не удалось сохранить"
        }
    }
    DisposableEffect(personId) {
        onDispose {
            val shotReady = buffer.ready
            val shotNamed = buffer.hasName
            val shotTouched = buffer.touched.isNotEmpty()
            if (shotReady && shotNamed && shotTouched) {
                saveScope.launch {
                    try {
                        flush(snapshot = true)
                    } catch (_: CancellationException) {
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                photoFile = withContext(Dispatchers.IO) { PhotoStore.save(context, personId, uri) }
                buffer.mark("photo")
            } catch (e: Exception) {
                status = e.message ?: "Не удалось открыть фото"
            }
        }
    }

    fun finish() {
        if (closing) return
        closing = true
        saveScope.launch {
            try {
                flush(snapshot = true)
                withContext(Dispatchers.Main) {
                    if (hasName) nav.previousBackStackEntry?.savedStateHandle?.set("highlight", personId)
                    nav.popBackStack()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    closing = false
                    status = e.message ?: "Не удалось сохранить"
                }
            }
        }
    }
    BackHandler { finish() }

    val scale by animateFloatAsState(if (appeared) 1f else 0.94f, spring(dampingRatio = 0.78f, stiffness = 380f), label = "formScale")
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(280), label = "formAlpha")
    val duplicate = tree.people.any {
        it.id != personId &&
            !it.vacant &&
            it.lastName.equals(draft.lastName, true) &&
            it.firstName.equals(draft.firstName, true) &&
            it.middleName.equals(draft.middleName, true) &&
            draft.lastName.isNotBlank()
    }

    val host = tree.people.find { it.id == anchor }
    val screenTitle = when {
        !creating -> "Изменить"
        role == "father" -> "Новый отец"
        role == "mother" -> "Новая мать"
        role == "spouse" && host?.gender == "m" -> "Новая супруга"
        role == "spouse" && host?.gender == "f" -> "Новый супруг"
        role == "spouse" -> "Новый супруг"
        role == "child" -> "Новый ребёнок"
        role == "branch" -> "Новая отдельная ветка"
        else -> "Новый человек"
    }
    val relationHint = when {
        role == "branch" && creating ->
            "Карточка появится отдельно на древе. Развивайте ветку родителями, детьми и супругами. " +
                "Связать с основным древом можно с экрана древа — «Связать с основным древом»."
        !creating || host == null -> ""
        role == "father" -> "Это отец для ${host.fullName()}. Фамилия подставится сама."
        role == "mother" -> "Это мать для ${host.fullName()}. Фамилия подставится сама."
        role == "spouse" && host.gender == "m" -> "Это супруга для ${host.fullName()}."
        role == "spouse" -> "Это супруг для ${host.fullName()}."
        role == "child" -> "Это ребёнок для ${host.fullName()}. Фамилия и отчество подставятся сами."
        else -> ""
    }
    HdScaffold(title = screenTitle, onBack = { finish() }) {
        Column(Modifier.fillMaxWidth().scale(scale).alpha(alpha)) {
            if (relationHint.isNotBlank()) {
                Text(relationHint, color = HdOrange, fontWeight = FontWeight.SemiBold)
                Text(
                    "Достаточно фамилии и имени. Телефон, почту и даты можно дописать ниже или позже.",
                    color = HdTextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            HdCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PersonAvatar(draft, 96.dp, Modifier.clickable { photoPicker.launch("image/*") })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { HdSecondaryButton("Фото") { photoPicker.launch("image/*") } }
                    if (photoFile.isNotBlank()) {
                        Box(Modifier.weight(1f)) {
                            HdSecondaryButton("Убрать фото") {
                                buffer.mark("photo")
                                photoFile = ""
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HdTextField(lastName, { buffer.mark("lastName"); lastName = it }, "Фамилия")
                HdTextField(birthLastName, { buffer.mark("birthLastName"); birthLastName = it }, "Девичья фамилия")
                HdTextField(firstName, { buffer.mark("firstName"); firstName = it }, "Имя")
                HdTextField(middleName, { middleTouched = true; buffer.mark("middleName"); middleName = it }, "Отчество")
                if (duplicate) Text("Такое ФИО уже есть в древе", color = HdOrange, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                HdTextField(phone, { buffer.mark("phone"); phone = it }, "Телефон", keyboardType = KeyboardType.Phone)
                HdTextField(email, { buffer.mark("email"); email = it }, "Почта", keyboardType = KeyboardType.Email)
            }
            Spacer(Modifier.height(12.dp))
            HdCard {
                Text("Пол", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenderChoice("Муж", gender == "m", Modifier.weight(1f)) { buffer.mark("gender"); gender = "m" }
                    GenderChoice("Жен", gender == "f", Modifier.weight(1f)) { buffer.mark("gender"); gender = "f" }
                    GenderChoice("Не указан", gender == "u", Modifier.weight(1f)) { buffer.mark("gender"); gender = "u" }
                }
            }
            Spacer(Modifier.height(12.dp))
            HdCard {
                Text("К кому относится", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Кнопка выбирает уже существующего человека. Новый человек создаётся с древа.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                RelationRow("Отец", tree.people.find { it.id == fatherId }?.fullName() ?: "не выбран", { picker = Picker.Father }, { buffer.mark("father"); fatherId = "" })
                RelationRow("Мать", tree.people.find { it.id == motherId }?.fullName() ?: "не выбрана", { picker = Picker.Mother }, { buffer.mark("mother"); motherId = "" })
                spouseDrafts.forEach { spouse ->
                    val name = tree.people.find { it.id == spouse.personId }?.fullName() ?: spouse.personId
                    RelationRow("Супруг", name, null) {
                        buffer.mark("spouses")
                        spouseRaw = formatSpouseDrafts(spouseDrafts.filterNot { it.personId == spouse.personId })
                    }
                    HdTextField(
                        spouse.marriedOn,
                        { value ->
                            buffer.mark("spouses")
                            spouseRaw = formatSpouseDrafts(
                                spouseDrafts.map { if (it.personId == spouse.personId) it.copy(marriedOn = value) else it },
                            )
                        },
                        "Дата брака, ГГГГ-ММ-ДД",
                    )
                }
                HdSecondaryButton(addSpouseLabel(gender)) { picker = Picker.Spouse }
            }
            Spacer(Modifier.height(12.dp))
            HdCard {
                Text("Даты и заметка", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                HdTextField(birthDate, { buffer.mark("birthDate"); birthDate = it }, "Дата рождения, ГГГГ-ММ-ДД")
                HdTextField(deathDate, { buffer.mark("deathDate"); deathDate = it }, "Дата смерти, если есть")
                HdTextField(birthPlace, { buffer.mark("birthPlace"); birthPlace = it }, "Место рождения")
                HdTextField(notes, { buffer.mark("notes"); notes = it }, "Заметка", singleLine = false)
            }
            Spacer(Modifier.height(12.dp))
            Text(status, color = HdTextSecondary)
            Text(
                "На телефоне запись идёт через 1,5 секунды после паузы в наборе. В Git, если он настроен, версия уходит через 10 секунд тишины.",
                color = HdTextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            HdPrimaryButton(if (creating) "Добавить в древо" else "Сохранить и закрыть") { finish() }
            Spacer(Modifier.height(24.dp))
        }
    }

    picker?.let { which ->
        val exclude = buildSet {
            add(personId)
            when (which) {
                Picker.Father -> if (motherId.isNotBlank()) add(motherId)
                Picker.Mother -> if (fatherId.isNotBlank()) add(fatherId)
                Picker.Spouse -> addAll(spouseIds)
            }
        }
        val candidates = tree.people.filter { person ->
            person.id !in exclude &&
                when (which) {
                    Picker.Father -> acceptsAsParent("father", person)
                    Picker.Mother -> acceptsAsParent("mother", person)
                    Picker.Spouse -> acceptsAsSpouse(gender, person.gender)
                }
        }
        PersonPickDialog(
            title = when (which) {
                Picker.Father -> "Отец"
                Picker.Mother -> "Мать"
                Picker.Spouse -> spouseNoun(gender)
            },
            people = candidates,
            emptyText = when (which) {
                Picker.Father -> "Нет мужских карточек — создайте или укажите пол «мужской»."
                Picker.Mother -> "Нет женских карточек — создайте или укажите пол «женский»."
                Picker.Spouse -> if (tree.people.any { it.id !in exclude }) {
                    "Нет людей противоположного пола"
                } else {
                    "Сначала добавьте других людей"
                }
            },
            onDismiss = { picker = null },
            onPick = { id ->
                when (which) {
                    Picker.Father -> {
                        buffer.mark("father")
                        fatherId = id
                    }
                    Picker.Mother -> {
                        buffer.mark("mother")
                        motherId = id
                    }
                    Picker.Spouse -> {
                        buffer.mark("spouses")
                        spouseRaw = formatSpouseDrafts(spouseDrafts + SpouseDraft(id))
                    }
                }
                picker = null
            },
        )
    }
}

@Composable
fun PersonDetailScreen(personId: String, nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as TreeXApplication
    val tree by app.container.treeRepository.observe().collectAsStateWithLifecycle(TreeState())
    val person = tree.people.find { it.id == personId }
    var confirmDelete by remember { mutableStateOf(false) }
    if (person == null) {
        HdScaffold(title = "Карточка", onBack = { nav.popBackStack() }) {
            Text("Этой карточки уже нет", color = HdTextSecondary)
        }
        return
    }
    val spouses = spouseIdsOf(tree.unions, person.id).mapNotNull { id -> tree.people.find { it.id == id } }
    val children = tree.people.filter { it.fatherId == person.id || it.motherId == person.id }
    val siblings = siblingGroupsOf(person, tree.people)
    if (person.isVacantSlot()) {
        HdScaffold(title = "Пустое место", onBack = { nav.popBackStack() }) {
            Text("Здесь была карточка. Древо и связи сохранены.", color = HdTextSecondary)
            Spacer(Modifier.height(12.dp))
            HdPrimaryButton("Создать карточку") { nav.navigate(Routes.edit(person.id)) }
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("Изменить связи") { nav.navigate(Routes.edit(person.id)) }
            Spacer(Modifier.height(12.dp))
            HdSecondaryButton("Убрать место") { confirmDelete = true }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Убрать пустое место?") },
                text = { Text("Место исчезнет с древа.", color = HdTextSecondary) },
                confirmButton = {
                    HdPrimaryButton("Убрать") {
                        confirmDelete = false
                        app.container.scope.launch {
                            app.container.treeRepository.deletePerson(person.id)
                            withContext(Dispatchers.Main) { nav.popBackStack() }
                        }
                    }
                },
                dismissButton = { HdSecondaryButton("Отмена") { confirmDelete = false } },
            )
        }
        return
    }
    HdScaffold(title = person.fullName(), onBack = { nav.popBackStack() }) {
        HdCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PersonAvatar(person, 112.dp) }
            Spacer(Modifier.height(8.dp))
            val years = person.lifeLine()
            if (years.isNotBlank()) Text(years, color = HdTextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (person.birthPlace.isNotBlank()) Text(person.birthPlace, color = HdTextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(12.dp))
        if (person.phone.isNotBlank()) HdSecondaryButton("Позвонить ${person.phone}") {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phone}")))
        }
        if (person.email.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            HdSecondaryButton("Написать ${person.email}") {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${person.email}")))
            }
        }
        Spacer(Modifier.height(12.dp))
        HdCard {
            LinkLine("Отец", tree.people.find { it.id == person.fatherId }, nav)
            LinkLine("Мать", tree.people.find { it.id == person.motherId }, nav)
            if (spouses.isEmpty()) {
                Text("Супруг не указан", color = HdTextSecondary)
            } else {
                spouses.forEach { spouse ->
                    val married = tree.unions.find {
                        setOf(it.partnerAId, it.partnerBId) == setOf(person.id, spouse.id)
                    }?.marriedOn.orEmpty()
                    LinkLine(spouseNoun(person.gender), spouse, nav, if (married.isBlank()) "" else ", брак $married")
                }
            }
            if (children.isEmpty()) Text("Детей в древе нет", color = HdTextSecondary, modifier = Modifier.padding(top = 6.dp))
            else children.forEach { LinkLine("Ребёнок", it, nav) }
            if (siblings.full.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Братья и сёстры", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                siblings.full.forEach { LinkLine("Брат / сестра", it, nav) }
            }
            if (siblings.half.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Сводные братья и сёстры", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                siblings.half.forEach { half ->
                    LinkLine(
                        "Сводный родственник",
                        half,
                        nav,
                        ", ${halfSiblingCaption(person, half)}",
                    )
                }
            }
            if (person.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(person.notes, color = HdTextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (person.fatherId == null) {
            HdSecondaryButton("Добавить отца") { nav.navigate(Routes.edit("new", "father", person.id)) }
            Spacer(Modifier.height(8.dp))
        }
        if (person.motherId == null) {
            HdSecondaryButton("Добавить мать") { nav.navigate(Routes.edit("new", "mother", person.id)) }
            Spacer(Modifier.height(8.dp))
        }
        HdSecondaryButton(addSpouseLabel(person.gender)) { nav.navigate(Routes.edit("new", "spouse", person.id)) }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Добавить ребёнка") { nav.navigate(Routes.edit("new", "child", person.id)) }
        Spacer(Modifier.height(12.dp))
        HdPrimaryButton("Редактировать") { nav.navigate(Routes.edit(person.id)) }
        Spacer(Modifier.height(8.dp))
        Text(
            "В редакторе блок «К кому относится»: отец, мать, супруг — выбрать или убрать.",
            color = HdTextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Удалить карточку") { confirmDelete = true }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (person.isVacantSlot()) "Убрать пустое место?" else "Удалить карточку?") },
            text = {
                Text(
                    if (person.isVacantSlot()) {
                        "Место исчезнет с древа. Снимок сохранится в истории."
                    } else {
                        "Карточка станет пустым местом: древо и связи на месте. Снимок сохранится в истории."
                    },
                    color = HdTextSecondary,
                )
            },
            confirmButton = {
                HdPrimaryButton("Удалить") {
                    confirmDelete = false
                    app.container.scope.launch {
                        app.container.treeRepository.deletePerson(person.id)
                        withContext(Dispatchers.Main) { nav.popBackStack() }
                    }
                }
            },
            dismissButton = { HdSecondaryButton("Отмена") { confirmDelete = false } },
        )
    }
}

@Composable
private fun LinkLine(label: String, person: PersonEntity?, nav: NavHostController, suffix: String = "") {
    if (person == null) {
        Text("$label: не указан", color = HdTextSecondary, modifier = Modifier.padding(vertical = 4.dp))
    } else {
        HdSecondaryButton("$label: ${person.fullName()}$suffix") { nav.navigate(Routes.person(person.id)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RelationRow(label: String, value: String, onPick: (() -> Unit)?, onClear: () -> Unit) {
    Text("$label: $value", color = HdTextPrimary, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onPick != null) Box(Modifier.weight(1f)) { HdSecondaryButton("Выбрать") { onPick() } }
        Box(Modifier.weight(1f)) { HdSecondaryButton("Убрать") { onClear() } }
    }
}

@Composable
private fun GenderChoice(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) HdBlue.copy(alpha = 0.35f) else HdPanel),
        border = BorderStroke(1.dp, if (active) HdOrange else HdTextSecondary.copy(alpha = 0.35f)),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (active) HdTextPrimary else HdTextSecondary)
        }
    }
}

private enum class Picker { Father, Mother, Spouse }

private class EditorBuffer {
    var person: PersonEntity = PersonEntity(id = "")
    var spouses: List<SpouseDraft> = emptyList()
    var attach: RelativeAttach? = null
    var touched: Set<String> = emptySet()
    var hasName: Boolean = false
    var ready: Boolean = false
    var epoch: Int = 0

    fun mark(field: String) {
        if (field !in touched) touched = touched + field
        epoch += 1
    }
}

@Composable
private fun PersonPickDialog(
    title: String,
    people: List<PersonEntity>,
    emptyText: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (people.isEmpty()) {
                    Text(emptyText, color = HdTextSecondary)
                }
                people.sortedBy { it.fullName() }.forEach { person ->
                    HdSecondaryButton(person.fullName()) { onPick(person.id) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { HdSecondaryButton("Закрыть") { onDismiss() } },
    )
}
