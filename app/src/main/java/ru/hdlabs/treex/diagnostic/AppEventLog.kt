package ru.hdlabs.treex.diagnostic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

data class LogEntry(
    val timeMillis: Long,
    val level: String,
    val tag: String,
    val message: String,
)

object AppEventLog {
    private const val MAX_ENTRIES = 200
    private val buffer = ArrayDeque<LogEntry>()
    private val lock = Any()
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun info(tag: String, message: String) = append("I", tag, message)

    fun warn(tag: String, message: String) = append("W", tag, message)

    fun error(tag: String, message: String, cause: Throwable? = null) {
        val detail = if (cause == null) {
            message
        } else {
            val nested = cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
            "$message ($nested)"
        }
        append("E", tag, detail)
    }

    fun snapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    private fun append(level: String, tag: String, message: String) {
        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(LogEntry(System.currentTimeMillis(), level, tag, message.trim()))
        }
    }

    fun formatEntries(entries: List<LogEntry>): String =
        entries.joinToString("\n") { entry ->
            "${timeFormat.format(Instant.ofEpochMilli(entry.timeMillis))} ${entry.level}/${entry.tag}: ${entry.message}"
        }
}
