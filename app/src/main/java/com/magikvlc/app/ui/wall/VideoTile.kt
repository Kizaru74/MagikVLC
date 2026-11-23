package com.magikvlc.app.ui.wall

import android.view.TextureView
import android.graphics.SurfaceTexture
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.magikvlc.app.data.formatDuration
import com.magikvlc.app.data.saveScreenshot
import com.magikvlc.app.diagnostics.DiagnosticLog
import com.magikvlc.app.player.ScaleMode
import com.magikvlc.app.player.Tile
import com.magikvlc.app.player.TileStatus

@Composable
fun VideoTile(
    tile: Tile,
    viewModel: MosaicViewModel,
    onAdd: () -> Unit,
    pendingSwap: Boolean,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    totalTiles: Int = 0,
    onReplaceVideo: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val textureView = remember { TextureView(context) }
    var controlsVisible by remember { mutableStateOf(false) }
    var scrubbing by remember(tile.index) { mutableStateOf(false) }
    var viewSize by remember(tile.index) { mutableStateOf(IntSize.Zero) }
    // Recortar: escala para llenar la celda sin deformar (la raíz ya recorta el sobrante).
    val cropScale = if (tile.scaleMode == ScaleMode.CROP &&
        tile.videoW > 0 && tile.videoH > 0 && viewSize.width > 0 && viewSize.height > 0
    ) {
        val cellAspect = viewSize.width.toFloat() / viewSize.height
        val videoAspect = tile.videoW.toFloat() / tile.videoH
        val fit = cellAspect / videoAspect
        val fill = videoAspect / cellAspect
        (if (fit > fill) fit else fill).coerceIn(1f, 4f)
    } else {
        1f
    }

    // Burbuja OSD estilo VLC: feedback de gestos sin abrir el overlay.
    var osd by remember { mutableStateOf<OsdData?>(null) }
    var volShown by remember(tile.index) { mutableFloatStateOf(-1f) }
    var briShown by remember(tile.index) { mutableFloatStateOf(-1f) }
    var scrubShown by remember(tile.index) { mutableLongStateOf(0L) }
    fun showOsd(label: String, progress: Float?) {
        osd = OsdData(label = label, progress = progress, seq = (osd?.seq ?: 0L) + 1L)
    }
    LaunchedEffect(osd?.seq) {
        if (osd != null) {
            delay(1000)
            osd = null
        }
    }
    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).clipToBounds()) {
        if (tile.source == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onAdd() },
            ) {
                EmptyTilePlaceholder()
            }
        } else {
            val listenerHandle = remember(tile.index) { object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    if (width > 0 && height > 0) viewModel.attach(tile.index, textureView)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    viewModel.updateWindowSize(tile.index, width, height)
                }
                // false: el framework libera la superficie; el vout ya fue desatado si era la actual.
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false
                // Conteo para el HUD de fps: no interfiere con el listener interno de libVLC.
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    viewModel.trackFrame(tile.index)
                }
            } }
            DisposableEffect(tile.index, tile.source) {
                if (textureView.isAvailable && textureView.width > 0 && textureView.height > 0) {
                    viewModel.attach(tile.index, textureView)
                } else {
                    textureView.surfaceTextureListener = listenerHandle
                    if (textureView.isAvailable) viewModel.attach(tile.index, textureView)
                }
                onDispose {
                    DiagnosticLog.log("TILE", "dispose index=${tile.index} source=${tile.source?.key ?: "empty"}")
                    viewModel.detachIfCurrent(tile.index, textureView)
                    textureView.surfaceTextureListener = null
                }
            }

            AndroidView(
                factory = { textureView },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cropScale * tile.zoom
                        scaleY = cropScale * tile.zoom
                        rotationZ = tile.rotationDegrees
                        translationX = tile.panX
                        translationY = tile.panY
                    }
                    .onSizeChanged {
                        viewSize = it
                        viewModel.updateWindowSize(tile.index, it.width, it.height)
                    }
                    .pointerInput(tile.index) {
                        detectTapGestures(
                            onTap = {
                                if (tile.status == TileStatus.ERROR) {
                                    viewModel.retry(tile.index)
                                } else {
                                    viewModel.focus(tile.index)
                                    controlsVisible = !controlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                val w = size.width.toFloat()
                                when {
                                    offset.x < w * 0.33f -> {
                                        viewModel.seekBy(tile.index, -10_000L)
                                        haptic()
                                        showOsd("−10 s", null)
                                    }
                                    offset.x > w * 0.67f -> {
                                        viewModel.seekBy(tile.index, 10_000L)
                                        haptic()
                                        showOsd("+10 s", null)
                                    }
                                    else -> {
                                        controlsVisible = false
                                        if (!isFullscreen) viewModel.toggleFullscreen(tile.index)
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(tile.index) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            viewModel.focus(tile.index)
                            viewModel.transformTile(
                                tile.index,
                                pan.x,
                                pan.y,
                                zoom,
                                    viewSize.width.toFloat(),
                                    viewSize.height.toFloat(),
                            )
                        }
                    }
                    .pointerInput(tile.index) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                viewModel.focus(tile.index)
                                scrubShown = 0L
                                showOsd("±0 s", null)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val duration = tile.durationMs.coerceAtLeast(1L)
                                // Un swipe completo avanza 60 s como máximo (no el video entero).
                                val range = if (duration < 60_000L) duration else 60_000L
                                val delta = (dragAmount / size.width * range).toLong()
                                scrubShown += delta
                                viewModel.scrubLiveBy(tile.index, delta)
                                val s = scrubShown / 1000L
                                showOsd(if (s >= 0) "+${s} s" else "−${-s} s", null)
                            },
                            onDragEnd = { viewModel.commitScrub(tile.index) },
                        )
                    }
                    .pointerInput(tile.index) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                viewModel.focus(tile.index)
                                volShown = tile.volume
                                briShown = -1f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // Rango 0..200%: un swipe completo recorre todo el rango.
                                val delta = -dragAmount / size.height * 2f
                                if (change.position.x > size.width / 2) {
                                    viewModel.adjustTileVolume(tile.index, delta)
                                    if (volShown < 0f) volShown = tile.volume
                                    volShown = (volShown + delta).coerceIn(0f, 2f)
                                    showOsd("Volumen ${(volShown * 100).toInt()}%", volShown)
                                } else {
                                    viewModel.adjustBrightness(delta)
                                    if (briShown < 0f) briShown = 0.5f
                                    briShown = (briShown + delta).coerceIn(0.05f, 1f)
                                    showOsd("Brillo ${(briShown * 100).toInt()}%", briShown)
                                }
                            },
                        )
                    },
            )

            var showSpeedDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    val dialogOpen = showSpeedDialog || showMoreDialog

    LaunchedEffect(controlsVisible, tile.status, dialogOpen, scrubbing) {
                // Con un diálogo abierto o mientras se arrastra la seekbar, no se oculta.
                if (controlsVisible && tile.status == TileStatus.PLAYING && !dialogOpen && !scrubbing) {
                    delay(6000)
                    controlsVisible = false
                }
            }

            when (tile.status) {
                TileStatus.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
                TileStatus.ERROR -> Text(
                    text = tile.error ?: "Error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Unit
            }

TileOverlay(
                    tile = tile,
                    viewModel = viewModel,
                    showToolbar = controlsVisible,
                    scrubbing = scrubbing,
                    onScrubbingChange = { scrubbing = it },
                    isFullscreen = isFullscreen,
                    totalTiles = totalTiles,
                    showSpeedDialog = showSpeedDialog,
                    showMoreDialog = showMoreDialog,
                    onSpeedDialogChange = { showSpeedDialog = it },
                    onMoreDialogChange = { showMoreDialog = it },
                    onReplaceVideo = {
                        viewModel.requestReplace(tile.index)
                        onReplaceVideo()
                    },
                    onRemoveAndReplace = {
                        if (viewModel.isOnlyActiveTile(tile.index)) {
                            // Único video del muro: cerramos la sesión completa
                            // y volvemos a Biblioteca sin pedir reemplazo.
                            viewModel.closeSinglePlayback()
                        } else {
                            viewModel.removeAndRequestReplace(tile.index)
                        }
                        onReplaceVideo()
                    },
                    onScreenshot = {
                        val bitmap = textureView.bitmap
                        if (bitmap != null) {
                            val uri = saveScreenshot(context, bitmap)
                            Toast.makeText(
                                context,
                                if (uri != null) "Captura guardada" else "No se pudo guardar",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    IconButton(onClick = { viewModel.toggleFullscreen(tile.index) }) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
if (pendingSwap) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.align(Alignment.TopCenter).padding(4.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "⇄ Origen del intercambio",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            osd?.let { data ->
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = data.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        data.progress?.let { p ->
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier
                                    .width(120.dp)
                                    .padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.25f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TileOverlay(
    tile: Tile,
    viewModel: MosaicViewModel,
    showToolbar: Boolean,
    scrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    isFullscreen: Boolean,
    totalTiles: Int,
    showSpeedDialog: Boolean,
    showMoreDialog: Boolean,
    onSpeedDialogChange: (Boolean) -> Unit,
    onMoreDialogChange: (Boolean) -> Unit,
    onScreenshot: () -> Unit,
    onReplaceVideo: () -> Unit,
    onRemoveAndReplace: () -> Unit,
) {
    if (!showToolbar) return

    val duration = tile.durationMs.coerceAtLeast(1L)
    var dragPosition by remember { mutableIntStateOf(tile.positionMs.toInt()) }
    // Ticker local: la seekbar se mueve fluido sin recomponer todo el muro.
    var tickMs by remember(tile.index) { mutableLongStateOf(tile.positionMs) }
    var renderFps by remember(tile.index) { mutableFloatStateOf(0f) }
    LaunchedEffect(showToolbar, tile.index) {
        if (!showToolbar) return@LaunchedEffect
        while (true) {
            tickMs = viewModel.playerPosition(tile.index)
            if (tile.showStats) renderFps = viewModel.playerRenderFps(tile.index)
            delay(250)
        }
    }
    val timeStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val currentMs = if (scrubbing) dragPosition.toLong() else tickMs
    val remainingMs = (duration - currentMs).coerceAtLeast(0L)

    // Piezas del overlay reutilizables: en fullscreen se montan en un panel
    // central; en el muro mantienen la barra de arriba y la de abajo.
    @Composable
    fun TitlePill() {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tile.title ?: "Video",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (totalTiles > 1) {
                    Text(
                        text = " · ${tile.index + 1}/$totalTiles",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    @Composable
    fun SeekRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(currentMs),
                color = Color.White.copy(alpha = 0.9f),
                style = if (scrubbing) {
                    timeStyle.copy(fontSize = timeStyle.fontSize * 1.5f)
                } else {
                    timeStyle
                },
            )
            Slider(
                value = currentMs.toFloat(),
                onValueChange = { v ->
                    onScrubbingChange(true)
                    dragPosition = v.toInt()
                    viewModel.scrubLive(tile.index, v.toLong())
                },
                onValueChangeFinished = {
                    onScrubbingChange(false)
                    viewModel.commitSeek(tile.index, dragPosition.toLong())
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            Text(
                text = "-" + formatDuration(remainingMs),
                color = Color.White.copy(alpha = 0.9f),
                style = timeStyle,
            )
        }
    }

    @Composable
    fun StatsRow() {
        if (tile.showStats) {
            Text(
                text = (tile.title ?: "Video") + "\n" + buildStatsText(tile, renderFps),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }

    @Composable
    fun ActionButtons() {
        ControlsButton(onClick = { viewModel.toggleMute(tile.index) }) {
            Icon(
                imageVector = if (tile.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = "Silencio",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        ControlsButton(onClick = { viewModel.toggleLoop(tile.index) }) {
            Icon(
                imageVector = Icons.Filled.Repeat,
                contentDescription = "Bucle",
                tint = if (tile.loop) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        if (isFullscreen) {
            // Play/Pause grande al centro de la fila
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                border = BorderStroke(2.dp, Color.White),
                modifier = Modifier
                    .size(64.dp)
                    .padding(0.dp),
            ) {
                IconButton(onClick = { viewModel.togglePlay(tile.index) }) {
                    Icon(
                        imageVector = if (tile.status == TileStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        } else {
            ControlsButton(onClick = { viewModel.togglePlay(tile.index) }) {
                Icon(
                    imageVector = if (tile.status == TileStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        ControlsButton(onClick = onReplaceVideo) {
            Icon(
                imageVector = Icons.Filled.Autorenew,
                contentDescription = "Cambiar video",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        ControlsButton(onClick = { viewModel.cycleScale(tile.index) }) {
            Icon(
                imageVector = Icons.Filled.AspectRatio,
                contentDescription = viewModel.scaleLabel(tile.scaleMode),
                tint = if (tile.scaleMode != ScaleMode.FIT) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        ControlsButton(onClick = { viewModel.rotateTile(tile.index) }) {
            Icon(
                imageVector = Icons.Filled.RotateRight,
                contentDescription = "Rotar 90°",
                tint = if (tile.rotationDegrees != 0f) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        ControlsButton(onClick = onRemoveAndReplace) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cerrar y elegir otro video",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        ControlsButton(onClick = { onMoreDialogChange(true) }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    // Botones de acción estilo mpvEx ControlsButton
    @Composable
    fun ButtonsRow() {
        if (isFullscreen) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Sin scroll, Arrangement.Center centra de verdad los controles.
                // En pantallas estrechas se conserva el scroll para no recortarlos.
                val controlsWidth = 360.dp
                if (maxWidth >= controlsWidth) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionButtons()
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionButtons()
                    }
                }
            }
        } else {
            // Muro: fila a la izquierda con scroll.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButtons()
            }
        }
    }

    // Barra superior: gradiente + pill con título (izquierda)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.65f),
                        Color.Transparent,
                    ),
                ),
            )
            .padding(top = 12.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
    ) {
        TitlePill()
    }

    // Barra inferior: gradiente + seekbar con tiempos + botones + stats
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.75f),
                    ),
                ),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SeekRow()
            ButtonsRow()
            StatsRow()
        }
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { onSpeedDialogChange(false) },
            title = { Text("Velocidad") },
            text = {
                Column {
                    Text(
                        text = String.format(java.util.Locale.US, "%.2f×", tile.speed),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Slider(
                        value = tile.speed,
                        onValueChange = { viewModel.setSpeed(tile.index, it) },
                        valueRange = 0.25f..4f,
                        steps = 14,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("0.25×", style = MaterialTheme.typography.labelSmall)
                        Text("4×", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSpeedDialogChange(false) }) { Text("Listo") }
            },
        )
    }

    if (showMoreDialog) {
        AlertDialog(
            onDismissRequest = { onMoreDialogChange(false) },
            title = { Text("Más opciones") },
            text = {
                Column {
                    MoreAction(
                        text = "Velocidad · " + String.format(java.util.Locale.US, "%.2g×", tile.speed),
                        onClick = {
                            onMoreDialogChange(false)
                            onSpeedDialogChange(true)
                        },
                    )
                    MoreAction(text = "Captura de frame") {
                        onMoreDialogChange(false)
                        onScreenshot()
                    }
                    MoreAction(text = "Restablecer zoom y rotación") {
                        onMoreDialogChange(false)
                        viewModel.resetTransform(tile.index)
                    }
                    MoreAction(text = "Intercambiar posición") {
                        onMoreDialogChange(false)
                        viewModel.swapSelect(tile.index)
                    }
                    MoreAction(text = if (tile.showStats) "Ocultar stats" else "Mostrar stats") {
                        onMoreDialogChange(false)
                        viewModel.toggleStats(tile.index)
                    }
                    MoreAction(text = "Quitar video", danger = true) {
                        onMoreDialogChange(false)
                        viewModel.removeTile(tile.index)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (tile.muted || tile.volume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Slider(
                            value = tile.volume,
                            onValueChange = { viewModel.setVolume(tile.index, it) },
                            valueRange = 0f..2f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(
                            text = "${(tile.volume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onMoreDialogChange(false) }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
private fun MoreAction(
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class OsdData(val label: String, val progress: Float?, val seq: Long)

@Composable
private fun ControlsButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private fun buildStatsText(tile: Tile, renderFps: Float = 0f): String {
    val res = if (tile.videoW > 0) "${tile.videoW}×${tile.videoH}" else "…"
    val fps = tile.videoFps?.let { String.format(java.util.Locale.US, "%.1f fps", it) } ?: "… fps"
    val codec = tile.videoCodec ?: "…"
    val br = if (tile.videoBitrateKbps > 0) "${tile.videoBitrateKbps} kbps" else "… kbps"
    val dec = if (tile.hwUsed) "HW" else "SW"
    val rind = String.format(java.util.Locale.US, "%.0f", renderFps)
    return "$codec · $res · $fps · rind ${rind}fps · $br · $dec · ${tile.speed}×"
}

@Composable
private fun EmptyTilePlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 56.sp)
        Text(
            text = "Añadir video",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
