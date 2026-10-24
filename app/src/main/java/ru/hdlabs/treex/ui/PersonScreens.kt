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
import kotlinx.coroutines.withContext
import ru.hdlabs.treex.TreeXApplication
import ru.hdlabs.treex.data.PhotoStore
import ru.hdlabs.treex.data.TreeState
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.domain.RelativeAttach
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.formatSpouseDrafts
import ru.hdlabs.treex.domain.fullName
import ru.hdlabs.treex.domain.lifeLine
import ru.hdlabs.treex.domain.parseSpouseDrafts
import ru.hdlabs.treex.domain.spouseDraftsOf
import ru.hdlabs.treex.domain.spouseIdsOf
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
                if (lastName.isBlank()) lastName = host.lastName
                if (gender == "u") gender = "m"
            }
            "mother" -> {
                if (lastName.isBlank()) lastName = host.lastName
                if (gender == "u") gender = "f"
            }
            "spouse" -> {
                if (gender == "u") {
                    gender = when (host.gender) {
                        "m" -> "f"
                        "f" -> "m"
                        else -> "u"
                    }
                }
                if (spouseRaw.isBlank()) spouseRaw = host.id
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
                if (fatherId.isBlank()) fatherId = father
                if (motherId.isBlank()) motherId = mother
                if (lastName.isBlank()) {
                    lastName = current.people.find { it.id == father }?.lastName ?: host.lastName
                }
            }
        }
        shaped = true
    }
    LaunchedEffect(role, gender, fatherId, shaped, middleTouched) {
        if (role != "child" || !shaped || middleTouched || middleName.isNotBlank()) return@LaunchedEffect
        val father = tree.people.find { it.id == fatherId } ?: return@LaunchedEffect
        val suggested = suggestPatronymic(father.firstName, gender)
        if (suggested.isNotBlank()) middleName = suggested
    }
    LaunchedEffect(signature, ready) {
        if (!ready || !hasName) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500)
        try {
            repo.savePerson(draft, spouseDrafts, snapshot = false, attach = attach)
            status = "Сохранено в приложении"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = e.message ?: "Не удалось сохранить"
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                photoFile = withContext(Dispatchers.IO) { PhotoStore.save(context, personId, uri) }
            } catch (e: Exception) {
                status = e.message ?: "Не удалось открыть фото"
            }
        }
    }

    fun finish() {
        scope.launch {
            try {
                if (ready && hasName) repo.savePerson(draft, spouseDrafts, snapshot = true, attach = attach)
                if (hasName) nav.previousBackStackEntry?.savedStateHandle?.set("highlight", personId)
                nav.popBackStack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Не удалось сохранить"
            }
        }
    }
    BackHandler { finish() }

    val scale by animateFloatAsState(if (appeared) 1f else 0.94f, spring(dampingRatio = 0.78f, stiffness = 380f), label = "formScale")
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(280), label = "formAlpha")
    val duplicate = tree.people.any {
        it.id != personId &&
            it.lastName.equals(draft.lastName, true) &&
            it.firstName.equals(draft.firstName, true) &&
            it.middleName.equals(draft.middleName, true) &&
            draft.lastName.isNotBlank()
    }

    val screenTitle = when {
        !creating -> "Карточка"
        role == "father" -> "Новый отец"
        role == "mother" -> "Новая мать"
        role == "spouse" -> "Новый супруг"
        role == "child" -> "Новый ребёнок"
        else -> "Новый человек"
    }
    HdScaffold(title = screenTitle, onBack = { finish() }) {
        Column(Modifier.fillMaxWidth().scale(scale).alpha(alpha)) {
            HdCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PersonAvatar(draft, 96.dp, Modifier.clickable { photoPicker.launch("image/*") })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { HdSecondaryButton("Фото") { photoPicker.launch("image/*") } }
                    if (photoFile.isNotBlank()) {
                        Box(Modifier.weight(1f)) { HdSecondaryButton("Убрать фото") { photoFile = "" } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HdTextField(lastName, { lastName = it }, "Фамилия")
                HdTextField(birthLastName, { birthLastName = it }, "Девичья фамилия")
                HdTextField(firstName, { firstName = it }, "Имя")
                HdTextField(middleName, { middleTouched = true; middleName = it }, "Отчество")
                if (duplicate) Text("Такое ФИО уже есть в древе", color = HdOrange, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                HdTextField(phone, { phone = it }, "Телефон", keyboardType = KeyboardType.Phone)
                HdTextField(email, { email = it }, "Почта", keyboardType = KeyboardType.Email)
            }
            Spacer(Modifier.height(12.dp))
            HdCard {
                Text("Пол", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenderChoice("Муж", gender == "m", Modifier.weight(1f)) { gender = "m" }
                    GenderChoice("Жен", gender == "f", Modifier.weight(1f)) { gender = "f" }
                    GenderChoice("Не указан", gender == "u", Modifier.weight(1f)) { gender = "u" }
                }
            }
            Spacer(Modifier.height(12.dp))
            HdCard {
                Text("Связи", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Отец, мать и супруги собирают поколение на главном экране.", color = HdTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                RelationRow("Отец", tree.people.find { it.id == fatherId }?.fullName() ?: "не выбран", { picker = Picker.Father }, { fatherId = "" })
                RelationRow("Мать", tree.people.find { it.id == motherId }?.fullName() ?: "не выбрана", { picker = Picker.Mother }, { motherId = "" })
                spouseDrafts.forEach { spouse ->
                    val name = tree.people.find { it.id == spouse.personId }?.fullName() ?: spouse.personId
                    RelationRow("Супруг", name, null) {
                        spouseRaw = formatSpouseDrafts(spouseDrafts.filterNot { it.personId == spouse.personId })
                    }
                    HdTextField(
                        spouse.marriedOn,
                        { value ->
                            spouseRaw = formatSpouseDrafts(
                                spouseDrafts.map { if (it.personId == spouse.personId) it.copy(marriedOn = value) else it },
                            )
                        },
                        "Дата брака, ГГГГ-ММ-ДД",
                    )
                }
                HdSecondaryButton("Добавить супруга") { picker = Picker.Spouse }
            }
            Spacer(Modifier.height(12.dp))
            HdCard {
                Text("Даты и заметка", color = HdTextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                HdTextField(birthDate, { birthDate = it }, "Дата рождения, ГГГГ-ММ-ДД")
                HdTextField(deathDate, { deathDate = it }, "Дата смерти, если есть")
                HdTextField(birthPlace, { birthPlace = it }, "Место рождения")
                HdTextField(notes, { notes = it }, "Заметка", singleLine = false)
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
        PersonPickDialog(
            title = when (which) {
                Picker.Father -> "Отец"
                Picker.Mother -> "Мать"
                Picker.Spouse -> "Супруг"
            },
            people = tree.people.filter { it.id !in exclude },
            onDismiss = { picker = null },
            onPick = { id ->
                when (which) {
                    Picker.Father -> fatherId = id
                    Picker.Mother -> motherId = id
                    Picker.Spouse -> spouseRaw = formatSpouseDrafts(spouseDrafts + SpouseDraft(id))
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
                    LinkLine("Супруг", spouse, nav, if (married.isBlank()) "" else ", брак $married")
                }
            }
            if (children.isEmpty()) Text("Детей в древе нет", color = HdTextSecondary, modifier = Modifier.padding(top = 6.dp))
            else children.forEach { LinkLine("Ребёнок", it, nav) }
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
        HdSecondaryButton("Добавить супруга") { nav.navigate(Routes.edit("new", "spouse", person.id)) }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Добавить ребёнка") { nav.navigate(Routes.edit("new", "child", person.id)) }
        Spacer(Modifier.height(12.dp))
        HdPrimaryButton("Редактировать") { nav.navigate(Routes.edit(person.id)) }
        Spacer(Modifier.height(8.dp))
        HdSecondaryButton("Удалить карточку") { confirmDelete = true }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить карточку?") },
            text = { Text("Человек исчезнет из древа. Снимок до удаления останется в истории.", color = HdTextSecondary) },
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
    Text(
        "$label: ${person?.fullName() ?: "не указан"}$suffix",
        color = if (person == null) HdTextSecondary else HdBlue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = person != null) { person?.let { nav.navigate(Routes.person(it.id)) } },
    )
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

@Composable
private fun PersonPickDialog(
    title: String,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (people.isEmpty()) Text("Сначала добавьте других людей", color = HdTextSecondary)
                people.sortedBy { it.fullName() }.forEach { person ->
                    Text(
                        person.fullName(),
                        color = HdTextPrimary,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(person.id) }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { HdSecondaryButton("Закрыть") { onDismiss() } },
    )
}
