package com.magikvlc.app.ui.wall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WallPerformancePolicyTest {

    @Test
    fun routeChangesSuspendLeavingWallAndResumeReturningToWall() {
        assertEquals(
            WallPerformancePolicy.RouteAction.SUSPEND,
            WallPerformancePolicy.routeAction(previous = "wall", current = "library"),
        )
        assertEquals(
            WallPerformancePolicy.RouteAction.RESUME,
            WallPerformancePolicy.routeAction(previous = "library", current = "wall"),
        )
        assertEquals(
            WallPerformancePolicy.RouteAction.NONE,
            WallPerformancePolicy.routeAction(previous = "library", current = "about"),
        )
    }

    @Test
    fun structuralProgressOnlyUpdatesFocusedOrFullscreenTile() {
        assertFalse(
            WallPerformancePolicy.shouldPublishPosition(
                index = 2,
                focusedIndex = 0,
                fullscreenIndex = null,
                deltaMs = 1_000L,
            ),
        )
        assertTrue(
            WallPerformancePolicy.shouldPublishPosition(
                index = 0,
                focusedIndex = 0,
                fullscreenIndex = null,
                deltaMs = 500L,
            ),
        )
        assertTrue(
            WallPerformancePolicy.shouldPublishPosition(
                index = 2,
                focusedIndex = 0,
                fullscreenIndex = 2,
                deltaMs = 500L,
            ),
        )
    }
}
