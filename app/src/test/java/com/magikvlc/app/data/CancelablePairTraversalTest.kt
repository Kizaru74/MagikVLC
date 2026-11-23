package com.magikvlc.app.data

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CancelablePairTraversalTest {
    @Test
    fun checksCancellationBetweenPairComparisons() {
        var checks = 0
        var pairsVisited = 0

        assertFailsWith<CancellationException> {
            forEachUnorderedPair(
                itemCount = 20,
                checkActive = {
                    checks++
                    if (checks == 5) throw CancellationException("cancelled")
                },
                onPair = { _, _ -> pairsVisited++ },
            )
        }

        assertEquals(3, pairsVisited)
    }
}
