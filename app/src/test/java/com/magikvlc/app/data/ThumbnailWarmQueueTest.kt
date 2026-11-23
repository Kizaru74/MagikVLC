package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThumbnailWarmQueueTest {

    @Test
    fun prioritySelectionIsLimitedTo150Videos() {
        val videos = (0 until 200).map { "video-$it" }

        val selected = ThumbnailWarmQueue.select(videos)

        assertEquals(150, selected.size)
        assertEquals(videos.take(150), selected)
    }

    @Test
    fun completedCountAllowsResumeWithoutRepeatingPreparedVideos() {
        val videos = listOf("a", "b", "c", "d")
        val completed = setOf(
            "a",
            "c",
        )

        assertEquals(2, ThumbnailWarmQueue.completedCount(videos, completed))
    }

    @Test
    fun staleWorkerCannotPublishAfterItsRunIsInvalidated() {
        assertFalse(ThumbnailWarmQueue.canPublish(workerRun = 4L, activeRun = 5L, paused = false))
        assertFalse(ThumbnailWarmQueue.canPublish(workerRun = 5L, activeRun = 5L, paused = true))
        assertTrue(ThumbnailWarmQueue.canPublish(workerRun = 5L, activeRun = 5L, paused = false))
    }

    @Test
    fun failedWarmIsNotCountedAsCompleted() {
        assertFalse(ThumbnailWarmQueue.shouldMarkCompleted(warmed = false))
        assertTrue(ThumbnailWarmQueue.shouldMarkCompleted(warmed = true))
    }

    @Test
    fun equalTargetsAreRecognizedEvenWhenTheListInstanceChanges() {
        assertTrue(
            ThumbnailWarmQueue.sameTarget(
                previousKeys = listOf("a#1", "b#2"),
                nextKeys = listOf("a#1", "b#2"),
            )
        )
        assertFalse(
            ThumbnailWarmQueue.sameTarget(
                previousKeys = listOf("a#1", "b#2"),
                nextKeys = listOf("b#2", "a#1"),
            )
        )
    }
}
