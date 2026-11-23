package com.magikvlc.app.player

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.TextureView
import com.magikvlc.app.diagnostics.DiagnosticLog
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import kotlin.math.roundToInt

/**
 * Wrapper around one libVLC [MediaPlayer], tied to a single video tile.
 * A single shared [LibVLC] instance must be used app-wide.
 *
 * Playback flow (important): [attach] must happen before the first [play],
 * otherwise libVLC stalls waiting for a video output surface.
 */
class MagikPlayer(private val libVLC: LibVLC) {

    val debugId: Int = System.identityHashCode(this)

    private val mediaPlayer = MediaPlayer(libVLC)
    private var pfd: ParcelFileDescriptor? = null
    private var blastedVolume = -1
    private var attachedView: TextureView? = null

    /** true tras EndReached para reiniciar desde 0 en el siguiente play. */
    private var ended = false

    val isEnded: Boolean
        get() = ended

    @Volatile
    var playing: Boolean = false
        private set

    @Volatile
    var started: Boolean = false
        private set

    @Volatile
    var sourceKey: String? = null
        private set

    @Volatile
    var positionMs: Long = 0L
        private set

    @Volatile
    var durationMs: Long = 0L
        private set

    @Volatile
    var volume: Float = 1f
        private set

    @Volatile
    var muted: Boolean = false

    /** Frames pintados en la última ventana de 1 s (HUD de diagnóstico). */
    @Volatile
    var renderFps: Float = 0f
        private set

    private val frameTimes = ArrayDeque<Long>()

    var eventCallback: ((MediaPlayer.Event) -> Unit)? = null

