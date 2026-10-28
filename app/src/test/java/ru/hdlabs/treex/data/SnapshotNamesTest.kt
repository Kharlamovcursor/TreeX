package ru.hdlabs.treex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SnapshotNamesTest {
    @Test
    fun fileNameShowsDateAndTimeWithSeparators() {
        val name = SnapshotNames.fileName(Instant.parse("2026-10-28T16:55:01.123Z"), ZoneOffset.UTC)
        assertEquals("2026-10-28_16-55-01-123.json", name)
        assertTrue(SnapshotNames.isSafe(name))
        assertEquals("2026-10-28 16:55:01", SnapshotNames.label(name))
        assertTrue(SnapshotNames.isSafe("20261028-165501-123.json"))
        assertEquals("2026-10-28 16:55:01", SnapshotNames.label("20261028-165501-123.json"))
    }
}
