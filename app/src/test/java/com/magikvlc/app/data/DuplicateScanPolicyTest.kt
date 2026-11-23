package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateScanPolicyTest {

    @Test
    fun cannotStartBeforeLibraryLoadCompletes() {
        assertFalse(DuplicateScanPolicy.canStart(loading = true, hasPermission = true, videoCount = 10))
    }

    @Test
    fun cannotStartWithoutVideoPermission() {
        assertFalse(DuplicateScanPolicy.canStart(loading = false, hasPermission = false, videoCount = 10))
    }

    @Test
    fun cannotStartWithEmptyLibrary() {
        assertFalse(DuplicateScanPolicy.canStart(loading = false, hasPermission = true, videoCount = 0))
    }

    @Test
    fun startsWhenLibraryIsLoadedAndNonempty() {
        assertTrue(DuplicateScanPolicy.canStart(loading = false, hasPermission = true, videoCount = 10))
    }

    @Test
    fun replacesOnlyWhenActiveModeDiffers() {
        assertFalse(DuplicateScanPolicy.shouldReplace(active = false, requestedDeepScan = true, activeDeepScan = false))
        assertFalse(DuplicateScanPolicy.shouldReplace(active = true, requestedDeepScan = false, activeDeepScan = false))
        assertTrue(DuplicateScanPolicy.shouldReplace(active = true, requestedDeepScan = true, activeDeepScan = false))
    }
}
