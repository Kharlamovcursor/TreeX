package ru.hdlabs.treex.domain

import java.util.Locale

private val NAME_LOCALE = Locale.forLanguageTag("ru")

fun capitalizePersonName(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.split('-')
                .filter { it.isNotBlank() }
                .joinToString("-") { part -> capitalizeToken(part) }
        }
}

private fun capitalizeToken(part: String): String {
    val lower = part.lowercase(NAME_LOCALE)
    if (lower.isEmpty()) return ""
    return lower.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(NAME_LOCALE) else char.toString()
    }
}
