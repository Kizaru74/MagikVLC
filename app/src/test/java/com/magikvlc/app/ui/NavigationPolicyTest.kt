package com.magikvlc.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationPolicyTest {

    @Test
    fun leavingWallForLibraryUsesBackStackWhenLibraryIsTheStartDestination() {
        assertEquals(
            NavigationPolicy.LibraryExit.POP_BACK_STACK,
            NavigationPolicy.libraryExit(currentRoute = Routes.WALL),
        )
        assertEquals(
            NavigationPolicy.LibraryExit.POP_BACK_STACK,
            NavigationPolicy.libraryExit(currentRoute = Routes.LIBRARY),
        )
    }
}
