package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DuplicateHeuristicsTest {

    @Test
    fun reencodedVideoWithSameNameAndDurationGetsHighConfidence() {
        val match = DuplicateHeuristics.compare(
            candidate("Movie 1080p.mp4", durationMs = 600_000L, width = 1920, height = 1080),
            candidate("Movie copy 1080p.mkv", durationMs = 601_200L, width = 1920, height = 1080),
        )

        assertNotNull(match)
        assertTrue(match.score >= 70)
    }

    @Test
    fun sameDurationAndResolutionWithDifferentNamesIsNotAcceptedAsProbable() {
        val match = DuplicateHeuristics.compare(
            candidate("concert-a.mp4", durationMs = 600_000L, width = 1920, height = 1080),
            candidate("vacaciones-b.mp4", durationMs = 600_500L, width = 1920, height = 1080),
        )

        assertTrue(match == null || match.score < 60)
    }

    @Test
    fun releaseTagsAndQualityDoNotHideTheProbableMatch() {
        val match = DuplicateHeuristics.compare(
            candidate(
                "The.Matrix.1999.1080p.WEB-DL.x264.mp4",
                durationMs = 8_160_000L,
                width = 1920,
                height = 1080,
            ),
            candidate(
                "The Matrix 1999 720p BluRay x265.mkv",
                durationMs = 8_161_500L,
                width = 1280,
                height = 720,
            ),
        )

        assertNotNull(match)
        assertTrue(match.score >= 70)
    }

    private fun candidate(name: String, durationMs: Long, width: Int, height: Int) =
        DuplicateHeuristics.Candidate(
            identity = name.hashCode().toString(),
            name = name,
            durationMs = durationMs,
            sizeBytes = 1_000_000L,
            width = width,
            height = height,
        )
}
