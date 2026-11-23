package com.magikvlc.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryRefreshPolicyTest {

    @Test
    fun refreshCannotStartWhileAnotherRefreshIsRunning() {
        assertTrue(LibraryRefreshPolicy.canStartRefresh(refreshing = false))
        assertFalse(LibraryRefreshPolicy.canStartRefresh(refreshing = true))
    }

    @Test
    fun diffCountsAddedAndRemovedVideosByStableUri() {
        val diff = LibraryRefreshPolicy.diff(
            previous = setOf("video-a", "video-b"),
            current = setOf("video-b", "video-c", "video-d"),
        )

        assertEquals(2, diff.added)
        assertEquals(1, diff.removed)
    }
}
