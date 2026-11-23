package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoriteMatchTest {

    private fun record(
        id: Long,
        name: String,
        durationMs: Long = 60_000L,
        sizeBytes: Long = 1000L,
    ): FavoriteRecord = FavoriteRecord(
        uri = "content://media/external_primary/video/media/$id",
        name = name,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        modifiedSec = null,
    )

    private fun favorite(
        uri: String,
        name: String,
        durationMs: Long = 60_000L,
        sizeBytes: Long? = 1000L,
    ): SavedFavorite = SavedFavorite(
        uri = uri,
        name = name,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
    )

    @Test
    fun keptFavoritesMatchByDirectUriWithoutMigration() {
        val current = record(1, "Pelicula.mp4")

        val result = matchFavoritesCore(
            videos = listOf(current),
            favorites = listOf(favorite(current.uri, "Pelicula.mp4")),
        )

        assertEquals(listOf(current.uri), result.matchedVideoUris)
        assertNull(result.migrated)
    }

    @Test
    fun movedFileIsRelinkedByFingerprintAndMigrated() {
        val current = record(7, "Pelicula 1080p WEB-DL.mp4")
        val stale = favorite(
            "content://media/external/video/media/999",
            "Pelicula.mp4",
        )

        val result = matchFavoritesCore(videos = listOf(current), favorites = listOf(stale))

        assertEquals(listOf(current.uri), result.matchedVideoUris)
        assertEquals(current.uri, result.migrated?.firstOrNull()?.uri)
    }

    @Test
    fun favoriteForAMissingVideoIsDropped() {
        val current = record(1, "Pelicula.mp4")

        val result = matchFavoritesCore(
            videos = listOf(current),
            favorites = listOf(
                favorite("content://media/external/video/media/999", "Ausente.mp4"),
            ),
        )

        assertTrue(result.matchedVideoUris.isEmpty())
    }

    @Test
    fun fingerprintRequiresDurationInsideTwoSeconds() {
        val current = record(2, "Pelicula.mp4", durationMs = 60_000L)
        val tooFar = favorite(
            "content://media/external/video/media/999",
            "Pelicula.mp4",
            durationMs = 65_000L,
        )

        assertTrue(matchFavoritesCore(listOf(current), listOf(tooFar)).matchedVideoUris.isEmpty())
    }

    @Test
    fun fingerprintRequiresSizeWhenItWasSaved() {
        val current = record(3, "Pelicula.mp4", sizeBytes = 2000L)
        val otherSize = favorite(
            "content://media/external/video/media/999",
            "Pelicula.mp4",
            sizeBytes = 1000L,
        )

        assertTrue(matchFavoritesCore(listOf(current), listOf(otherSize)).matchedVideoUris.isEmpty())
    }

    @Test
    fun manyCandidatesWithSameNameStillNeedExactSizeAndDuration() {
        val keeper = record(10, "Concierto.mp4", durationMs = 60_000L, sizeBytes = 5000L)
        val decoy = record(11, "Concierto.mp4", durationMs = 60_000L, sizeBytes = 9999L)
        val stale = favorite(
            "content://media/external/video/media/999",
            "Concierto.mp4",
            durationMs = 60_000L,
            sizeBytes = 5000L,
        )

        val result = matchFavoritesCore(listOf(keeper, decoy), listOf(stale))

        assertEquals(listOf(keeper.uri), result.matchedVideoUris)
    }
}
