package com.magikvlc.app.data

object NotificationPermissionPolicy {
    private const val ANDROID_13_API = 33

    fun shouldRequest(apiLevel: Int, granted: Boolean): Boolean = apiLevel >= ANDROID_13_API && !granted

    fun shouldWarn(apiLevel: Int, granted: Boolean): Boolean = apiLevel >= ANDROID_13_API && !granted
}
