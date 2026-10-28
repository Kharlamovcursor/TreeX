package ru.hdlabs.treex.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SnapshotNames {
    private val fileStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
    private val modern = Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})(?:-(\d{3}))?\.json""")
    private val legacy = Regex("""(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})-(\d{3})\.json""")

    fun fileName(instant: Instant, zone: ZoneId): String =
        fileStamp.withZone(zone).format(instant) + ".json"

    fun isSafe(fileName: String): Boolean = modern.matches(fileName) || legacy.matches(fileName)

    fun label(fileName: String): String {
        modern.matchEntire(fileName)?.let { match ->
            val p = match.groupValues
            return "${p[1]}-${p[2]}-${p[3]} ${p[4]}:${p[5]}:${p[6]}"
        }
        legacy.matchEntire(fileName)?.let { match ->
            val p = match.groupValues
            return "${p[1]}-${p[2]}-${p[3]} ${p[4]}:${p[5]}:${p[6]}"
        }
        return fileName.removeSuffix(".json")
    }
}
