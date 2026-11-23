package com.magikvlc.app.data

import android.provider.MediaStore

enum class VideoSort(val orderBy: String, val label: String) {
    NEWEST("${MediaStore.Video.Media.DATE_MODIFIED} DESC", "Nuevos"),
    OLDEST("${MediaStore.Video.Media.DATE_MODIFIED} ASC", "Antiguos"),
    NAME("${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC", "A–Z"),
    LONGEST("${MediaStore.Video.Media.DURATION} DESC", "Largos"),
    SHORTEST("${MediaStore.Video.Media.DURATION} ASC", "Cortos"),
    ;
}
