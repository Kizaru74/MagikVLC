package com.magikvlc.app.data

object DuplicateScanWorkPolicy {
    enum class Decision { ENQUEUE, KEEP, REPLACE }

    fun decision(active: Boolean, requestedDeepScan: Boolean, activeDeepScan: Boolean?): Decision = when {
        !active -> Decision.ENQUEUE
        activeDeepScan == requestedDeepScan -> Decision.KEEP
        else -> Decision.REPLACE
    }
}
