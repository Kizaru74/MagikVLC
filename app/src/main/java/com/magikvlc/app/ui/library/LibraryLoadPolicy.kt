package com.magikvlc.app.ui.library

internal object LibraryLoadPolicy {
    enum class LoadMode { CACHE, SCAN }

    /** Mínimo entre escaneos reales para volver a refrescar en fondo con la caché. */
    const val BACKGROUND_REFRESH_INTERVAL_MS = 30_000L

    fun mode(hasCachedData: Boolean, forceRefresh: Boolean): LoadMode =
        if (forceRefresh || !hasCachedData) LoadMode.SCAN else LoadMode.CACHE

    fun shouldRelink(scanGeneration: Int, lastRelinkGeneration: Int): Boolean =
        scanGeneration > 0 && scanGeneration > lastRelinkGeneration

    fun shouldRefreshInBackground(
        msSinceLastScan: Long,
        minIntervalMs: Long = BACKGROUND_REFRESH_INTERVAL_MS,
    ): Boolean = msSinceLastScan >= minIntervalMs
}