    /** (videoW, videoH, sarNum, sarDen) cuando el vout conoce el layout real. */
    var layoutCallback: ((Int, Int, Int, Int) -> Unit)? = null

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                }
                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    ended = true
                }
                MediaPlayer.Event.TimeChanged -> positionMs = event.timeChanged
                MediaPlayer.Event.LengthChanged ->
                    if (event.lengthChanged > 0) durationMs = event.lengthChanged
            }
            eventCallback?.invoke(event)
        }
    }

    fun attach(textureView: TextureView) {
        val vout = mediaPlayer.vlcVout
        vout.setVideoView(textureView)
        if (textureView.width > 0 && textureView.height > 0) {
            vout.setWindowSize(textureView.width, textureView.height)
        }
        vout.attachViews(object : IVLCVout.OnNewVideoLayoutListener {
            override fun onNewVideoLayout(
                vout: IVLCVout,
                width: Int,
                height: Int,
                visibleWidth: Int,
                visibleHeight: Int,
                sarNum: Int,
                sarDen: Int,
            ) {
                layoutCallback?.invoke(visibleWidth, visibleHeight, sarNum, sarDen)
            }
        })
        attachedView = textureView
        // NOTA: no registrar OnFrameAvailableListener aquí: libVLC usa el suyo
        // interno para pintar y reemplazarlo deja el video en negro. El conteo
        // de frames para el HUD entra por trackFrame() desde
        // SurfaceTextureListener.onSurfaceTextureUpdated (no interfiere).
    }

    /** Un frame nuevo llegó a la superficie (vía onSurfaceTextureUpdated). */
    fun trackFrame() {
        val now = SystemClock.uptimeMillis()
        synchronized(frameTimes) {
            frameTimes.addLast(now)
            while (frameTimes.isNotEmpty() && now - frameTimes.first() > 1000L) {
                frameTimes.removeFirst()
            }
            renderFps = frameTimes.size.toFloat()
        }
    }

    private fun resetFrameStats() {
        synchronized(frameTimes) { frameTimes.clear() }
        renderFps = 0f
    }

    /** Re-sincroniza el tamaño nativo cuando la vista cambia (p. ej. al ocultar el chrome). */
    fun updateWindowSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (!mediaPlayer.vlcVout.areViewsAttached()) return
        runCatching { mediaPlayer.vlcVout.setWindowSize(width, height) }
    }

    /** FIT = BEST_FIT nativo; FILL y proporciones como en VLC.
     *  CROP usa BEST_FIT como base y la UI recorta por escala (sin deformar). */
    fun setVideoScaleMode(mode: ScaleMode) {
        runCatching {
            mediaPlayer.setVideoScale(
                when (mode) {
                    ScaleMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    ScaleMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
                    ScaleMode.CROP -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    ScaleMode.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
                    ScaleMode.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
                    ScaleMode.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
                },
            )
        }
    }

    /** preset null = plano; si no, índice de preset nativo de libVLC. */
    fun setEqualizerPreset(preset: Int?) {
        runCatching {
            val eq = if (preset == null) {
                MediaPlayer.Equalizer.create().also { flat ->
                    repeat(MediaPlayer.Equalizer.getBandCount()) { band ->
                        flat.setAmp(band, 0f)
                    }
                }
            } else {
                MediaPlayer.Equalizer.createFromPreset(preset)
            }
            mediaPlayer.setEqualizer(eq)
        }
    }

    fun videoTrackInfo(): IMedia.VideoTrack? =
        runCatching { mediaPlayer.currentVideoTrack }.getOrNull()

    fun detachViews() {
        if (!mediaPlayer.vlcVout.areViewsAttached()) return
        mediaPlayer.vlcVout.detachViews()
    }

    /** Desadjunta solo si el vout sigue atado a esta vista (evita race con re-attach). */
    fun detachIfCurrent(textureView: TextureView) {
        if (attachedView === textureView) {
            attachedView = null
            detachViews()
        }
    }

    fun isAttached(): Boolean =
        runCatching { mediaPlayer.vlcVout.areViewsAttached() }.getOrDefault(false)

    fun play(context: Context, source: MediaSource, hw: Boolean) {
        closePfd()
        val media: Media = when (source) {
            is MediaSource.Local -> {
                val fd = try {
                    context.contentResolver.openFileDescriptor(source.uri, "r")
                } catch (t: Throwable) {
                    DiagnosticLog.log(
                        "PLAYER",
                        "open failed player=$debugId source=${source.key} " +
                            "error=${t.javaClass.simpleName}:${t.message}",
                    )
                    null
                } ?: run {
                    DiagnosticLog.log(
                        "PLAYER",
                        "open returned null player=$debugId source=${source.key}",
                    )
                    throw IllegalStateException("No se pudo abrir el video")
                }
                pfd = fd
                DiagnosticLog.log("PLAYER", "open success player=$debugId source=${source.key}")
                Media(libVLC, fd.fileDescriptor)
            }
            is MediaSource.Network -> Media(libVLC, source.url).also {
                it.addOption(":network-caching=300")
            }
        }
        media.setHWDecoderEnabled(hw, false)
        if (!hw) {
            media.addOption(":avcodec-fast")
            media.addOption(":avcodec-skiploopfilter=4")
        }
        mediaPlayer.media = media
        media.release()
        sourceKey = source.key
        ended = false
        resetFrameStats()
        started = true
        mediaPlayer.play()
    }

    fun play() {
        if (ended) {
            ended = false
            runCatching { mediaPlayer.setTime(0L) }
        }
        mediaPlayer.play()
    }

    fun pause() {
        mediaPlayer.pause()
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    fun stop() {
        mediaPlayer.stop()
    }

    fun seekTo(ms: Long) {
        if (started && ms >= 0) mediaPlayer.setTime(ms)
    }

    fun setSpeed(value: Float) {
        volumeCalm()
        if (started) {
            try {
                mediaPlayer.rate = value
            } catch (_: Throwable) {
            }
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 2f)
        volumeCalm()
    }

    fun setMute(value: Boolean) {
        muted = value
        volumeCalm()
    }

    private fun volumeCalm() {
        val level = if (muted) 0 else (volume * 100).roundToInt()
        if (level != blastedVolume) {
            mediaPlayer.setVolume(level)
            blastedVolume = level
        }
    }

    fun release() {
        stop()
        detachViews()
        mediaPlayer.setEventListener(null)
        mediaPlayer.release()
        closePfd()
        started = false
        playing = false
        sourceKey = null
        positionMs = 0L
        durationMs = 0L
        resetFrameStats()
    }

    private fun closePfd() {
        runCatching { pfd?.close() }
        pfd = null
    }
}
