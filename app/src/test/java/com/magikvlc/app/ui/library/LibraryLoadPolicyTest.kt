package com.magikvlc.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryLoadPolicyTest {

    @Test
    fun cachedLibrarySkipsFullScanUnlessRefreshWasRequested() {
        assertEquals(
            LibraryLoadPolicy.LoadMode.CACHE,
            LibraryLoadPolicy.mode(hasCachedData = true, forceRefresh = false),
        )
        assertEquals(
            LibraryLoadPolicy.LoadMode.SCAN,
            LibraryLoadPolicy.mode(hasCachedData = true, forceRefresh = true),
        )
        assertEquals(
            LibraryLoadPolicy.LoadMode.SCAN,
            LibraryLoadPolicy.mode(hasCachedData = false, forceRefresh = false),
        )
    }

    @Test
    fun relinkRunsOnlyForANewRealScan() {
        assertFalse(LibraryLoadPolicy.shouldRelink(scanGeneration = 0, lastRelinkGeneration = 0))
        assertTrue(LibraryLoadPolicy.shouldRelink(scanGeneration = 1, lastRelinkGeneration = 0))
        assertFalse(LibraryLoadPolicy.shouldRelink(scanGeneration = 1, lastRelinkGeneration = 1))
    }

    @Test
    fun backgroundRefreshWaitsForTheMinimumInterval() {
        assertFalse(LibraryLoadPolicy.shouldRefreshInBackground(msSinceLastScan = 0L))
        assertFalse(LibraryLoadPolicy.shouldRefreshInBackground(msSinceLastScan = 29_999L))
        assertTrue(LibraryLoadPolicy.shouldRefreshInBackground(msSinceLastScan = 30_000L))
        assertTrue(LibraryLoadPolicy.shouldRefreshInBackground(msSinceLastScan = 61_000L))
    }

    @Test
    fun backgroundRefreshIntervalCanBeOverridden() {
        assertFalse(LibraryLoadPolicy.shouldRefreshInBackground(msSinceLastScan = 9_999L, minIntervalMs = 10_000L))
        assertTrue(LibraryLoadPolicy.shouldRefreshInBackground(msSinceLastScan = 10_000L, minIntervalMs = 10_000L))
    }
}
