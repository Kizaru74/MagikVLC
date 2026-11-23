package com.magikvlc.app.data

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DuplicateScanReportStoreTest {

    @Test
    fun roundTripsExactAndProbableGroupsIncludingUriVolumeIds() = runBlocking {
        val root = Files.createTempDirectory("duplicate-report-store-").toFile()
        try {
            val store = DuplicateScanReportStore(root)
            val report = StoredDuplicateScanReport(
                scannedCount = 3,
                candidateRuns = 2,
                unreadable = 1,
                error = null,
                exactGroups = listOf(
                    StoredDuplicateGroup(
                        items = listOf(
                            sampleVideo("content://media/external_primary/video/media/42", "a.mp4"),
                            sampleVideo("content://media/8F3A-12BC/video/media/43", "b.mp4"),
                        ),
                        freedBytes = 400L,
                    ),
                ),
                probableGroups = listOf(
                    StoredProbableGroup(
                        items = listOf(
                            sampleVideo("content://media/external/video/media/44", "c.mp4"),
                            sampleVideo("content://media/8F3A-12BC/video/media/45", "d.mp4"),
                        ),
                        reason = "nombre y resolución",
                    ),
                ),
            )

            store.save("work-1", report)

            assertEquals(report, store.load("work-1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun isolatesReportsByWorkIdAndSupportsEmptyErrorReports() = runBlocking {
        val root = Files.createTempDirectory("duplicate-report-store-").toFile()
        try {
            val store = DuplicateScanReportStore(root)
            val empty = StoredDuplicateScanReport(
                scannedCount = 0,
                candidateRuns = 0,
                unreadable = 0,
                error = "snapshot vacío",
                exactGroups = emptyList(),
                probableGroups = emptyList(),
            )

            store.save("work/one", empty)

            assertEquals(empty, store.load("work/one"))
            assertNull(store.load("work-two"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sampleVideo(uri: String, name: String) = StoredVideoItem(
        id = 42L,
        uri = uri,
        name = name,
        durationMs = 12_000L,
        sizeBytes = 200L,
        relativePath = "Movies/",
        mimeType = "video/mp4",
        width = 1080,
        height = 1920,
        storage = "SD",
        volumeName = "8F3A-12BC",
        modifiedSec = 1_700_000_000L,
    )
}
