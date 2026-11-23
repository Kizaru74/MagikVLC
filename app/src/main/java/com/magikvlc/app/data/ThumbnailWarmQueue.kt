package com.magikvlc.app.data

/** Selección determinista de miniaturas prioritarias para bibliotecas grandes. */
internal object ThumbnailWarmQueue {
    const val MAX_PRIORITY_ITEMS = 150

    fun <T> select(videos: List<T>): List<T> =
        videos.take(MAX_PRIORITY_ITEMS)

    fun sameTarget(previousKeys: List<String>, nextKeys: List<String>): Boolean =
        previousKeys == nextKeys

    fun key(video: VideoItem): String = "${video.uri}#${video.id}"

    fun completedCount(keys: Iterable<String>, completedKeys: Set<String>): Int =
        keys.count { it in completedKeys }

    fun canPublish(workerRun: Long, activeRun: Long, paused: Boolean): Boolean =
        !paused && workerRun == activeRun

    fun shouldMarkCompleted(warmed: Boolean): Boolean = warmed
}
