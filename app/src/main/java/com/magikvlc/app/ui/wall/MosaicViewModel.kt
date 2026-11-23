package com.magikvlc.app.ui.wall

import android.app.Application
import android.os.Trace
import android.provider.Settings
import android.util.Rational
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magikvlc.app.MagikVLCApp
import com.magikvlc.app.data.LibraryRepository
import com.magikvlc.app.data.SessionRepository
import com.magikvlc.app.data.SettingsRepository
import com.magikvlc.app.diagnostics.DiagnosticLog
import com.magikvlc.app.data.toPersisted
import com.magikvlc.app.data.toSource
import com.magikvlc.app.pip.PipController
import com.magikvlc.app.player.HwDecodePolicy
import com.magikvlc.app.player.MagikPlayer
import com.magikvlc.app.player.MediaSource
import com.magikvlc.app.player.ScaleMode
import com.magikvlc.app.player.Tile
import com.magikvlc.app.player.TileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.MediaPlayer.Event
import kotlin.math.abs

enum class AudioMode { SINGLE, MULTIPLE }

class MosaicViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionRepository(app)
    private val settings = SettingsRepository(app)
    private val library = LibraryRepository(app)
    private val libVLC = (app as MagikVLCApp).libVLC
    private val players = mutableMapOf<Int, MagikPlayer>()
    /** Players que estaban reproduciendo cuando el muro dejó de estar visible. */
    private val suspendedPlaying = mutableSetOf<Int>()
    private var wallPlaybackSuspended = false
    private var wallSuspendJob: Job? = null

    val hwLimit: Int = HwDecodePolicy.maxSupportedInstances()

    private val _slots = MutableStateFlow(DEFAULT_SLOT_COUNT)
    val slots: StateFlow<Int> = _slots.asStateFlow()

    private val _tiles = MutableStateFlow(buildTiles(DEFAULT_SLOT_COUNT))
    val tiles: StateFlow<List<Tile>> = _tiles.asStateFlow()

    private val _fullscreenIndex = MutableStateFlow<Int?>(null)
    val fullscreenIndex: StateFlow<Int?> = _fullscreenIndex.asStateFlow()

    private val _pendingSwap = MutableStateFlow<Int?>(null)
    val pendingSwap: StateFlow<Int?> = _pendingSwap.asStateFlow()

    /** Slot que espera un vídeo nuevo desde la biblioteca (botón Cambiar). */
    private val _pendingReplaceSlot = MutableStateFlow<Int?>(null)
    val pendingReplaceSlot: StateFlow<Int?> = _pendingReplaceSlot.asStateFlow()

    private val _synced = MutableStateFlow(false)
    val synced: StateFlow<Boolean> = _synced.asStateFlow()

    private val _hwWarning = MutableStateFlow(false)
    val hwWarning: StateFlow<Boolean> = _hwWarning.asStateFlow()

    private val _focusedIndex = MutableStateFlow(0)
    val focusedIndex: StateFlow<Int> = _focusedIndex.asStateFlow()

    private val _audioMode = MutableStateFlow(AudioMode.SINGLE)
    val audioMode: StateFlow<AudioMode> = _audioMode.asStateFlow()

    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    // ---------- Picture-in-Picture ----------

    private val _pipMode = MutableStateFlow(false)
    val pipMode: StateFlow<Boolean> = _pipMode.asStateFlow()

    /** Ruta de navegación actual (la alimenta AppNav). */
    private val _currentRoute = MutableStateFlow<String?>(null)
    val currentRoute: StateFlow<String?> = _currentRoute.asStateFlow()

    private val _autoPip = MutableStateFlow(false)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    /** Brillo de pantalla (0.05..1) o null = brillo del sistema. Lo aplica MosaicScreen. */
    private val _brightness = MutableStateFlow<Float?>(null)
    val brightness: StateFlow<Float?> = _brightness.asStateFlow()
    private var brightnessInit = false

    // Última vista conocida por tile (para retry y rearranques).
    private val attachedViews = mutableMapOf<Int, TextureView>()
    // Arranques escalonados: 6 players a la vez saturan decodificadores/RAM.
    private val startQueue = ArrayDeque<Int>()
    private var starterActive = false
    // Trabajo diferido para no saturar el arranque con 9 vídeos.
    private val pendingSeek = mutableMapOf<Int, Long>()
    private val pendingEq = mutableMapOf<Int, Int?>()
    // Último seek en vivo por tile (scrub estilo VLC, throttled).
    private val lastLiveSeek = mutableMapOf<Int, Long>()
    // Último objetivo del scrub en curso (el commit lo fija exacto al soltar).
    private val lastScrubTarget = mutableMapOf<Int, Long>()
    // Evita escribir en DataStore en cada TimeChanged; solo al cruzar otro 10%.
    private val lastSavedProgressBucket = mutableMapOf<Int, Int>()
    /** Reproducción temporal iniciada con un único video desde Biblioteca. */
    private val _singlePlayback = MutableStateFlow(false)
    val singlePlayback: StateFlow<Boolean> = _singlePlayback.asStateFlow()

    init {
        DiagnosticLog.log("PLAYER", "hwLimit=$hwLimit")
        viewModelScope.launch {
            restore()
            while (true) {
                delay(10_000)
                saveCurrent()
            }
        }
        viewModelScope.launch {
            settings.autoPip.collect { _autoPip.value = it }
        }
        // Watchdog: si una carrera deja un tile en LOADING sin arrancar,
        // se re-encola para evitar la celda en blanco permanente.
        viewModelScope.launch {
            while (true) {
                delay(2000)
                if (startQueue.isEmpty() && !starterActive) {
                    val stale = _tiles.value.indices.firstOrNull { idx ->
                        val tile = _tiles.value[idx]
                        tile.source != null &&
                            tile.status == TileStatus.LOADING &&
                            players[idx]?.started != true &&
                            attachedViews[idx] != null
                    }
                    if (stale != null) enqueueStart(stale)
                }
            }
        }
    }

    // ---------- PiP helpers ----------

    fun setRoute(route: String?) {
        val previous = _currentRoute.value
        if (previous == route) return
        _currentRoute.value = route
        val action = WallPerformancePolicy.routeAction(previous, route)
        DiagnosticLog.log(
            "ROUTE",
            "setRoute previous=$previous current=$route action=${action.name} players=${players.size} tiles=${_tiles.value.size}",
        )
        Trace.beginSection("MagikVLC.route:${action.name}")
        try {
            when (action) {
                WallPerformancePolicy.RouteAction.SUSPEND -> suspendWallPlayback()
                WallPerformancePolicy.RouteAction.RESUME -> resumeWallPlayback()
                WallPerformancePolicy.RouteAction.NONE -> Unit
            }
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Pausa y desacopla LibVLC al salir del muro, pero conserva los players y sus
     * posiciones para reanudar sin reconstruir los videos al volver.
     */
    private fun suspendWallPlayback() {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        wallPlaybackSuspended = true
        suspendedPlaying.clear()
        startQueue.clear()
        val snapshot = players.toMap()
        snapshot.forEach { (index, player) ->
            val tile = _tiles.value.getOrNull(index)
            recordPlaybackProgress(
                index = index,
                positionMs = player.positionMs,
                durationMs = player.durationMs.takeIf { it > 0L } ?: tile?.durationMs ?: 0L,
                force = true,
            )
            if (player.playing) suspendedPlaying += index
        }
        attachedViews.clear()
        DiagnosticLog.log("WALL", "suspend scheduled players=${snapshot.size} playing=${suspendedPlaying.size}")
        wallSuspendJob?.cancel()
        wallSuspendJob = viewModelScope.launch(Dispatchers.IO) {
            val workerStartedAt = android.os.SystemClock.elapsedRealtime()
            snapshot.values.forEach { player ->
                runCatching { player.pause() }
                runCatching { player.detachViews() }
            }
            DiagnosticLog.log(
                "WALL",
                "suspend finished players=${snapshot.size} elapsed=${android.os.SystemClock.elapsedRealtime() - workerStartedAt}ms totalSinceRoute=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
            )
        }
    }

    private fun resumeWallPlayback() {
        val suspension = wallSuspendJob
        if (suspension?.isActive == true) {
            DiagnosticLog.log("WALL", "resume waiting for suspend job")
            viewModelScope.launch {
                suspension.join()
                if (_currentRoute.value == "wall") finishWallResume()
            }
        } else {
            finishWallResume()
        }
    }

    private fun finishWallResume() {
        wallSuspendJob = null
        wallPlaybackSuspended = false
        DiagnosticLog.log("WALL", "resume attachedViews=${attachedViews.size} pendingPlaying=${suspendedPlaying.size}")
        attachedViews.toMap().forEach { (index, textureView) ->
            attach(index, textureView)
        }
    }

    fun setPipMode(inPip: Boolean) {
        _pipMode.value = inPip
    }

    fun setAutoPip(enabled: Boolean) {
        _autoPip.value = enabled
        viewModelScope.launch { settings.setAutoPip(enabled) }
    }

    fun canEnterPip(): Boolean = _tiles.value.any { it.source != null }

    fun focusedTilePlaying(): Boolean =
        _tiles.value.getOrNull(_focusedIndex.value)?.status == TileStatus.PLAYING

    /** Aspecto de la ventana PiP: vídeo del tile visible si se conoce, si no 16:9. */
    fun pipAspectRatio(): Rational? {
        val idx = _fullscreenIndex.value ?: _focusedIndex.value
        val tile = _tiles.value.getOrNull(idx) ?: return null
        if (tile.videoW > 0 && tile.videoH > 0) {
            val ratio = tile.videoW.toFloat() / tile.videoH
            if (ratio in 0.5f..2.39f) return Rational(tile.videoW, tile.videoH)
        }
        return Rational(16, 9)
    }

    /** Acciones de la ventana PiP (botones flotantes). */
    fun pipAction(code: Int) {
        when (code) {
            PipController.ACTION_PLAY_PAUSE -> togglePlay(_focusedIndex.value)
            PipController.ACTION_REWIND -> seekBy(_focusedIndex.value, -10_000L)
            PipController.ACTION_FORWARD -> seekBy(_focusedIndex.value, 10_000L)
        }
    }

    // ---------- Layout / assign ----------

    fun setSlotCount(count: Int) {
        if (count !in MIN_SLOTS..MAX_SLOTS || count == _slots.value) return
        val previous = _slots.value
        _slots.value = count
        (count until previous).forEach {
            releasePlayer(it, reason = "slot-count")
            attachedViews.remove(it)
        }
        _tiles.update { old ->
            buildTiles(count).map { t -> old.getOrNull(t.index)?.takeIf { it.source != null } ?: t }
        }
        if (_focusedIndex.value >= count) _focusedIndex.value = 0
        updateHwWarning()
    }

    fun openInWall(items: List<Pair<MediaSource, String?>>) {
        if (items.isEmpty()) return
        resetWall()
        val target = LAYOUT_OPTIONS.firstOrNull { it >= items.size } ?: MAX_SLOTS
        setSlotCount(target)
        _tiles.update { list ->
            list.toMutableList().apply {
                items.forEachIndexed { i, (source, title) ->
                    if (i in indices) this[i] = this[i].copy(
                        source = source,
                        title = title ?: titleFor(source),
                        status = TileStatus.LOADING,
                        muted = _audioMode.value == AudioMode.MULTIPLE,
                        rotationDegrees = 0f,
                        zoom = 1f,
                        panX = 0f,
                        panY = 0f,
                        positionMs = 0L,
                        durationMs = 0L,
                    )
                }
            }
        }
_focusedIndex.value = 0
        updateHwWarning()
    }

    fun playSingle(item: Pair<MediaSource, String?>) {
        openInWall(listOf(item))
        _singlePlayback.value = true
        _fullscreenIndex.value = 0
    }

    /** Coloca la fuente en la primera celda libre (ampliando el muro si cabe).
     *  Devuelve false si el muro está lleno y no se pudo colocar. */
    fun add(source: MediaSource, title: String? = null): Boolean {
        _singlePlayback.value = false
        var placedIdx = -1
        _tiles.update { list ->
            val idx = list.indexOfFirst { it.source == null }
            if (idx < 0) {
                list
            } else {
                placedIdx = idx
                list.toMutableList().also {
                    it[idx] = it[idx].copy(
                        source = source,
                        title = title ?: titleFor(source),
                        status = TileStatus.LOADING,
                        muted = _audioMode.value == AudioMode.MULTIPLE,
                        rotationDegrees = 0f,
                        zoom = 1f,
                        panX = 0f,
                        panY = 0f,
                    )
                }
            }
        }
        if (placedIdx < 0 && _slots.value < MAX_SLOTS) {
            val next = LAYOUT_OPTIONS.firstOrNull { it > _slots.value } ?: MAX_SLOTS
            setSlotCount(next)
            _tiles.update { list ->
                val idx = list.indexOfFirst { s -> s.source == null }
                if (idx >= 0) placedIdx = idx
                list.toMutableList().also {
                    it[idx] = it[idx].copy(
                        source = source,
                        title = title ?: titleFor(source),
                        status = TileStatus.LOADING,
                        muted = _audioMode.value == AudioMode.MULTIPLE,
                    )
                }
            }
        }
        if (placedIdx >= 0) _focusedIndex.value = placedIdx
        return placedIdx >= 0
    }

    // ---------- Player surface lifecycle ----------

    fun attach(index: Int, textureView: TextureView) {
        attachedViews[index] = textureView
        if (wallPlaybackSuspended) return
        val tile = _tiles.value.getOrNull(index) ?: return
        if (tile.source == null) return
        DiagnosticLog.log(
            "PLAYER",
            "attach index=$index source=${tile.source.key} suspended=false " +
                "existing=${players[index]?.debugId ?: "none"}",
        )
        var player = players[index]
        if (player != null && player.sourceKey != tile.source.key) {
            releasePlayer(index, reason = "attach-source-mismatch")
            player = null
        }
        val p = player ?: getOrCreatePlayer(index)
        if (p.started) {
            // Re-attach a superficie nueva (fullscreen, resize). Un solo attach.
            try {
                p.attach(textureView)
                p.setVideoScaleMode(tile.scaleMode)
                if (suspendedPlaying.remove(index)) p.play()
            } catch (t: Throwable) {
                DiagnosticLog.log(
                    "PLAYER",
                    "attach failed index=$index source=${tile.source.key} player=${p.debugId} " +
                        "error=${t.javaClass.simpleName}:${t.message}",
                )
                markError(index, "No se pudo iniciar el video")
                return
            }
            applyAudioPolicy()
            return
        }
        // Player nuevo: NO adjuntar aquí; startPlayback() hace el único attach.
        enqueueStart(index)
    }

    private fun enqueueStart(index: Int) {
        startQueue.remove(index)
        if (isPriorityTile(index)) {
            startQueue.addFirst(index)
        } else {
            startQueue.addLast(index)
        }
        DiagnosticLog.log(
            "PLAYER",
            "queued index=$index source=${_tiles.value.getOrNull(index)?.source?.key ?: "none"} " +
                "queue=${startQueue.joinToString(",")}",
        )
        pumpStarter()
    }

    private fun isPriorityTile(index: Int): Boolean =
        index == _focusedIndex.value || index == _fullscreenIndex.value || _slots.value <= 4

    private fun starterDelayMs(): Long = when {
        _slots.value >= 9 -> 650L
        _slots.value >= 6 -> 500L
        else -> 350L
    }

    private fun pumpStarter() {
        if (starterActive) return
        val next = startQueue.removeFirstOrNull() ?: return
        starterActive = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DiagnosticLog.log(
                    "PLAYER",
                    "start attempt index=$next source=${_tiles.value.getOrNull(next)?.source?.key ?: "none"}",
                )
                startPlayback(next)
            } catch (t: Throwable) {
                DiagnosticLog.log(
                    "PLAYER",
                    "start failed index=$next source=${_tiles.value.getOrNull(next)?.source?.key ?: "none"} " +
                        "error=${t.javaClass.simpleName}:${t.message}",
                )
                markError(next, "No se pudo iniciar el video")
            }
            delay(starterDelayMs())
            starterActive = false
            pumpStarter()
        }
    }

    private fun startPlayback(index: Int) {
        if (wallPlaybackSuspended) return
        val tile = _tiles.value.getOrNull(index) ?: return
        val source = tile.source ?: return
        var view = attachedViews[index]
        if (view == null) {
            DiagnosticLog.log(
                "PLAYER",
                "surface missing index=$index source=${source.key}; retrying after 150ms",
            )
            // La superficie aún puede estar creándose: no abandonar el arranque,
            // reintentar en un momento y re-encolar si sigue sin vista.
            Thread.sleep(150)
            view = attachedViews[index]
            if (view == null) {
                if (_tiles.value.getOrNull(index)?.source != null &&
                    players[index]?.started != true
                ) {
                    enqueueStart(index)
                }
                return
            }
        }
        var player = players[index]
        if (player != null && player.sourceKey != null && player.sourceKey != source.key) {
            // La fuente cambió mientras esperaba en cola: empezar de cero, no colgarse.
            releasePlayer(index, reason = "start-source-mismatch")
            player = null
        }
        player = player ?: getOrCreatePlayer(index)
        if (wallPlaybackSuspended) return
        val hw = hwEnabledFor(index)
        DiagnosticLog.log(
            "PLAYER",
            "play attempt index=$index source=${source.key} player=${player.debugId} hw=$hw " +
                "view=${view.width}x${view.height}",
        )
        player.attach(view)
        player.setVideoScaleMode(tile.scaleMode)
        if (!player.started) {
            val priority = isPriorityTile(index)
            val nativeEq = if (_eqPreset.value == 0) null else _eqPreset.value - 1
            player.play(getApplication(), source, hw)
            DiagnosticLog.log(
                "PLAYER",
                "play requested index=$index source=${source.key} player=${player.debugId} hw=$hw",
            )
            player.setVolume(tile.volume)
            player.setSpeed(tile.speed)
            if (tile.positionMs > 500) {
                if (priority) {
                    player.seekTo(tile.positionMs)
                } else {
                    pendingSeek[index] = tile.positionMs
                }
            }
            if (nativeEq != null) {
                if (priority) {
                    player.setEqualizerPreset(nativeEq)
                } else {
                    pendingEq[index] = nativeEq
                }
            } else {
                pendingEq.remove(index)
            }
        }
        applyAudioPolicy()
    }

    /** Reintenta un tile en ERROR (tap sobre el tile). */
    fun retry(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        if (tile.source == null) return
        releasePlayer(index, reason = "retry")
        updateTile(index) {
            it.copy(status = TileStatus.LOADING, error = null, positionMs = 0L)
        }
        enqueueStart(index)
    }

    private fun markError(index: Int, msg: String) {
        DiagnosticLog.log(
            "PLAYER",
            "tile error index=$index source=${_tiles.value.getOrNull(index)?.source?.key ?: "none"} message=$msg",
        )
        updateTile(index) { it.copy(status = TileStatus.ERROR, error = msg) }
    }

    fun hasEmptySlot(): Boolean = _tiles.value.any { it.source == null }

    fun hasActiveVideos(): Boolean = _tiles.value.any { it.source != null }

    fun requestReplace(index: Int) {
        if (_tiles.value.getOrNull(index)?.source == null) return
        _singlePlayback.value = false
        _fullscreenIndex.value = null
        _pendingReplaceSlot.value = index
    }

    fun cancelReplace() {
        _pendingReplaceSlot.value = null
    }

    /** Sustituye el vídeo de una celda sin tocar las demás. */
    fun replaceTile(index: Int, source: MediaSource, title: String? = null) {
        val tile = _tiles.value.getOrNull(index) ?: return
        _singlePlayback.value = false
        _fullscreenIndex.value = null
        if (tile.source == null) {
            // La X ya liberó esta celda antes de abrir Biblioteca: conservar el
            // índice pedido evita caer en el flujo de video único.
            updateTile(index) {
                it.copy(
                    source = source,
                    title = title ?: titleFor(source),
                    status = TileStatus.LOADING,
                    error = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    videoW = 0, videoH = 0, videoCodec = null,
                    videoFps = null, videoBitrateKbps = 0, showStats = false,
                    rotationDegrees = 0f, zoom = 1f, panX = 0f, panY = 0f,
                )
            }
            _focusedIndex.value = index
            _pendingReplaceSlot.value = null
            applyAudioPolicy()
            return
        }
        releasePlayer(index, deferRelease = true, reason = "replace")
        updateTile(index) {
            it.copy(
                source = source,
                title = title ?: titleFor(source),
                status = TileStatus.LOADING,
                error = null,
                positionMs = 0L,
                durationMs = 0L,
                muted = _audioMode.value == AudioMode.MULTIPLE,
                videoW = 0, videoH = 0, videoCodec = null,
                videoFps = null, videoBitrateKbps = 0, showStats = false,
                rotationDegrees = 0f, zoom = 1f, panX = 0f, panY = 0f,
            )
        }
        _focusedIndex.value = index
        _pendingReplaceSlot.value = null
        applyAudioPolicy()
    }

    /** El Tile cambió de tamaño: re-sincroniza la ventana nativa del vout. */
    fun updateWindowSize(index: Int, width: Int, height: Int) {
        players[index]?.updateWindowSize(width, height)
    }

    fun detach(index: Int) {
        players[index]?.detachViews()
    }

    fun detachIfCurrent(index: Int, textureView: TextureView) {
        players[index]?.detachIfCurrent(textureView)
    }

    // ---------- Focus / audio ----------

    fun focus(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        if (tile.source == null) return
        _focusedIndex.value = index
        moveQueuedToFront(index)
        applyAudioPolicy()
    }

    fun toggleMute(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        if (_audioMode.value == AudioMode.SINGLE) {
            if (_focusedIndex.value != index) {
                _focusedIndex.value = index
                updateTile(index) { it.copy(muted = false) }
            } else {
                updateTile(index) { it.copy(muted = !tile.muted) }
            }
        } else {
            updateTile(index) { it.copy(muted = !tile.muted) }
        }
        applyAudioPolicy()
    }

    fun toggleAudioMode() {
        if (_audioMode.value == AudioMode.SINGLE) {
            val focused = _focusedIndex.value
            _tiles.update { tiles ->
                tiles.mapIndexed { index, tile ->
                    if (tile.source != null) tile.copy(muted = index != focused) else tile
                }
            }
            _audioMode.value = AudioMode.MULTIPLE
        } else {
            _audioMode.value = AudioMode.SINGLE
        }
        applyAudioPolicy()
    }

    fun muteAll() {
        _tiles.update { tiles ->
            tiles.map { tile -> if (tile.source != null) tile.copy(muted = true) else tile }
        }
        applyAudioPolicy()
    }

    private fun applyAudioPolicy() {
        val focused = _focusedIndex.value
        _tiles.value.forEachIndexed { i, tile ->
            val player = players[i] ?: return@forEachIndexed
            val audible = when (_audioMode.value) {
                AudioMode.SINGLE -> i == focused && !tile.muted
                AudioMode.MULTIPLE -> !tile.muted
            }
            player.setMute(!audible)
        }
    }

    fun toggleChrome() {
        _chromeVisible.value = !_chromeVisible.value
    }

    fun showChrome() {
        if (!_chromeVisible.value) _chromeVisible.value = true
    }

    // ---------- Transport ----------

    fun togglePlay(index: Int) {
        if (_synced.value) { toggleAll(); return }
        val player = players[index] ?: return
        if (player.isEnded) replayTile(index, 0L) else player.toggle()
    }

    fun toggleAll() {
        val list = players.values.toList()
        if (list.isEmpty()) return
        val anyPlaying = list.any { it.playing }
        players.forEach { (index, player) ->
            if (anyPlaying) player.pause()
            else if (player.isEnded) replayTile(index, 0L)
            else player.play()
        }
    }

    fun setVolume(index: Int, value: Float) {
        players[index]?.setVolume(value)
        updateTile(index) { it.copy(volume = value) }
    }

    /** Gesto vertical: ajusta el volumen del tile y lo desilencia si sube de 0. */
    fun adjustTileVolume(index: Int, delta: Float) {
        val tile = _tiles.value.getOrNull(index) ?: return
        val next = (tile.volume + delta).coerceIn(0f, 2f)
        players[index]?.setVolume(next)
        updateTile(index) { it.copy(volume = next, muted = if (next > 0f) false else it.muted) }
        applyAudioPolicy()
    }

    /** Salto relativo (doble-tap laterales). */
    fun seekBy(index: Int, deltaMs: Long) {
        val player = players[index] ?: return
        if (!player.started) return
        val duration = maxOf(_tiles.value.getOrNull(index)?.durationMs ?: 0L, player.durationMs)
        val target = jumpTarget(player.positionMs, duration, deltaMs)
        seekOrReplay(index, target)
        updateTile(index) { it.copy(positionMs = target) }
    }

    /** Gesto vertical izquierdo: brillo de pantalla. */
    fun adjustBrightness(delta: Float) {
        if (!brightnessInit) {
            _brightness.value = readSystemBrightness()
            brightnessInit = true
        }
        _brightness.value = ((_brightness.value ?: 0.5f) + delta).coerceIn(0.05f, 1f)
    }

    private fun readSystemBrightness(): Float = runCatching {
        Settings.System.getInt(
            getApplication<Application>().contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128,
        ) / 255f
    }.getOrDefault(0.5f)

    fun toggleLoop(index: Int) {
        updateTile(index) { it.copy(loop = !it.loop) }
    }

    fun cycleScale(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        val order = listOf(
            ScaleMode.FIT, ScaleMode.FILL, ScaleMode.CROP, ScaleMode.RATIO_16_9,
            ScaleMode.RATIO_4_3, ScaleMode.ORIGINAL,
        )
        val next = order[(order.indexOf(tile.scaleMode) + 1).coerceAtLeast(1) % order.size]
        players[index]?.setVideoScaleMode(next)
        updateTile(index) { it.copy(scaleMode = next) }
    }

    fun rotateTile(index: Int) {
        updateTile(index) {
            it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
        }
    }

    fun transformTile(
        index: Int,
        panX: Float,
        panY: Float,
        zoomChange: Float,
        viewportWidth: Float = 0f,
        viewportHeight: Float = 0f,
    ) {
        updateTile(index) {
            val nextZoom = (it.zoom * zoomChange).coerceIn(1f, 4f)
            val maxPanX = (viewportWidth * (nextZoom - 1f) / 2f).coerceAtLeast(0f)
            val maxPanY = (viewportHeight * (nextZoom - 1f) / 2f).coerceAtLeast(0f)
            it.copy(
                zoom = nextZoom,
                panX = if (nextZoom <= 1.01f) 0f else (it.panX + panX).coerceIn(-maxPanX, maxPanX),
                panY = if (nextZoom <= 1.01f) 0f else (it.panY + panY).coerceIn(-maxPanY, maxPanY),
            )
        }
    }

    fun resetTransform(index: Int) {
        updateTile(index) { it.copy(rotationDegrees = 0f, zoom = 1f, panX = 0f, panY = 0f) }
    }

    fun scaleLabel(mode: ScaleMode): String = when (mode) {
        ScaleMode.FIT -> "Ajustar"
        ScaleMode.FILL -> "Llenar"
        ScaleMode.CROP -> "Recortar"
        ScaleMode.RATIO_16_9 -> "16:9"
        ScaleMode.RATIO_4_3 -> "4:3"
        ScaleMode.ORIGINAL -> "Original"
    }

    fun setSpeed(index: Int, value: Float) {
        val v = value.coerceIn(0.25f, 4f)
        players[index]?.setSpeed(v)
        updateTile(index) { it.copy(speed = v) }
    }

    // ---------- Ecualizador global ----------

    val eqPresets: List<String> by lazy {
        runCatching {
            buildList {
                add("Plano")
                repeat(MediaPlayer.Equalizer.getPresetCount()) { i ->
                    add(MediaPlayer.Equalizer.getPresetName(i))
                }
            }
        }.getOrDefault(listOf("Plano"))
    }

    private val _eqPreset = MutableStateFlow(0)
    val eqPreset: StateFlow<Int> = _eqPreset.asStateFlow()

    fun setEqPreset(index: Int) {
        _eqPreset.value = index.coerceIn(0, eqPresets.size - 1)
        val native = if (_eqPreset.value == 0) null else _eqPreset.value - 1
        pendingEq.clear()
        players.values.forEach { it.setEqualizerPreset(native) }
    }

    /** Quita un vídeo del muro sin tocar los demás (botón × del tile). */
    fun removeTile(index: Int) {
        _singlePlayback.value = false
        releasePlayer(index, reason = "remove")
        attachedViews.remove(index)
        if (_pendingSwap.value == index) _pendingSwap.value = null
        if (_fullscreenIndex.value == index) _fullscreenIndex.value = null
        updateTile(index) {
            it.copy(
                source = null, title = null, status = TileStatus.EMPTY,
                positionMs = 0L, durationMs = 0L,
                videoW = 0, videoH = 0, videoCodec = null,
                videoFps = null, videoBitrateKbps = 0, showStats = false,
            )
        }
        val nextFocus = _tiles.value.indexOfFirst { t -> t.source != null }
        _focusedIndex.value = if (nextFocus >= 0) nextFocus else 0
        applyAudioPolicy()
    }

    /** true si este tile es el único video activo del muro. */
    fun isOnlyActiveTile(index: Int): Boolean {
        val tiles = _tiles.value
        return tiles.getOrNull(index)?.source != null && tiles.count { it.source != null } == 1
    }

    /** Libera una celda y la deja como destino explícito para Biblioteca. */
    fun removeAndRequestReplace(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        _singlePlayback.value = false
        _fullscreenIndex.value = null
        if (tile.source == null) {
            _pendingReplaceSlot.value = index
            return
        }
        releasePlayer(index, deferRelease = true, reason = "replace-request")
        attachedViews.remove(index)
        if (_pendingSwap.value == index) _pendingSwap.value = null
        updateTile(index) {
            it.copy(
                source = null, title = null, status = TileStatus.EMPTY,
                positionMs = 0L, durationMs = 0L,
                videoW = 0, videoH = 0, videoCodec = null,
                videoFps = null, videoBitrateKbps = 0, showStats = false,
                rotationDegrees = 0f, zoom = 1f, panX = 0f, panY = 0f,
            )
        }
        _pendingReplaceSlot.value = index
        val nextFocus = _tiles.value.indexOfFirst { current -> current.source != null }
        _focusedIndex.value = if (nextFocus >= 0) nextFocus else index
        applyAudioPolicy()
    }

    fun toggleStats(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        val next = !tile.showStats
        updateTile(index) { it.copy(showStats = next) }
        if (next) refreshTrackInfo(index)
    }

    private fun refreshTrackInfo(index: Int) {
        val track = players[index]?.videoTrackInfo() ?: return
        val fps = if (track.frameRateDen > 0) track.frameRateNum.toFloat() / track.frameRateDen else null
        updateTile(index) {
            it.copy(
                videoW = track.width,
                videoH = track.height,
                videoCodec = track.codec,
                videoFps = fps,
                videoBitrateKbps = (track.bitrate / 1000).coerceAtLeast(0),
                hwUsed = hwEnabledFor(index),
            )
        }
    }

    fun seekPreview(index: Int, ms: Long) {
        updateTile(index) { it.copy(positionMs = ms.coerceAtLeast(0L)) }
    }

    /** Posición fresca del player sin pasar por el estado (para tickers de UI). */
    fun playerPosition(index: Int): Long =
        players[index]?.positionMs ?: _tiles.value.getOrNull(index)?.positionMs ?: 0L

    /** Fps renderizados frescos (para el HUD de stats). */
    fun playerRenderFps(index: Int): Float = players[index]?.renderFps ?: 0f

    /** Registra un frame pintado (vía onSurfaceTextureUpdated, no interfiere con libVLC). */
    fun trackFrame(index: Int) {
        players[index]?.trackFrame()
    }

    fun commitSeek(index: Int, ms: Long) {
        val duration = _tiles.value.getOrNull(index)?.durationMs ?: 0L
        // Margen anti-fin: nunca buscar al milisegundo final (dispararía EndReached).
        val target = if (duration > 2000L) {
            ms.coerceIn(0L, duration - 1000L)
        } else {
            ms.coerceAtLeast(0L)
        }
        lastLiveSeek.remove(index)
        seekOrReplay(index, target)
        if (_synced.value) {
            players.keys.filter { it != index }.forEach { seekOrReplay(it, target) }
        }
        updateTile(index) { it.copy(positionMs = target) }
    }

    /** Scrub horizontal por gestos: avanza incremental sobre el estado fresco. */
    fun scrubPreview(index: Int, deltaMs: Long) {
        if (players[index]?.started != true) return
        updateTile(index) {
            val max = (it.durationMs - 1000L).coerceAtLeast(0L)
            it.copy(positionMs = (it.positionMs + deltaMs).coerceIn(0L, max))
        }
    }

    fun commitScrub(index: Int) {
        val tile = _tiles.value.getOrNull(index) ?: return
        lastLiveSeek.remove(index)
        val target = lastScrubTarget.remove(index) ?: tile.positionMs
        seekOrReplay(index, target)
        if (_synced.value) {
            players.keys.filter { it != index }.forEach { seekOrReplay(it, target) }
        }
        updateTile(index) { it.copy(positionMs = target) }
    }

    /**
     * Scrub en vivo estilo VLC: el video muestra los frames mientras se arrastra.
     * Durante el arrastre no se toca el estado global (la UI va por estado local);
     * el commit al soltar fija la posición exacta. Throttle 300 ms entre seeks.
     * En modo sync, el vivo es solo del tile arrastrado; al soltar se aplica a todos.
     */
    fun scrubLive(index: Int, ms: Long) {
        val tile = _tiles.value.getOrNull(index) ?: return
        liveSeekTo(index, ms.coerceIn(0L, scrubMax(tile.durationMs)))
    }

    fun scrubLiveBy(index: Int, deltaMs: Long) {
        val player = players[index] ?: return
        if (!player.started) return
        val tile = _tiles.value.getOrNull(index) ?: return
        liveSeekTo(index, (player.positionMs + deltaMs).coerceIn(0L, scrubMax(tile.durationMs)))
    }

    private fun scrubMax(durationMs: Long): Long =
        if (durationMs > 2000L) durationMs - 1000L else durationMs.coerceAtLeast(0L)

    private fun liveSeekTo(index: Int, target: Long) {
        val player = players[index] ?: return
        if (!player.started) return
        val now = System.currentTimeMillis()
        if (now - (lastLiveSeek[index] ?: 0L) < 300L) return
        lastLiveSeek[index] = now
        lastScrubTarget[index] = target
        seekOrReplay(index, target)
    }

    /** LibVLC no acepta setTime tras EndReached; hay que abrir la fuente de nuevo. */
    private fun seekOrReplay(index: Int, target: Long) {
        val player = players[index] ?: return
        if (player.isEnded) {
            replayTile(index, target)
        } else if (pendingSeek.containsKey(index) && !player.playing) {
            // El player todavía está arrancando: conserva el último punto arrastrado.
            pendingSeek[index] = target
        } else {
            player.seekTo(target)
        }
    }

    private fun replayTile(index: Int, target: Long) {
        val tile = _tiles.value.getOrNull(index) ?: return
        val source = tile.source ?: return
        val player = players[index] ?: return
        pendingSeek[index] = target.coerceIn(0L, scrubMax(tile.durationMs))
        updateTile(index) { it.copy(status = TileStatus.LOADING, positionMs = target) }
        runCatching {
            player.play(getApplication(), source, hwEnabledFor(index))
            player.setVolume(tile.volume)
            player.setSpeed(tile.speed)
        }.onFailure { error -> markError(index, error.message ?: "No se pudo reiniciar") }
    }

    fun toggleSync() {
        _synced.value = !_synced.value
    }

    // ---------- Fullscreen ----------

    fun toggleFullscreen(index: Int) {
        _fullscreenIndex.value = if (_fullscreenIndex.value == index) null else index
        moveQueuedToFront(index)
    }

    fun exitFullscreen() {
        _fullscreenIndex.value = null
    }

    /** Cierra la reproducción temporal de un video único antes de volver a Biblioteca. */
    fun closeSinglePlayback() {
        // Biblioteca debe poder dibujarse antes de que LibVLC bloquee al soltar el vout.
        resetWall(deferRelease = true)
    }

    private fun moveQueuedToFront(index: Int) {
        if (startQueue.remove(index)) {
            startQueue.addFirst(index)
        }
    }

    // ---------- Swap ----------

    fun swapSelect(index: Int) {
        val pending = _pendingSwap.value
        if (pending == null) {
            _pendingSwap.value = index
        } else if (pending == index) {
            _pendingSwap.value = null
        } else {
            doSwap(pending, index)
            _pendingSwap.value = null
        }
    }

    private fun doSwap(from: Int, to: Int) {
        val list = _tiles.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val a = list[from]
        val b = list[to]
        list[from] = b.copy(index = from)
        list[to] = a.copy(index = to)
        _tiles.value = list
        releasePlayer(from, reason = "swap")
        releasePlayer(to, reason = "swap")
    }

    // ---------- Reset ----------

    fun clear() {
        resetWall()
    }

    private fun resetWall(deferRelease: Boolean = false) {
        players.forEach { (index, player) ->
            recordPlaybackProgress(index, player.positionMs, _tiles.value.getOrNull(index)?.durationMs ?: 0L, force = true)
        }
        val releasedPlayers = players.values.toList()
        players.clear()
        startQueue.clear()
        pendingSeek.clear()
        pendingEq.clear()
        lastLiveSeek.clear()
        lastScrubTarget.clear()
        lastSavedProgressBucket.clear()
        suspendedPlaying.clear()
        wallPlaybackSuspended = false
        attachedViews.clear()
        _fullscreenIndex.value = null
        _singlePlayback.value = false
        _pendingSwap.value = null
        _pendingReplaceSlot.value = null
        _focusedIndex.value = 0
        _tiles.value = buildTiles(_slots.value)
        val suspension = wallSuspendJob?.takeIf { it.isActive }
        if (deferRelease || suspension != null) {
            releasedPlayers.forEach {
                it.eventCallback = null
                it.layoutCallback = null
            }
            viewModelScope.launch(Dispatchers.IO) {
                suspension?.join()
                // Da al NavHost al menos un frame para mostrar Biblioteca antes de
                // stop/detach/release, operaciones que pueden bloquear con LibVLC.
                delay(64)
                releasedPlayers.forEach {
                    DiagnosticLog.log(
                        "PLAYER",
                        "release player=${it.debugId} source=${it.sourceKey ?: "none"} " +
                            "deferred=true reason=reset-wall",
                    )
                    it.release()
                }
            }
        } else {
            releasedPlayers.forEach {
                DiagnosticLog.log(
                    "PLAYER",
                    "release player=${it.debugId} source=${it.sourceKey ?: "none"} " +
                        "deferred=false reason=reset-wall",
                )
                it.release()
            }
        }
    }

    // ---------- Persistence ----------

    fun persistNow() {
        viewModelScope.launch { withContext(Dispatchers.IO) { saveCurrent() } }
    }

    private suspend fun saveCurrent() {
        val layout = _slots.value
        val persisted = _tiles.value.mapNotNull { tile ->
            tile.source?.let {
                it.toPersisted(
                    positionMs = players[tile.index]?.positionMs ?: tile.positionMs,
                    rotationDegrees = tile.rotationDegrees,
                    zoom = tile.zoom,
                    panX = tile.panX,
                    panY = tile.panY,
                )
            }
        }
        session.save(layout, persisted)
    }

    private suspend fun restore() {
        val data = session.load() ?: return
        if (data.tiles.isEmpty()) return
        val layout = data.layout.coerceIn(MIN_SLOTS, MAX_SLOTS)
        _slots.value = layout
        _tiles.value = buildTiles(layout).mapIndexed { i, t ->
            val p = data.tiles.getOrNull(i)
            val source = p?.toSource()
            t.copy(
                source = source,
                title = source?.let { titleFor(it) },
                positionMs = p?.positionMs ?: 0L,
                rotationDegrees = p?.rotationDegrees ?: 0f,
                zoom = p?.zoom ?: 1f,
                panX = p?.panX ?: 0f,
                panY = p?.panY ?: 0f,
                status = if (source != null) TileStatus.LOADING else TileStatus.EMPTY,
            )
        }
        updateHwWarning()
    }

    // ---------- Events ----------

    private fun getOrCreatePlayer(index: Int): MagikPlayer {
        return players.getOrPut(index) {
            MagikPlayer(libVLC).also { player ->
                player.eventCallback = { event -> handleEvent(index, event) }
                player.layoutCallback = { w, h, _, _ ->
                    if (w > 0 && h > 0) {
                        updateTile(index) { it.copy(videoW = w, videoH = h) }
                    }
                }
            }
        }
    }

    private fun releasePlayer(
        index: Int,
        deferRelease: Boolean = false,
        reason: String = "unspecified",
    ) {
        val tile = _tiles.value.getOrNull(index)
        val player = players[index]
        if (tile != null && player != null) {
            recordPlaybackProgress(index, player.positionMs, tile.durationMs, force = true)
        }
        startQueue.remove(index)
        suspendedPlaying.remove(index)
        pendingSeek.remove(index)
        pendingEq.remove(index)
        lastLiveSeek.remove(index)
        lastScrubTarget.remove(index)
        lastSavedProgressBucket.remove(index)
        val released = players.remove(index) ?: return
        DiagnosticLog.log(
            "PLAYER",
            "release index=$index player=${released.debugId} source=${released.sourceKey ?: tile?.source?.key ?: "none"} " +
                "deferred=$deferRelease reason=$reason",
        )
        val suspension = wallSuspendJob?.takeIf { it.isActive }
        if (deferRelease || suspension != null) {
            released.eventCallback = null
            released.layoutCallback = null
            viewModelScope.launch(Dispatchers.IO) {
                suspension?.join()
                delay(64)
                released.release()
            }
        } else {
            released.release()
        }
    }

    private fun recordPlaybackProgress(index: Int, positionMs: Long, durationMs: Long, force: Boolean = false) {
        val source = _tiles.value.getOrNull(index)?.source as? MediaSource.Local ?: return
        if (durationMs <= 0L || positionMs < 0L) return
        val bucket = ((positionMs.coerceAtMost(durationMs) * 10L) / durationMs).toInt()
        if (!force && bucket <= (lastSavedProgressBucket[index] ?: -1)) return
        lastSavedProgressBucket[index] = maxOf(lastSavedProgressBucket[index] ?: -1, bucket)
        viewModelScope.launch(Dispatchers.IO) {
            library.savePlaybackProgress(source.uri, positionMs, durationMs)
        }
    }

    private fun hwEnabledFor(index: Int): Boolean = index < hwLimit

    private fun handleEvent(index: Int, event: Event) {
        val current = _tiles.value.getOrNull(index) ?: return
        when (event.type) {
            Event.Playing -> {
                DiagnosticLog.log(
                    "PLAYER",
                    "event Playing index=$index player=${players[index]?.debugId ?: "none"} " +
                        "source=${current.source?.key ?: "none"}",
                )
                updateTile(index) { it.copy(status = TileStatus.PLAYING) }
                if (current.showStats) refreshTrackInfo(index)
                pendingSeek.remove(index)?.let { target ->
                    players[index]?.seekTo(target)
                }
                if (pendingEq.containsKey(index)) {
                    players[index]?.setEqualizerPreset(pendingEq.remove(index))
                }
            }
            Event.Paused -> updateTile(index) { it.copy(status = TileStatus.PAUSED) }
            Event.Stopped -> updateTile(index) { it.copy(status = TileStatus.PAUSED) }
            Event.TimeChanged -> {
                if (WallPerformancePolicy.shouldPublishPosition(
                        index = index,
                        focusedIndex = _focusedIndex.value,
                        fullscreenIndex = _fullscreenIndex.value,
                        deltaMs = abs(event.timeChanged - current.positionMs),
                    )
                ) {
                    updateTile(index) { it.copy(positionMs = event.timeChanged) }
                }
                recordPlaybackProgress(index, event.timeChanged, players[index]?.durationMs ?: current.durationMs)
            }
            Event.LengthChanged ->
                if (event.lengthChanged > 0 && event.lengthChanged != current.durationMs) {
                    updateTile(index) { it.copy(durationMs = event.lengthChanged) }
                }
            Event.EndReached -> {
                if (current.loop) {
                    replayTile(index, 0L)
                } else {
                    val duration = (players[index]?.durationMs ?: current.durationMs).coerceAtLeast(0L)
                    recordPlaybackProgress(index, duration, duration, force = true)
                    updateTile(index) { it.copy(status = TileStatus.PAUSED, positionMs = duration) }
                }
            }
            Event.EncounteredError -> {
                DiagnosticLog.log(
                    "PLAYER",
                    "event EncounteredError index=$index player=${players[index]?.debugId ?: "none"} " +
                        "source=${current.source?.key ?: "none"}",
                )
                updateTile(index) {
                    it.copy(status = TileStatus.ERROR, error = "No se pudo reproducir")
                }
            }
            else -> Unit
        }
    }

    private fun updateTile(index: Int, transform: (Tile) -> Tile) {
        _tiles.update { list ->
            if (index !in list.indices) list
            else list.toMutableList().also { it[index] = transform(it[index]) }
        }
    }

    private fun updateHwWarning() {
        _hwWarning.value = _slots.value > hwLimit
    }

    override fun onCleared() {
        players.values.forEach {
            DiagnosticLog.log(
                "PLAYER",
                "release player=${it.debugId} source=${it.sourceKey ?: "none"} reason=viewmodel-cleared",
            )
            it.release()
        }
        players.clear()
        super.onCleared()
    }

    private companion object {
        const val MIN_SLOTS = 1
        const val MAX_SLOTS = 9
        const val DEFAULT_SLOT_COUNT = 1
        val LAYOUT_OPTIONS = listOf(1, 2, 4, 6, 9)

        fun buildTiles(count: Int): List<Tile> = List(count) { i -> Tile(index = i) }

        fun titleFor(source: MediaSource): String? = when (source) {
            is MediaSource.Local -> source.uri.lastPathSegment?.substringAfterLast('/')
            is MediaSource.Network -> source.url
        }
    }
}
