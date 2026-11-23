package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DuplicateScanWorkPolicyTest {

    @Test
    fun reusesAnActiveScanWithTheSameMode() {
        assertEquals(
            DuplicateScanWorkPolicy.Decision.KEEP,
            DuplicateScanWorkPolicy.decision(active = true, requestedDeepScan = false, activeDeepScan = false),
        )
    }

    @Test
    fun replacesAnActiveScanWhenModeChanges() {
        assertEquals(
            DuplicateScanWorkPolicy.Decision.REPLACE,
            DuplicateScanWorkPolicy.decision(active = true, requestedDeepScan = true, activeDeepScan = false),
        )
    }

    @Test
    fun enqueuesWhenNoScanIsActive() {
        assertEquals(
            DuplicateScanWorkPolicy.Decision.ENQUEUE,
            DuplicateScanWorkPolicy.decision(active = false, requestedDeepScan = true, activeDeepScan = null),
        )
    }
}
