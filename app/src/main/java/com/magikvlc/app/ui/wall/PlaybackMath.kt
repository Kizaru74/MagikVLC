package com.magikvlc.app.ui.wall

/** Límite usado por los saltos de doble toque; no reserva el último segundo. */
internal fun jumpTarget(positionMs: Long, durationMs: Long, deltaMs: Long): Long {
    val base = positionMs.coerceAtLeast(0L)
    return if (durationMs > 0L) {
        (base + deltaMs).coerceIn(0L, durationMs)
    } else {
        (base + deltaMs).coerceAtLeast(0L)
    }
}
