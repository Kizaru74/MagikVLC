package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateScanUiPolicyTest {
    @Test
    fun doesNotStartUntilWorkInfoHasBeenQueried() {
        assertFalse(
            DuplicateScanUiPolicy.shouldAutoStart(
                onDuplicatesTab = true,
                libraryReady = true,
                hasVideoPermission = true,
                videoCount = 5,
                workInfoLoaded = false,
                hasWorkRecord = false,
            ),
        )
    }

    @Test
    fun waitsForLoadedNonemptyLibraryAndNoWorkRecord() {
        assertFalse(DuplicateScanUiPolicy.shouldAutoStart(true, false, true, 5, true, false))
        assertFalse(DuplicateScanUiPolicy.shouldAutoStart(true, true, true, 0, true, false))
        assertFalse(DuplicateScanUiPolicy.shouldAutoStart(true, true, true, 5, true, true))
        assertTrue(DuplicateScanUiPolicy.shouldAutoStart(true, true, true, 5, true, false))
    }

    @Test
    fun explicitCancellationPreventsDelayedAutoStart() {
        assertFalse(
            DuplicateScanUiPolicy.shouldAutoStart(
                onDuplicatesTab = true,
                libraryReady = true,
                hasVideoPermission = true,
                videoCount = 5,
                workInfoLoaded = true,
                hasWorkRecord = false,
                userCancelled = true,
            ),
        )
    }

    @Test
    fun failedWorkWithoutOutputCannotLoadAnOlderReportByWorkId() {
        assertFalse(DuplicateScanUiPolicy.reportIdToLoad("FAILED", null) != null)
        assertFalse(DuplicateScanUiPolicy.reportIdToLoad("RUNNING", "old-report") != null)
        assertTrue(DuplicateScanUiPolicy.reportIdToLoad("SUCCEEDED", "confirmed-report") == "confirmed-report")
    }

    @Test
    fun emptyResultsWithErrorsAreNotPresentedAsSuccessfulNoDuplicates() {
        assertEquals("No se encontraron duplicados", DuplicateScanUiPolicy.noGroupsMessage(null))
        assertEquals("No se pudo completar el análisis", DuplicateScanUiPolicy.noGroupsMessage("falló"))
    }
}
