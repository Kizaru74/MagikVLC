package com.magikvlc.app.ui.library

internal object LibraryRefreshPolicy {
    data class Diff(val added: Int, val removed: Int)

    fun canStartRefresh(refreshing: Boolean): Boolean = !refreshing

    fun diff(previous: Set<String>, current: Set<String>): Diff = Diff(
        added = (current - previous).size,
        removed = (previous - current).size,
    )
}
