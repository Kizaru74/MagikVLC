package com.magikvlc.app.player

enum class TileStatus { EMPTY, LOADING, PLAYING, PAUSED, ERROR }

enum class ScaleMode { FIT, FILL, CROP, RATIO_16_9, RATIO_4_3, ORIGINAL }

data class Tile(
    val index: Int,
    val source: MediaSource? = null,
    val title: String? = null,
    val status: TileStatus = TileStatus.EMPTY,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val loop: Boolean = false,
    val muted: Boolean = false,
    val volume: Float = 1f,
    val scaleMode: ScaleMode = ScaleMode.FILL,
    val rotationDegrees: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val error: String? = null,
    // Layout real del vídeo (vía OnNewVideoLayout) para stats y diagnóstico.
    val videoW: Int = 0,
    val videoH: Int = 0,
    val videoCodec: String? = null,
    val videoFps: Float? = null,
    val videoBitrateKbps: Int = 0,
    val hwUsed: Boolean = true,
    val showStats: Boolean = false,
)
