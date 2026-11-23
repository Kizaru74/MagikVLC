package com.magikvlc.app.ui.wall

internal object WallPerformancePolicy {
    enum class RouteAction { NONE, SUSPEND, RESUME }

    fun routeAction(previous: String?, current: String?): RouteAction = when {
        previous == "wall" && current != "wall" -> RouteAction.SUSPEND
        previous != "wall" && current == "wall" -> RouteAction.RESUME
        else -> RouteAction.NONE
    }

    fun shouldPublishPosition(
        index: Int,
        focusedIndex: Int,
        fullscreenIndex: Int?,
        deltaMs: Long,
    ): Boolean =
        deltaMs >= 500L && (index == focusedIndex || index == fullscreenIndex)
}
