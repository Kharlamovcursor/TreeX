package ru.hdlabs.treex.diagnostic

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.first
import ru.hdlabs.treex.BuildConfig
import ru.hdlabs.treex.TreeXApplication
import kotlinx.coroutines.flow.first
import ru.hdlabs.treex.sync.FamilyGitConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DiagnosticReport {
    suspend fun buildText(context: Context): String {
        val app = context.applicationContext as TreeXApplication
        val settings = app.container.settingsRepository.settings.first()
        val state = app.container.treeRepository.observe().first()
        val living = state.people.count { !it.vacant }
        val now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val gitLabel = when {
            FamilyGitConfig.isEmbedded() -> "встроен (${FamilyGitConfig.embeddedOwner}/${FamilyGitConfig.embeddedRepo})"
            settings.gitConfigured -> "настроен (${settings.githubOwner}/${settings.githubRepo})"
            else -> "не настроен"
        }
        return buildString {
            appendLine("TreeX — отчёт для отладки")
            appendLine("Сформирован: $now")
            appendLine()
            appendLine("=== Приложение ===")
            appendLine("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Сборка: ${BuildConfig.BUILD_TYPE}")
            appendLine()
            appendLine("=== Устройство ===")
            appendLine("Модель: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("=== Древо (без персональных данных) ===")
            appendLine("Карточек: ${state.people.size}, с именами: $living")
            appendLine("Связей супругов: ${state.unions.size}")
            appendLine("Git: $gitLabel")
            appendLine()
            appendLine("=== Последние события ===")
            val events = AppEventLog.snapshot()
            if (events.isEmpty()) {
                appendLine("(пока нет записей — воспроизведите ошибку и отправьте отчёт снова)")
            } else {
                append(AppEventLog.formatEntries(events))
            }
            appendLine()
            appendLine("Токены, пароли и содержимое древа в этот файл не попадают.")
        }
    }
}
