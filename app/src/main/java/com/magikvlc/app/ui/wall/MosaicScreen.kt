package com.magikvlc.app.ui.wall

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magikvlc.app.player.MediaSource
import com.magikvlc.app.player.Tile
import com.magikvlc.app.player.TileStatus
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun MosaicScreen(
    viewModel: MosaicViewModel,
    onOpenLibrary: () -> Unit,
    onAddUrl: () -> Unit,
    onEnterPip: (() -> Unit)? = null,
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val fullscreenIndex by viewModel.fullscreenIndex.collectAsStateWithLifecycle()
    val pendingSwap by viewModel.pendingSwap.collectAsStateWithLifecycle()
    val hwWarning by viewModel.hwWarning.collectAsStateWithLifecycle()
    val synced by viewModel.synced.collectAsStateWithLifecycle()
    val audioMode by viewModel.audioMode.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()
    val pipMode by viewModel.pipMode.collectAsStateWithLifecycle()
    val autoPip by viewModel.autoPip.collectAsStateWithLifecycle()
    val singlePlayback by viewModel.singlePlayback.collectAsStateWithLifecycle()
    val anyPlaying = tiles.any { it.status == TileStatus.PLAYING }
    var showEq by remember { mutableStateOf(false) }
    val eqPreset by viewModel.eqPreset.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(brightness) {
        (context as? Activity)?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val placed = viewModel.add(MediaSource.Local(uri), uri.lastPathSegment?.substringAfterLast('/'))
            if (!placed) {
                Toast.makeText(context, "Muro lleno: quita un video primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = !pipMode) {
        when {
            singlePlayback -> {
            viewModel.closeSinglePlayback()
            onOpenLibrary()
            }
            fullscreenIndex != null -> viewModel.exitFullscreen()
            else -> onOpenLibrary()
        }
    }

    LaunchedEffect(chromeVisible, fullscreenIndex, pipMode) {
        if (chromeVisible && fullscreenIndex == null && !pipMode) {
            delay(6000)
            if (viewModel.chromeVisible.value) viewModel.toggleChrome()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (fullscreenIndex == null && chromeVisible && !pipMode) {
                MosaicTopBar(
                    slots = viewModel.slots.collectAsStateWithLifecycle().value,
                    viewModel = viewModel,
                    onEnterPip = onEnterPip,
                )
            }
            if (fullscreenIndex == null && chromeVisible && !pipMode) {
                if (hwWarning) {
                    HwWarningBanner(hwLimit = viewModel.hwLimit)
                }
                if (pendingSwap != null) {
                    SwapBanner()
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val fullTile = fullscreenIndex?.let { tiles.getOrNull(it) }
                if (fullTile != null) {
                    VideoTile(
                        tile = fullTile,
                        viewModel = viewModel,
                        onAdd = onAddUrl,
                        pendingSwap = false,
                        modifier = Modifier.fillMaxSize(),
                        isFullscreen = true,
                        totalTiles = 1,
                        onReplaceVideo = onOpenLibrary,
                    )
                } else {
                    MosaicGrid(
                        tiles = tiles,
                        viewModel = viewModel,
                        onAdd = { onOpenLibrary() },
                        pendingSwap = pendingSwap,
                        onReplaceVideo = onOpenLibrary,
                    )
                }
            }
            if (fullscreenIndex == null && chromeVisible && !pipMode) {
                MosaicControlBar(
                    viewModel = viewModel,
                    synced = synced,
                    audioMode = audioMode,
                    anyPlaying = anyPlaying,
                    autoPip = autoPip,
                    onOpenLibrary = onOpenLibrary,
                    onAddUrl = onAddUrl,
                    onPickFile = { pickFile.launch(arrayOf("video/*")) },
                    onOpenEq = { showEq = true },
                )
            }
        }
        if (fullscreenIndex == null && !chromeVisible && !pipMode) {
            FilledTonalIconButton(
                onClick = { viewModel.toggleChrome() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "Mostrar controles",
                )
            }
        }

        if (showEq && !pipMode) {
            EqDialog(
                presets = viewModel.eqPresets,
                selected = eqPreset,
                onSelect = { viewModel.setEqPreset(it) },
                onDismiss = { showEq = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MosaicTopBar(slots: Int, viewModel: MosaicViewModel, onEnterPip: (() -> Unit)?) {
    Column {
        TopAppBar(
            title = { Text("MagikVLC") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            actions = {
                if (onEnterPip != null) {
                    IconButton(onClick = onEnterPip) {
                        Icon(
                            imageVector = Icons.Filled.PictureInPicture,
                            contentDescription = "Ventana flotante (PiP)",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                IconButton(onClick = { viewModel.toggleChrome() }) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "Ocultar controles",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(1, 2, 4, 6, 9).forEach { n ->
                FilterChip(
                    selected = n == slots,
                    onClick = { viewModel.setSlotCount(n) },
                    label = {
                        Text(
                            text = "$n",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    modifier = Modifier.height(32.dp),
                )
            }
        }
    }
}

@Composable
private fun HwWarningBanner(hwLimit: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Tu dispositivo decodifica por hardware hasta $hwLimit videos a la vez. " +
                "Los tiles extra usarán decodificación por software (más batería).",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SwapBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Selecciona otro tile para intercambiar posiciones.",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MosaicGrid(
    tiles: List<Tile>,
    viewModel: MosaicViewModel,
    onAdd: () -> Unit,
    pendingSwap: Int?,
    onReplaceVideo: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // En vertical (más alto que ancho) preferimos más filas que columnas.
        val portrait = maxHeight > maxWidth
        val (rows, cols) = gridDims(tiles.size, portrait)
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        if (index < tiles.size) {
                            VideoTile(
                        tile = tiles[index],
                        viewModel = viewModel,
                        onAdd = onAdd,
                        pendingSwap = pendingSwap == index,
                        totalTiles = tiles.size,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onReplaceVideo = onReplaceVideo,
                    )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MosaicControlBar(
    viewModel: MosaicViewModel,
    synced: Boolean,
    audioMode: AudioMode,
    anyPlaying: Boolean,
    autoPip: Boolean,
    onOpenLibrary: () -> Unit,
    onAddUrl: () -> Unit,
    onPickFile: () -> Unit,
    onOpenEq: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onOpenLibrary) {
                Icon(Icons.Filled.LibraryBooks, contentDescription = null)
                Text("Biblioteca", modifier = Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onPickFile) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Archivo", modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = onAddUrl) {
                Icon(Icons.Filled.AddLink, contentDescription = null)
                Text("URL", modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = { viewModel.toggleAll() }) {
                Icon(
                    if (anyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Text(
                    if (anyPlaying) "Pausar todo" else "Reproducir todo",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            FilterChip(
                selected = synced,
                onClick = { viewModel.toggleSync() },
                label = { Text("Sync") },
                leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
            )
            FilterChip(
                selected = audioMode == AudioMode.MULTIPLE,
                onClick = { viewModel.toggleAudioMode() },
                label = {
                    Text(if (audioMode == AudioMode.MULTIPLE) "Audio múltiple" else "Audio único")
                },
                leadingIcon = {
                    Icon(
                        if (audioMode == AudioMode.MULTIPLE) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = null,
                    )
                },
            )
            TextButton(onClick = { viewModel.muteAll() }) {
                Icon(Icons.Filled.VolumeOff, contentDescription = null)
                Text("Silenciar", modifier = Modifier.padding(start = 4.dp))
            }
            FilterChip(
                selected = autoPip,
                onClick = { viewModel.setAutoPip(!autoPip) },
                label = { Text("PiP auto") },
                leadingIcon = { Icon(Icons.Filled.PictureInPicture, contentDescription = null) },
            )
            TextButton(onClick = onOpenEq) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Text("EQ", modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = { viewModel.clear() }) {
                Text("Limpiar")
            }
        }
    }
}

private fun gridDims(count: Int, portrait: Boolean = false): Pair<Int, Int> {
    if (count <= 1) return 1 to 1
    if (count == 4) return 2 to 2
    val cols = ceil(sqrt(count.toDouble())).toInt()
    val rows = ceil(count.toDouble() / cols).toInt()
    // En vertical invertimos para tener más filas que columnas (celdas más grandes).
    return if (portrait && cols > rows) cols to rows else rows to cols
}
