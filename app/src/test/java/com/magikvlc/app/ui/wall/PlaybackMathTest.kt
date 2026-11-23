package com.magikvlc.app.ui.wall

import com.magikvlc.app.data.VideosDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMathTest {
    @Test
    fun doubleTapJumpKeepsTenSecondJumpNearEnd() {
        assertEquals(110_000L, jumpTarget(120_000L, 120_000L, -10_000L))
    }

    @Test
    fun basicDuplicateNameNormalizationRemovesCopyAndQualityMarkers() {
        assertEquals(
            "vacaciones playa",
            VideosDataSource.normalizeName("VID_20240101_1234 - Vacaciones_Playa (1) 1080p - Copy.mp4"),
        )
    }
}
