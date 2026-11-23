package com.magikvlc.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryPerformancePolicyTest {

    @Test
    fun thumbnailsAreAllowedOnlyOnVideoAndFavoriteTabs() {
        assertTrue(LibraryPerformancePolicy.shouldWarm(tab = 0, granted = true))
        assertTrue(LibraryPerformancePolicy.shouldWarm(tab = 1, granted = true))
        assertFalse(LibraryPerformancePolicy.shouldWarm(tab = 2, granted = true))
        assertFalse(LibraryPerformancePolicy.shouldWarm(tab = 3, granted = true))
    }

    @Test
    fun thumbnailsStayDisabledWithoutPermission() {
        assertFalse(LibraryPerformancePolicy.shouldWarm(tab = 0, granted = false))
    }

    @Test
    fun warmingUsesOnlyTheVisibleTabItems() {
        val visible = listOf("favorite-a", "favorite-b")

        assertEquals(
            visible,
            LibraryPerformancePolicy.warmItems(tab = 1, granted = true, visibleItems = visible),
        )
        assertTrue(
            LibraryPerformancePolicy.warmItems(
                tab = 2,
                granted = true,
                visibleItems = visible,
            ).isEmpty()
        )
    }
}
