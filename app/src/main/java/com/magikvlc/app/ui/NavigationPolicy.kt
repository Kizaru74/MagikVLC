package com.magikvlc.app.ui

internal object NavigationPolicy {
    enum class LibraryExit { POP_BACK_STACK }

    fun libraryExit(currentRoute: String?): LibraryExit = LibraryExit.POP_BACK_STACK
}
