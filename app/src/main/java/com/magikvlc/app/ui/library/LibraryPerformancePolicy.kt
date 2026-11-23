package com.magikvlc.app.ui.library

internal object LibraryPerformancePolicy {
    fun shouldWarm(tab: Int, granted: Boolean): Boolean =
        granted && tab in 0..1

    fun <T> warmItems(tab: Int, granted: Boolean, visibleItems: List<T>): List<T> =
        if (shouldWarm(tab, granted)) visibleItems else emptyList()
}
