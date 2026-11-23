package com.magikvlc.app

import android.app.Application
import org.videolan.libvlc.LibVLC

class MagikVLCApp : Application() {

    val libVLC: LibVLC by lazy {
        // Sin --drop-late-frames/--skip-frames: descartar cuadros a nivel
        // global causaba caídas de fps visibles hasta en 720p30 por HW.
        // El bajo costo para tiles en software vive en MagikPlayer por media.
        val options = arrayListOf(
            "--avcodec-hw=any",
        )
        LibVLC(this, options)
    }
}