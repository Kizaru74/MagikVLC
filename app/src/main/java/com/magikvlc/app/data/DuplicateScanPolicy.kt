package com.magikvlc.app.data

object DuplicateScanPolicy {
    fun canStart(loading: Boolean, hasPermission: Boolean, videoCount: Int): Boolean =
        !loading && hasPermission && videoCount > 0

    fun shouldReplace(active: Boolean, requestedDeepScan: Boolean, activeDeepScan: Boolean): Boolean =
        active && requestedDeepScan != activeDeepScan
}
