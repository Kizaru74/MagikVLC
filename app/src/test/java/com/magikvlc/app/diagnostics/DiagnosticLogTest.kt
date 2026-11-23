package com.magikvlc.app.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticLogTest {

    @Test
    fun keepsOnlyTheNewestEntriesAndIncludesComponent() {
        DiagnosticLog.clear()
        repeat(DiagnosticLog.MAX_ENTRIES + 5) { index ->
            DiagnosticLog.log("TEST", "event-$index")
        }

        val entries = DiagnosticLog.snapshot()
        assertEquals(DiagnosticLog.MAX_ENTRIES, entries.size)
        assertTrue(entries.first().contains("event-5"))
        assertTrue(entries.last().contains("[TEST] event-${DiagnosticLog.MAX_ENTRIES + 4}"))
    }
}
