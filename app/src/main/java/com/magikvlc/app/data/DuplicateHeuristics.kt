package com.magikvlc.app.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object DuplicateHeuristics {
    data class Candidate(
        val identity: String,
        val name: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
    )

    data class Match(val score: Int, val reason: String)

    fun compare(a: VideoItem, b: VideoItem): Match? = compare(
        a = Candidate(a.uri.toString(), a.name, a.durationMs, a.sizeBytes, a.width, a.height),
        b = Candidate(b.uri.toString(), b.name, b.durationMs, b.sizeBytes, b.width, b.height),
    )

    fun compare(a: Candidate, b: Candidate): Match? {
        if (a.identity == b.identity) return null

        var score = 0
        val reasons = mutableListOf<String>()
        val nameScore = nameScore(a.name, b.name)
        if (nameScore > 0) {
            score += nameScore
            reasons += if (nameScore >= 45) "nombre normalizado" else "tokens de nombre"
        }

        if (durationClose(a.durationMs, b.durationMs)) {
            score += 30
            reasons += "duración compatible"
        }
        if (a.width > 0 && a.height > 0 && a.width == b.width && a.height == b.height) {
            score += 15
            reasons += "misma resolución"
        } else if (aspectRatioClose(a, b)) {
            score += 8
            reasons += "relación de aspecto compatible"
        }
        if (sizeRatioClose(a.sizeBytes, b.sizeBytes)) {
            score += 5
            reasons += "tamaño cercano"
        }

        return Match(
            score = score,
            reason = reasons.joinToString(", "),
        ).takeIf { it.score >= 60 }
    }

    private fun nameScore(a: String, b: String): Int {
        val left = VideosDataSource.normalizeName(a)
        val right = VideosDataSource.normalizeName(b)
        if (left.isBlank() || right.isBlank()) return 0
        if (left == right) return 45
        val leftTokens = left.split(' ').filter { it.length >= 2 }.toSet()
        val rightTokens = right.split(' ').filter { it.length >= 2 }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0
        val union = leftTokens union rightTokens
        val overlap = leftTokens intersect rightTokens
        return if (overlap.size.toFloat() / union.size >= 0.6f) 30 else 0
    }

    private fun durationClose(a: Long, b: Long): Boolean {
        if (a <= 0L || b <= 0L) return false
        val tolerance = max(2_000L, max(a, b) / 200L)
        return abs(a - b) <= tolerance
    }

    private fun aspectRatioClose(a: Candidate, b: Candidate): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val left = a.width.toFloat() / a.height
        val right = b.width.toFloat() / b.height
        return abs(left - right) <= 0.03f
    }

    private fun sizeRatioClose(a: Long, b: Long): Boolean {
        if (a <= 0L || b <= 0L) return false
        val smaller = min(a, b).toDouble()
        val larger = max(a, b).toDouble()
        return smaller / larger >= 0.85
    }
}
