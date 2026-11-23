package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionPolicyTest {

    @Test
    fun olderAndroidDoesNotRequestPermissionOrShowWarning() {
        assertFalse(NotificationPermissionPolicy.shouldRequest(apiLevel = 32, granted = false))
        assertFalse(NotificationPermissionPolicy.shouldWarn(apiLevel = 32, granted = false))
    }

    @Test
    fun grantedPermissionNeedsNoRequestOrWarning() {
        assertFalse(NotificationPermissionPolicy.shouldRequest(apiLevel = 33, granted = true))
        assertFalse(NotificationPermissionPolicy.shouldWarn(apiLevel = 33, granted = true))
    }

    @Test
    fun deniedPermissionStillAllowsScanAndShowsVisibilityWarning() {
        assertTrue(NotificationPermissionPolicy.shouldRequest(apiLevel = 33, granted = false))
        assertTrue(NotificationPermissionPolicy.shouldWarn(apiLevel = 33, granted = false))
        assertTrue(DuplicateScanPolicy.canStart(loading = false, hasPermission = true, videoCount = 1))
    }
}
