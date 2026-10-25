package ru.hdlabs.treex.domain

import ru.hdlabs.treex.data.settings.AppSettings

fun formatUserFio(lastName: String, firstName: String, middleName: String): String =
    listOf(lastName, firstName, middleName).filter { it.isNotBlank() }.joinToString(" ")

fun AppSettings.userFio(): String = formatUserFio(userLastName, userFirstName, userMiddleName)

fun treeGitCommitMessage(authorFio: String, peopleCount: Int): String {
    val body = "Древо: $peopleCount чел."
    return if (authorFio.isBlank()) body else "$authorFio: $body"
}
