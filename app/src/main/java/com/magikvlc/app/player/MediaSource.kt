package com.magikvlc.app.player

import android.net.Uri

sealed interface MediaSource {
    data class Local(val uri: Uri) : MediaSource
    data class Network(val url: String) : MediaSource

    val key: String
        get() = when (this) {
            is Local -> uri.toString()
            is Network -> url
        }
}