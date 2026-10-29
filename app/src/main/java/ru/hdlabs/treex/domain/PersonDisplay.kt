package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.backup.UnionRecord
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun PersonEntity.isVacantSlot(): Boolean = vacant

fun PersonEntity.fullName(): String {
    if (vacant) return "Пустое место"
    val surname = if (
        gender == "f" &&
        birthLastName.isNotBlank() &&
        !birthLastName.equals(lastName, ignoreCase = true)
    ) {
        "$lastName ($birthLastName)"
    } else {
        lastName
    }
    return listOf(surname, firstName, middleName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Без имени" }
}

fun PersonEntity.initials(): String {
    val letter = lastName.firstOrNull() ?: firstName.firstOrNull() ?: '?'
    return letter.uppercase()
}

fun PersonEntity.yearsLabel(): String {
    val birth = formatCardDate(birthDate)
    val death = formatCardDate(deathDate)
    return when {
        birth != null && death != null -> "$birth — $death"
        birth != null -> birth
        death != null -> death
        else -> ""
    }
}

fun formatCardDate(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    return parseStoredDate(trimmed)?.format(CARD_DATE)
}

fun acceptsAsParent(slot: String, candidate: PersonEntity): Boolean = when (slot) {
    "father" -> candidate.vacant || candidate.gender == "m"
    "mother" -> candidate.vacant || candidate.gender == "f"
    else -> true
}

fun PersonEntity.lifeLine(today: LocalDate = LocalDate.now()): String {
    val years = yearsLabel()
    val age = ageLabel(today)
    return listOf(years, age).filter { it.isNotBlank() }.joinToString(" · ")
}

fun PersonEntity.ageLabel(today: LocalDate = LocalDate.now()): String {
    val birth = parseStoredDate(birthDate) ?: return ""
    val end = when {
        deathDate.isBlank() -> today
        else -> parseStoredDate(deathDate) ?: return ""
    }
    if (end.isBefore(birth)) return ""
    val years = Period.between(birth, end).years
    val prefix = if (deathDate.isBlank()) "" else "прожил${if (gender == "f") "а" else ""} "
    return prefix + "$years ${ageWord(years)}"
}

fun suggestPatronymic(fatherName: String, gender: String): String {
    val name = fatherName.trim()
    if (name.isBlank() || (gender != "m" && gender != "f")) return ""
    specialPatronymic[name.lowercase()]?.let { pair ->
        return if (gender == "f") pair.second else pair.first
    }
    val soft = name.last() == 'й' || name.last() == 'ь'
    val stem = if (soft) name.dropLast(1) else name
    val suffix = if (gender == "f") {
        if (soft) "евна" else "овна"
    } else {
        if (soft) "евич" else "ович"
    }
    return stem + suffix
}

private val specialPatronymic = mapOf(
    "лев" to ("Львович" to "Львовна"),
    "пётр" to ("Петрович" to "Петровна"),
    "петр" to ("Петрович" to "Петровна"),
    "павел" to ("Павлович" to "Павловна"),
    "михаил" to ("Михайлович" to "Михайловна"),
    "яков" to ("Яковлевич" to "Яковлевна"),
    "никита" to ("Никитич" to "Никитична"),
    "илья" to ("Ильич" to "Ильинична"),
    "кузьма" to ("Кузьмич" to "Кузьминична"),
    "фома" to ("Фомич" to "Фоминична"),
    "лука" to ("Лукич" to "Лукинична"),
)

private fun ageWord(years: Int): String {
    val mod = years % 100
    val last = years % 10
    return when {
        mod in 11..14 -> "лет"
        last == 1 -> "год"
        last in 2..4 -> "года"
        else -> "лет"
    }
}

private val CARD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun parseStoredDate(value: String): LocalDate? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    return try {
        when {
            Regex("""^\d{4}$""").matches(trimmed) ->
                LocalDate.of(trimmed.toInt(), 1, 1)
            else -> {
                Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(trimmed)?.let { match ->
                    LocalDate.of(
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[3].toInt(),
                    )
                } ?: Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(trimmed)?.let { match ->
                    LocalDate.of(
                        match.groupValues[3].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[1].toInt(),
                    )
                }
            }
        }
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun yearOf(value: String): String? = Regex("""(\d{4})""").find(value)?.groupValues?.get(1)

fun PersonEntity.matchesQuery(query: String): Boolean {
    if (vacant) return false
    if (query.isBlank()) return true
    val haystack = listOf(lastName, birthLastName, firstName, middleName, phone, email, birthPlace, notes, birthDate, deathDate)
        .joinToString(" ")
        .lowercase()
    return query.trim().lowercase().split(Regex("\\s+")).all { it in haystack }
}

fun spouseNoun(gender: String): String = if (gender == "m") "Супруга" else "Супруг"

fun addSpouseLabel(gender: String): String = if (gender == "m") "Добавить супругу" else "Добавить супруга"

fun acceptsAsSpouse(hostGender: String, candidateGender: String): Boolean = when (hostGender) {
    "m" -> candidateGender == "f"
    "f" -> candidateGender == "m"
    else -> true
}

fun spouseIdsOf(unions: List<UnionRecord>, personId: String): List<String> =
    unions.mapNotNull { union ->
        when (personId) {
            union.partnerAId -> union.partnerBId
            union.partnerBId -> union.partnerAId
            else -> null
        }
    }.distinct()

fun stableUnionId(firstId: String, secondId: String): String {
    val a = minOf(firstId, secondId)
    val b = maxOf(firstId, secondId)
    return "u-$a-$b"
}
