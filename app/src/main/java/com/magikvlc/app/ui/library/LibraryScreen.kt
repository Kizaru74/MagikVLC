package com.magikvlc.app.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magikvlc.app.data.LibraryRepository
import com.magikvlc.app.data.DuplicateScanNotifications
import com.magikvlc.app.data.DuplicateScanReportStore
import com.magikvlc.app.data.DuplicateScanWorkRepository
import com.magikvlc.app.data.DuplicateScanWorker
import com.magikvlc.app.data.DuplicateScanUiPolicy
import com.magikvlc.app.data.DuplicateSelectionPolicy
import com.magikvlc.app.data.NotificationPermissionPolicy
import com.magikvlc.app.data.toDomain
import com.magikvlc.app.diagnostics.DiagnosticLog
import com.magikvlc.app.data.M3u
import com.magikvlc.app.data.Playlist
import com.magikvlc.app.data.PlaylistEntry
import com.magikvlc.app.data.PlaylistItem
import com.magikvlc.app.data.FavoriteMatch
import com.magikvlc.app.data.ThumbnailCache
import com.magikvlc.app.data.ThumbnailWarmer
import com.magikvlc.app.data.asVideoItem
import com.magikvlc.app.data.matchFavorites
import com.magikvlc.app.data.resolvePlaylistEntries
import com.magikvlc.app.data.VideosDataSource.DuplicateGroup
import com.magikvlc.app.data.VideosDataSource.DuplicateScanPhase
import com.magikvlc.app.data.VideosDataSource.DuplicateScanProgress
import com.magikvlc.app.data.VideosDataSource.DuplicateScanReport
import com.magikvlc.app.data.VideosDataSource.ProbableGroup
import com.magikvlc.app.data.VideosDataSource.RenameOutcome
import com.magikvlc.app.data.VideoItem
import com.magikvlc.app.data.VideoSort
import com.magikvlc.app.data.VideosDataSource
import com.magikvlc.app.data.formatDuration
import com.magikvlc.app.data.toPlaylistItem
import com.magikvlc.app.data.toVideoItem
import com.magikvlc.app.data.WatchedProgress
import com.magikvlc.app.data.watchedKey
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast

private const val TAB_VIDEOS = 0
private const val TAB_FAVS = 1
private const val TAB_LISTS = 2
private const val TAB_DUPS = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onPlaySingle: (VideoItem) -> Unit,
    onOpenWall: (List<VideoItem>) -> Unit,
    onAddUrl: () -> Unit,
    onAbout: () -> Unit = {},
    replaceSlot: Int? = null,
    onCancelReplace: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { LibraryRepository(context) }
    val duplicateScanWorkRepository = remember { DuplicateScanWorkRepository(context) }
    val duplicateReportStore = remember { DuplicateScanReportStore(context.filesDir) }
    var duplicateWorkInfos by remember { mutableStateOf<List<androidx.work.WorkInfo>?>(null) }
    LaunchedEffect(duplicateScanWorkRepository) {
        duplicateScanWorkRepository.workInfos.collect { infos -> duplicateWorkInfos = infos }
    }
    val duplicateWorkInfo = duplicateWorkInfos
        .orEmpty()
        .filter { DuplicateScanWorkRepository.TAG in it.tags }
        .let { infos ->
            infos.firstOrNull {
                it.state == androidx.work.WorkInfo.State.RUNNING ||
                    it.state == androidx.work.WorkInfo.State.ENQUEUED ||
                    it.state == androidx.work.WorkInfo.State.BLOCKED
            } ?: infos.lastOrNull()
        }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    val sort by repo.sort.collectAsStateWithLifecycle(initialValue = VideoSort.NEWEST)
    val favorites by repo.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteUris by repo.favoritesUris.collectAsStateWithLifecycle(initialValue = emptySet())
    val watchedProgress by repo.watchedProgress.collectAsStateWithLifecycle(initialValue = emptyMap())
    val playlists by repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var favoriteMatch by remember { mutableStateOf(FavoriteMatch(emptyList(), null)) }
    var loading by remember { mutableStateOf(true) }
    var libraryLoadError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var scanGeneration by remember { mutableStateOf(0) }
    var lastRelinkGeneration by remember { mutableStateOf(0) }
    var lastScanAtMs by remember { mutableStateOf(0L) }
    var backgroundRefreshActive by remember { mutableStateOf(false) }
    /** Evita que Favoritos parpadee vacío mientras la caché aún carga. */
    var libraryLoaded by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var tab by rememberSaveable { mutableStateOf(TAB_VIDEOS) }

    var pendingDeleteUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingDeleteFromDuplicates by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<List<VideoItem>?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Pair<VideoItem, Boolean>?>(null) }
    var pendingRename by remember { mutableStateOf<Triple<Uri, String, Boolean>?>(null) }
    var moveTarget by remember { mutableStateOf<List<VideoItem>?>(null) }
    var pendingMove by remember { mutableStateOf<MoveSession?>(null) }
    var moveRunning by remember { mutableStateOf(false) }
    var moveResume by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var deepScan by remember { mutableStateOf(false) }
    var dupReport by remember { mutableStateOf<DuplicateScanReport?>(null) }
    var dupScanning by remember { mutableStateOf(false) }
    var dupProgress by remember { mutableStateOf<DuplicateScanProgress?>(null) }
    var dupScanCancelled by remember { mutableStateOf(false) }
    var duplicateScanNeedsRescan by remember { mutableStateOf(false) }
    var duplicateScanUserCancelled by remember { mutableStateOf(false) }
    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionDenied by remember {
        mutableStateOf(
            NotificationPermissionPolicy.shouldWarn(
                apiLevel = Build.VERSION.SDK_INT,
                granted = Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            ),
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = NotificationPermissionPolicy.shouldWarn(Build.VERSION.SDK_INT, granted)
    }
    var selectedDuplicateUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deletePlaylistTarget by remember { mutableStateOf<Playlist?>(null) }
    var detailPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var addToListItems by remember { mutableStateOf<List<VideoItem>?>(null) }

    DisposableEffect(Unit) {
        onDispose { ThumbnailWarmer.pause(reason = "screen-dispose") }
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    suspend fun reload(
        showRefreshSummary: Boolean = false,
        forceRefresh: Boolean = true,
    ) {
        if (!LibraryRefreshPolicy.canStartRefresh(refreshing)) return
        val startedAt = android.os.SystemClock.elapsedRealtime()
        DiagnosticLog.log(
            "LIBRARY",
            "reload start force=$forceRefresh summary=$showRefreshSummary sort=$sort",
        )
        val previousUris = videos.mapTo(mutableSetOf()) { it.uri.toString() }
        refreshing = true
        ThumbnailWarmer.pause(reason = "library-reload")
        try {
            // La caché permite abrir la biblioteca sin volver a escanear MediaStore.
            // El escaneo fresco queda reservado para Actualizar y operaciones locales.
            val cached = withContext(Dispatchers.IO) {
                if (granted) VideosDataSource.loadCached(context, sort) else null
            }
            if (cached != null) {
                videos = cached
                libraryLoadError = null
                loading = false
            } else {
                loading = true
            }
            DiagnosticLog.log("LIBRARY", "cache loaded count=${cached?.size ?: 0}")
            val shouldScan = LibraryLoadPolicy.mode(
                hasCachedData = cached != null,
                forceRefresh = forceRefresh,
            ) == LibraryLoadPolicy.LoadMode.SCAN
            if (shouldScan) {
                val result = if (granted) {
                    VideosDataSource.loadWithStatus(context, sort)
                } else {
                    VideosDataSource.VideoLoadResult(emptyList())
                }
                val refreshedVideos = result.videos
                libraryLoadError = result.error
                if (result.error == null) {
                    videos = refreshedVideos
                    scanGeneration += 1
                    lastScanAtMs = android.os.SystemClock.elapsedRealtime()
                } else if (cached == null) {
                    videos = emptyList()
                }
                loading = false
                DiagnosticLog.log(
                    "LIBRARY",
                    "MediaStore scan finished count=${refreshedVideos.size} error=${result.error}",
                )
                if (showRefreshSummary && result.error == null) {
                    val diff = LibraryRefreshPolicy.diff(
                        previous = previousUris,
                        current = refreshedVideos.mapTo(mutableSetOf()) { it.uri.toString() },
                    )
                    toast(
                        when {
                            diff.added > 0 || diff.removed > 0 -> {
                                "Biblioteca actualizada · +${diff.added} nuevos · ${diff.removed} eliminados"
                            }
                            else -> "Biblioteca actualizada · ${refreshedVideos.size} videos"
                        }
                    )
                }
            }
            selectedIds.clear()
            if (forceRefresh) {
                dupReport = null
                dupProgress = null
                dupScanCancelled = false
                selectedDuplicateUris = emptySet()
            }
            if (forceRefresh) duplicateScanNeedsRescan = true
            if (forceRefresh) duplicateScanUserCancelled = false
            // La caché abre al instante, pero puede quedar obsoleta si el usuario
            // movió o añadió archivos: se repone con un escaneo real en fondo.
            if (!shouldScan && granted && !backgroundRefreshActive &&
                LibraryLoadPolicy.shouldRefreshInBackground(
                    android.os.SystemClock.elapsedRealtime() - lastScanAtMs,
                )
            ) {
                backgroundRefreshActive = true
                scope.launch {
                    try {
                        val fresh = withContext(Dispatchers.IO) {
                            VideosDataSource.loadWithStatus(context, sort)
                        }
                        if (fresh.error != null) {
                            libraryLoadError = fresh.error
                            DiagnosticLog.log("LIBRARY", "background refresh failed error=${fresh.error}")
                            return@launch
                        }
                        libraryLoadError = null
                        val freshVideos = fresh.videos
                        val freshUris = freshVideos.mapTo(mutableSetOf()) { it.uri.toString() }
                        if (videos.mapTo(mutableSetOf()) { it.uri.toString() } != freshUris) {
                            duplicateScanNeedsRescan = true
                            dupReport = null
                        }
                        videos = freshVideos
                        scanGeneration += 1
                        lastScanAtMs = android.os.SystemClock.elapsedRealtime()
                        DiagnosticLog.log(
                            "LIBRARY",
                            "background refresh finished count=${freshVideos.size}",
                        )
                    } finally {
                        backgroundRefreshActive = false
                    }
                }
            }
        } finally {
            refreshing = false
            DiagnosticLog.log(
                "LIBRARY",
                "reload finished elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms videos=${videos.size}",
            )
        }
    }

    fun removeDeletedFromDuplicateReport(uris: Collection<String>) {
        val deleted = uris.toSet()
        if (deleted.isEmpty()) return
        selectedDuplicateUris = selectedDuplicateUris - deleted
        dupReport = dupReport?.let { report ->
            val exact = report.groups.mapNotNull { group ->
                val remaining = group.items.filter { it.uri.toString() !in deleted }
                if (remaining.size < 2) null
                else group.copy(
                    items = remaining,
                    freedBytes = remaining.drop(1).sumOf { it.sizeBytes },
                )
            }
            val probable = report.probableGroups.mapNotNull { group ->
                val remaining = group.items.filter { it.uri.toString() !in deleted }
                if (remaining.size < 2) null else group.copy(items = remaining)
            }
            report.copy(groups = exact, probableGroups = probable)
        }
    }

    /** Actualiza el nombre de un video dentro del informe para no resetear la pestaña. */
    fun renameInDuplicateReport(uri: String, newName: String) {
        dupReport = dupReport?.let { report ->
            fun fixed(items: List<VideoItem>) = items.map {
                if (it.uri.toString() == uri) it.copy(name = newName) else it
            }
            report.copy(
                groups = report.groups.map { g -> g.copy(items = fixed(g.items)) },
                probableGroups = report.probableGroups.map { g -> g.copy(items = fixed(g.items)) },
            )
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                repo.pruneDeleted(pendingDeleteUris)
                pendingDeleteUris.forEach { ThumbnailCache.removeByUri(context, it) }
                if (pendingDeleteFromDuplicates) removeDeletedFromDuplicateReport(pendingDeleteUris)
                toast("Videos eliminados")
            } else {
                toast("Eliminación cancelada")
            }
            pendingDeleteUris = emptyList()
            pendingDeleteFromDuplicates = false
            reload()
        }
    }

    val renameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingRename
        pendingRename = null
        if (pending == null) return@rememberLauncherForActivityResult
        val (uri, finalName, fromDuplicates) = pending
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                when (val out = VideosDataSource.renameFile(context, uri, finalName, finalName)) {
                    is RenameOutcome.Renamed -> {
                        toast("Video renombrado")
                        if (fromDuplicates) renameInDuplicateReport(uri.toString(), finalName)
                        reload()
                    }
                    is RenameOutcome.Failed -> toast("No se pudo renombrar: ${out.message}")
                    is RenameOutcome.NeedsSystemConfirm -> toast("Operación cancelada")
                }
            } else {
                toast("Renombrado cancelado")
            }
        }
    }

    // Mover: los dos consentimientos del sistema comparten `moveResume`; el
    // driver `runMove` se declara más abajo y nunca se referenciará desde aquí.
    val moveWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> moveResume?.invoke(result.resultCode == Activity.RESULT_OK) }
    val moveDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> moveResume?.invoke(result.resultCode == Activity.RESULT_OK) }

    fun doRename(video: VideoItem, finalName: String, fromDuplicates: Boolean) {
        scope.launch {
            when (val out = VideosDataSource.renameFile(context, video.uri, video.name, finalName)) {
                is RenameOutcome.Renamed -> {
                    toast("Video renombrado")
                    if (fromDuplicates) renameInDuplicateReport(video.uri.toString(), finalName)
                        reload()
                }
                is RenameOutcome.NeedsSystemConfirm -> {
                    pendingRename = Triple(video.uri, finalName, fromDuplicates)
                    renameLauncher.launch(IntentSenderRequest.Builder(out.intentSender).build())
                }
                is RenameOutcome.Failed -> toast("No se pudo renombrar: ${out.message}")
            }
        }
    }


    /**
     * Mueve los videos de la sesión uno por uno; si el sistema pide un
     * consentimiento (escritura o borrado), guarda la sesión, entrega el
     * IntentSender y devuelve el control al `Launcher`, que reanuda con
     * `moveResume`.
     */
    suspend fun runMove(session: MoveSession) {
        var s = session
        while (s.nextIndex < s.items.size) {
            val index = s.nextIndex
            val item = s.items[index]
            when (val step = VideosDataSource.moveSingle(context, item, s.destVolume, s.destPath)) {
                is VideosDataSource.MoveStep.Moved -> {
                    if (step.crossVolume) ThumbnailCache.removeByUri(context, item.uri.toString())
                    s = s.copy(nextIndex = index + 1, moved = s.moved + 1)
                }
                is VideosDataSource.MoveStep.NeedWriteConfirm -> {
                    if (s.writeRetries >= 1) {
                        s = s.copy(nextIndex = index + 1, failed = s.failed + 1)
                    } else {
                        pendingMove = s.copy(nextIndex = index, writeRetries = s.writeRetries + 1)
                        moveResume = { consented ->
                            val saved = pendingMove
                            pendingMove = null
                            moveResume = null
                            if (saved == null) {
                                moveRunning = false
                                toast("Movimiento cancelado")
                            } else if (consented) {
                                scope.launch {
                                    moveRunning = true
                                    runMove(saved)
                                }
                            } else {
                                moveRunning = false
                                toast("Movimiento cancelado")
                            }
                        }
                        moveWriteLauncher.launch(IntentSenderRequest.Builder(step.sender).build())
                        return
                    }
                }
                is VideosDataSource.MoveStep.NeedDeleteConfirm -> {
                    pendingMove = s.copy(nextIndex = index, copiedUri = step.newUri.toString())
                    moveResume = { consented ->
                        val saved = pendingMove
                        pendingMove = null
                        moveResume = null
                        if (saved == null) {
                            moveRunning = false
                            toast("Movimiento cancelado")
                        } else if (consented) {
                            // El sistema ya borró el original; limpiamos la miniatura vieja.
                            val oldUri = saved.items[saved.nextIndex].uri.toString()
                            ThumbnailCache.removeByUri(context, oldUri)
                            scope.launch {
                                moveRunning = true
                                runMove(saved.copy(nextIndex = saved.nextIndex + 1, moved = saved.moved + 1))
                            }
                        } else {
                            moveRunning = false
                            val copy = saved.copiedUri?.let { Uri.parse(it) }
                            if (copy != null) {
                                scope.launch {
                                    VideosDataSource.abandonMove(context, copy)
                                    toast("Movimiento cancelado")
                                }
                            } else {
                                toast("Movimiento cancelado")
                            }
                        }
                    }
                    moveDeleteLauncher.launch(IntentSenderRequest.Builder(step.sender).build())
                    return
                }
                is VideosDataSource.MoveStep.Failed -> {
                    toast("${item.name}: ${step.message}")
                    s = s.copy(nextIndex = index + 1, failed = s.failed + 1)
                }
            }
        }
        val moved = s.moved
        val failed = s.failed
        if (moved > 0 || failed > 0) {
            toast(
                when {
                    failed == 0 -> if (moved == 1) "Video movido" else "$moved videos movidos"
                    else -> "Movidos $moved · $failed sin cambios"
                },
            )
        }
        moveRunning = false
        if (moved > 0) reload()
    }

    fun startMove(items: List<VideoItem>, destVolume: String, destPath: String) {
        if (items.isEmpty()) return
        scope.launch {
            moveRunning = true
            runMove(MoveSession(items = items, destVolume = destVolume, destPath = destPath, nextIndex = 0))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = M3u.readUri(context, uri)
            val items = text?.let { M3u.parse(it) } ?: emptyList()
            if (items.isEmpty()) {
                toast("No se encontraron pistas en el .m3u")
                return@launch
            }
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() } ?: "Importada"
            val created = repo.createPlaylist(name)
            repo.addToPlaylist(created.id, items)
            tab = TAB_LISTS
            toast("${items.size} pistas importadas en “$name”")
        }
    }

    fun doDelete(items: List<VideoItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val fromDuplicates = tab == TAB_DUPS && dupReport != null
            val uris = items.map { it.uri.toString() }
            when (val out = VideosDataSource.deleteUris(context, items.map { it.uri })) {
                is VideosDataSource.DeleteOutcome.Deleted -> {
                    repo.pruneDeleted(uris)
                    items.forEach { ThumbnailCache.removeByUri(context, it.uri.toString()) }
                    if (fromDuplicates) removeDeletedFromDuplicateReport(uris)
                    toast(if (items.size == 1) "Video eliminado" else "${items.size} videos eliminados")
                    reload()
                }
                is VideosDataSource.DeleteOutcome.NeedsSystemConfirm -> {
                    pendingDeleteUris = uris
                    pendingDeleteFromDuplicates = fromDuplicates
                    deleteLauncher.launch(IntentSenderRequest.Builder(out.intentSender).build())
                }
                is VideosDataSource.DeleteOutcome.Partial -> {
                    toast("Eliminados ${out.deleted} de ${out.total}")
                    reload()
                }
                is VideosDataSource.DeleteOutcome.Failed -> toast("No se pudo eliminar: ${out.message}")
            }
        }
    }

    LaunchedEffect(granted, sort) { reload(forceRefresh = false) }
    LaunchedEffect(videos, favorites) {
        // Mientras la pantalla arranca con el estado inicial vacío no hay nada
        // que emparejar: conservar el resultado previo evita el parpadeo.
        if (videos.isNotEmpty()) libraryLoaded = true
        if (videos.isEmpty() && !libraryLoaded) return@LaunchedEffect
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val computed = withContext(Dispatchers.Default) {
            matchFavorites(videos, favorites)
        }
        favoriteMatch = computed
        DiagnosticLog.log(
            "LIBRARY",
            "favorites match videos=${videos.size} favorites=${favorites.size} " +
                "matched=${computed.videos.size} elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
        )
    }
    // Tras un escaneo real reparamos las rutas de las listas fuera del hilo UI.
    LaunchedEffect(scanGeneration, granted) {
        if (!granted || videos.isEmpty() ||
            !LibraryLoadPolicy.shouldRelink(scanGeneration, lastRelinkGeneration)
        ) return@LaunchedEffect
        val generation = scanGeneration
        lastRelinkGeneration = generation
        val startedAt = android.os.SystemClock.elapsedRealtime()
        DiagnosticLog.log("PLAYLIST", "relink start generation=$generation videos=${videos.size}")
        val fixed = withContext(Dispatchers.Default) {
            repo.relinkAllPlaylists(videos)
        }
        DiagnosticLog.log(
            "PLAYLIST",
            "relink finished generation=$generation fixed=$fixed elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
        )
        if (fixed > 0) toast("Listas actualizadas: $fixed ruta(s) reparada(s)")
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(permission)
    }
    LaunchedEffect(tab, granted) {
        selectedIds.clear()
        if (!LibraryPerformancePolicy.shouldWarm(tab, granted)) {
            ThumbnailWarmer.pause(reason = "unsupported-tab")
        }
    }
    LaunchedEffect(tab) {
        if (tab == TAB_DUPS && Build.VERSION.SDK_INT >= 33 && !notificationPermissionAsked) {
            notificationPermissionAsked = true
            val notificationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (NotificationPermissionPolicy.shouldRequest(Build.VERSION.SDK_INT, notificationGranted)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionDenied = false
            }
        }
    }
    LaunchedEffect(duplicateWorkInfo?.id, duplicateWorkInfo?.state, duplicateWorkInfo?.progress) {
        val info = duplicateWorkInfo ?: return@LaunchedEffect
        val state = info.state
        deepScan = DuplicateScanWorkRepository.modeTag(true) in info.tags
        dupScanning = state == androidx.work.WorkInfo.State.ENQUEUED ||
            state == androidx.work.WorkInfo.State.RUNNING ||
            state == androidx.work.WorkInfo.State.BLOCKED
        val phase = info.progress.getString(DuplicateScanWorker.KEY_PHASE)
            ?.let { runCatching { DuplicateScanPhase.valueOf(it) }.getOrNull() }
        dupProgress = if (dupScanning && phase != null) {
            DuplicateScanProgress(
                phase = phase,
                completed = info.progress.getInt(DuplicateScanWorker.KEY_COMPLETED, 0),
                total = info.progress.getInt(DuplicateScanWorker.KEY_TOTAL, 0),
            )
        } else if (dupScanning) {
            DuplicateScanProgress(DuplicateScanPhase.PREPARING, 0, 0)
        } else {
            null
        }
        when (state) {
            androidx.work.WorkInfo.State.SUCCEEDED, androidx.work.WorkInfo.State.FAILED -> {
                dupScanCancelled = false
                val reportId = DuplicateScanUiPolicy.reportIdToLoad(
                    state = state.name,
                    outputReportId = info.outputData.getString(DuplicateScanWorker.KEY_REPORT_ID),
                )
                dupReport = reportId?.let { duplicateReportStore.load(it)?.toDomain() }
                    ?: DuplicateScanReport(
                        groups = emptyList(),
                        scannedCount = info.outputData.getInt(DuplicateScanWorker.KEY_SCANNED_COUNT, 0),
                        candidateRuns = 0,
                        unreadable = 0,
                        error = if (state == androidx.work.WorkInfo.State.FAILED) {
                            "El análisis no terminó correctamente o su reporte no está disponible"
                        } else {
                            "El reporte del análisis no está disponible"
                        },
                    )
            }
            androidx.work.WorkInfo.State.CANCELLED -> {
                dupReport = null
                dupScanCancelled = true
            }
            else -> {
                if (dupScanCancelled) dupScanCancelled = false
            }
        }
    }
    val libraryReadyForDuplicates = !loading && !refreshing && !backgroundRefreshActive && libraryLoadError == null
    LaunchedEffect(
        tab,
        libraryReadyForDuplicates,
        granted,
        videos.size,
        deepScan,
        duplicateScanNeedsRescan,
        duplicateScanUserCancelled,
        duplicateWorkInfos,
        duplicateWorkInfo?.id,
        duplicateWorkInfo?.state,
    ) {
        if (DuplicateScanUiPolicy.shouldAutoStart(
                onDuplicatesTab = tab == TAB_DUPS,
                libraryReady = libraryReadyForDuplicates,
                hasVideoPermission = granted,
                videoCount = videos.size,
                workInfoLoaded = duplicateWorkInfos != null,
                hasWorkRecord = duplicateWorkInfo != null && !duplicateScanNeedsRescan,
                userCancelled = duplicateScanUserCancelled,
            )
        ) {
            if (duplicateScanNeedsRescan) {
                duplicateScanWorkRepository.startFresh(deepScan)
                duplicateScanNeedsRescan = false
            } else {
                duplicateScanWorkRepository.ensureScan(deepScan)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Biblioteca",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = when {
                        !granted -> "Permiso pendiente"
                        refreshing -> "Actualizando biblioteca…"
                        else -> "${videos.size} videos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                enabled = granted && !refreshing,
                onClick = { scope.launch { reload(showRefreshSummary = true) } },
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Actualizar biblioteca",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = { showFilters = true }) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Filtros",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box {
                IconButton(onClick = { showLibraryMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Más opciones",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(
                    expanded = showLibraryMenu,
                    onDismissRequest = { showLibraryMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Añadir URL") },
                        leadingIcon = { Icon(Icons.Filled.AddLink, contentDescription = null) },
                        onClick = {
                            showLibraryMenu = false
                            onAddUrl()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Acerca de") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = {
                            showLibraryMenu = false
                            onAbout()
                        },
                    )
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
        ) {
            Tab(
                selected = tab == TAB_VIDEOS,
                onClick = {
                    DiagnosticLog.log("LIBRARY", "tab change from=$tab to=$TAB_VIDEOS")
                    tab = TAB_VIDEOS
                },
                text = { Text("Videos") },
            )
            Tab(
                selected = tab == TAB_FAVS,
                onClick = {
                    DiagnosticLog.log("LIBRARY", "tab change from=$tab to=$TAB_FAVS")
                    tab = TAB_FAVS
                },
                text = { Text("Favoritos" + if (favoriteUris.isNotEmpty()) " (${favoriteUris.size})" else "") },
            )
            Tab(
                selected = tab == TAB_LISTS,
                onClick = {
                    DiagnosticLog.log("LIBRARY", "tab change from=$tab to=$TAB_LISTS")
                    tab = TAB_LISTS
                },
                text = { Text("Listas" + if (playlists.isNotEmpty()) " (${playlists.size})" else "") },
            )
            Tab(
                selected = tab == TAB_DUPS,
                onClick = {
                    DiagnosticLog.log("LIBRARY", "tab change from=$tab to=$TAB_DUPS")
                    tab = TAB_DUPS
                },
                text = { Text("Duplicados" + if (dupReport?.groups?.isNotEmpty() == true) " (${dupReport!!.groups.size})" else "") },
            )
        }

        if (replaceSlot != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Elige el video para la celda ${replaceSlot + 1}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelReplace) { Text("Cancelar") }
                }
            }
        }

        if (moveRunning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            !granted -> PermissionCard(onRequest = { permissionLauncher.launch(permission) })
            loading && videos.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            tab == TAB_DUPS && libraryLoadError != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No se pudo cargar la biblioteca: $libraryLoadError",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { scope.launch { reload(showRefreshSummary = false) } }) {
                        Text("Reintentar")
                    }
                }
            }
            tab == TAB_DUPS && libraryReadyForDuplicates && videos.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No hay videos en la biblioteca para analizar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            tab == TAB_LISTS -> PlaylistsTab(
                playlists = playlists,
                videos = videos,
                watchedProgress = watchedProgress,
                onOpen = { playlist ->
                    scope.launch(Dispatchers.Default) {
                        val startedAt = android.os.SystemClock.elapsedRealtime()
                        val items = resolvePlaylistEntries(playlist, videos).map { it.asVideoItem() }
                        DiagnosticLog.log(
                            "LIBRARY",
                            "playlist resolve id=${playlist.id} items=${playlist.items.size} " +
                                "resolved=${items.size} elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
                        )
                        withContext(Dispatchers.Main.immediate) {
                            if (items.isNotEmpty()) onOpenWall(items) else toast("La lista está vacía")
                        }
                    }
                },
                onDetail = { detailPlaylist = it },
                onDelete = { deletePlaylistTarget = it },
                onCreate = { showCreatePlaylist = true },
                onImport = { importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*")) },
            )
            tab == TAB_DUPS -> DuplicatesTab(
                report = dupReport,
                scanning = dupScanning,
                progress = dupProgress,
                cancelled = dupScanCancelled,
                notificationWarning = notificationPermissionDenied,
                deepScan = deepScan,
                watchedProgress = watchedProgress,
                onDeepScanChange = { checked ->
                    deepScan = checked
                    dupProgress = null
                    dupScanCancelled = false
                    duplicateScanUserCancelled = false
                    selectedDuplicateUris = emptySet()
                    dupReport = null
                    scope.launch { duplicateScanWorkRepository.enqueue(checked) }
                },
                selectedUris = selectedDuplicateUris,
                onDelete = { items -> showDeleteConfirm = items },
                onToggleSelection = { video ->
                    val uri = video.uri.toString()
                    selectedDuplicateUris = if (uri in selectedDuplicateUris) {
                        selectedDuplicateUris - uri
                    } else {
                        selectedDuplicateUris + uri
                    }
                },
                onClearSelection = { selectedDuplicateUris = emptySet() },
                onSelectDuplicates = { groupItems, selectedItems ->
                    selectedDuplicateUris = DuplicateSelectionPolicy.replaceGroupSelection(
                        currentUris = selectedDuplicateUris,
                        groupUris = groupItems.mapTo(mutableSetOf()) { it.uri.toString() },
                        nextSelectedUris = selectedItems.mapTo(mutableSetOf()) { it.uri.toString() },
                    )
                },
                onRename = { video -> renameVideoTarget = video to true },
                onRescan = {
                    dupProgress = null
                    dupScanCancelled = false
                    dupReport = null
                    duplicateScanUserCancelled = false
                    selectedDuplicateUris = emptySet()
                    duplicateScanNeedsRescan = true
                },
                onCancel = {
                    duplicateScanUserCancelled = true
                    duplicateScanNeedsRescan = false
                    dupScanCancelled = true
                    duplicateScanWorkRepository.cancel()
                },
            )
            else -> {
                AnimatedVisibility(visible = searchOpen || query.isNotBlank()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { new ->
                            query = new
                            selectedIds.clear()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Buscar por nombre") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                query = ""
                                selectedIds.clear()
                                searchOpen = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar búsqueda")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                    )
                }
                LaunchedEffect(favoriteMatch.migrated) {
                    favoriteMatch.migrated?.let { repo.setFavorites(it) }
                }
                val shown = remember(videos, favoriteMatch.videos, tab, query) {
                    val source = if (tab == TAB_FAVS) favoriteMatch.videos else videos
                    if (query.isBlank()) source else source.filter {
                        it.name.contains(query, ignoreCase = true)
                    }
                }
                LaunchedEffect(tab, shown.isNotEmpty(), granted) {
                    if (!shown.isNotEmpty() && LibraryPerformancePolicy.shouldWarm(tab, granted)) {
                        ThumbnailWarmer.pause(reason = "empty-tab")
                    }
                }
                if (shown.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                query.isNotBlank() -> "Sin resultados para “$query”"
                                tab == TAB_FAVS -> "Sin favoritos todavía — marca la ★ en un video"
                                else -> "No hay videos en este dispositivo"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    var showScrollbar by remember { mutableStateOf(false) }
                    var scrollCooldown by remember { mutableStateOf(false) }
                    // Posición por pestaña: sobrevive al ir a Listas/Duplicados y volver.
                    var savedIndex by rememberSaveable(tab) { mutableStateOf(0) }
                    var savedOffset by rememberSaveable(tab) { mutableStateOf(0) }
                    LaunchedEffect(gridState) {
                        snapshotFlow { gridState.isScrollInProgress }
                            .collect { scrolling ->
                                showScrollbar = true
                                if (scrolling) {
                                    scrollCooldown = true
                                } else {
                                    delay(700)
                                    if (!gridState.isScrollInProgress) showScrollbar = false
                                    delay(1300)
                                    if (!gridState.isScrollInProgress) scrollCooldown = false
                                }
                            }
                    }
                    LaunchedEffect(gridState) {
                        snapshotFlow {
                            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            savedIndex = index
                            savedOffset = offset
                        }
                    }
                    LaunchedEffect(tab, shown.isNotEmpty()) {
                        if (shown.isNotEmpty() && (savedIndex > 0 || savedOffset > 0)) {
                            gridState.scrollToItem(
                                savedIndex.coerceIn(0, shown.size - 1),
                                savedOffset,
                            )
                        }
                    }
                    LaunchedEffect(tab, shown, granted, refreshing) {
                        if (!refreshing &&
                            LibraryPerformancePolicy.shouldWarm(tab, granted) &&
                            shown.isNotEmpty()
                        ) {
                            withFrameNanos { }
                            if (refreshing) return@LaunchedEffect
                            val warmItems = LibraryPerformancePolicy.warmItems(tab, granted, shown)
                            ThumbnailWarmer.start(context, warmItems) {
                                gridState.isScrollInProgress || scrollCooldown
                            }
                        }
                    }
                    WarmProgressBar()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 28.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        items(shown, key = { it.id }) { video ->
                            val uriKey = video.uri.toString()
                            VideoCard(
                                video = video,
                                selected = video.id in selectedIds,
                                isFavorite = uriKey in favoriteUris,
                                watched = video.watchedKey()?.let(watchedProgress::get),
                                onToggleFavorite = { scope.launch { repo.toggleFavorite(video) } },
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        if (video.id in selectedIds) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                    } else {
                                        onPlaySingle(video)
                                    }
                                },
                                onLongClick = {
                                    if (video.id in selectedIds) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                },
                            )
                        }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 3.dp),
                        ) {
                            val barAlpha by animateFloatAsState(
                                targetValue = if (showScrollbar) 1f else 0f,
                                animationSpec = tween(durationMillis = 350),
                                label = "scrollbarAlpha",
                            )
                            VlcScrollbarThumb(
                                state = gridState,
                                alpha = barAlpha,
                                onInteract = { interacting -> if (interacting) showScrollbar = true },
                            )
                        }
                    }
                }
                if (selectedIds.isNotEmpty()) {
                    val selectedVideos = shown.filter { it.id in selectedIds }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 6.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "${selectedIds.size}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Button(onClick = { onOpenWall(selectedVideos) }) { Text("En muro") }
                                if (selectedIds.size == 1) {
                                    TextButton(onClick = { renameVideoTarget = selectedVideos.first() to false }) {
                                        Icon(Icons.Filled.Edit, contentDescription = null)
                                        Text("Renombrar", modifier = Modifier.padding(start = 2.dp))
                                    }
                                }
                                TextButton(onClick = { addToListItems = selectedVideos }) { Text("Lista") }
                                TextButton(onClick = { moveTarget = selectedVideos }) { Text("Mover") }
                                TextButton(
                                    onClick = { showDeleteConfirm = selectedVideos },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) { Text("Eliminar") }
                                TextButton(onClick = { selectedIds.clear() }) { Text("✕") }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- Diálogos ----------

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("Ordenar videos") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(VideoSort.entries) { option ->
                        val selected = sort == option
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    repo.setSort(option)
                                    showFilters = false
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFilters = false }) { Text("Cerrar") }
            },
        )
    }

    if (showCreatePlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Nueva lista") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nombre de la lista") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.createPlaylist(name)
                            showCreatePlaylist = false
                            toast("Lista creada")
                        }
                    },
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancelar") } },
        )
    }

    renameTarget?.let { playlist ->
        var name by remember(playlist.id) { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar lista") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.renamePlaylist(playlist.id, name)
                            renameTarget = null
                        }
                    },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } },
        )
    }

    renameVideoTarget?.let { (video, fromDuplicates) ->
        val ext = video.name.substringAfterLast('.', "").takeIf { it.isNotBlank() && it != video.name }
        var base by remember(video.id) { mutableStateOf(video.name.substringBeforeLast('.', video.name)) }
        val finalName = if (ext != null && base.trim().isNotBlank() && !base.trim().endsWith(".$ext", ignoreCase = true)) {
            "${base.trim()}.$ext"
        } else {
            base.trim()
        }
        AlertDialog(
            onDismissRequest = { renameVideoTarget = null },
            title = { Text("Renombrar video") },
            text = {
                Column {
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it },
                        singleLine = true,
                        placeholder = { Text("Nuevo nombre") },
                    )
                    if (finalName.isNotBlank()) {
                        Text(
                            text = "Se guardará como \"$finalName\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = finalName.isNotBlank(),
                    onClick = {
                        renameVideoTarget = null
                        doRename(video, finalName, fromDuplicates)
                    },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameVideoTarget = null }) { Text("Cancelar") } },
        )
    }

    moveTarget?.let { moveItems ->
        MoveToFolderDialog(
            items = moveItems,
            allVideos = videos,
            onConfirm = { volume, path ->
                moveTarget = null
                startMove(moveItems, volume, path)
            },
            onDismiss = { moveTarget = null },
        )
    }

    deletePlaylistTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deletePlaylistTarget = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Eliminar “${playlist.name}”? Los videos no se borran.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deletePlaylist(playlist.id)
                        deletePlaylistTarget = null
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deletePlaylistTarget = null }) { Text("Cancelar") } },
        )
    }

    addToListItems?.let { items ->
        var listQuery by remember { mutableStateOf("") }
        var newListName by remember { mutableStateOf("") }
        val selectedUris = remember(items) { items.map { it.uri.toString() }.toSet() }
        AlertDialog(
            onDismissRequest = { addToListItems = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f),
            title = { Text("Añadir a lista (${items.size})") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = listQuery,
                        onValueChange = { listQuery = it },
                        placeholder = { Text("Buscar lista…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (listQuery.isNotEmpty()) {
                                IconButton(onClick = { listQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            placeholder = { Text("Nueva lista…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            enabled = newListName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    val created = repo.createPlaylist(newListName)
                                    repo.addToPlaylist(created.id, items.map { it.toPlaylistItem() })
                                    addToListItems = null
                                    selectedIds.clear()
                                    toast("Lista “${created.name}” creada con ${items.size} videos")
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Crear lista")
                        }
                    }
                    val filtered = if (listQuery.isBlank()) {
                        playlists
                    } else {
                        playlists.filter { it.name.contains(listQuery, ignoreCase = true) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(span = { GridItemSpan(2) }) {
                            val alreadyFav = items.count { it.uri.toString() in favoriteUris }
                            val allFav = items.isNotEmpty() && alreadyFav == items.size
                            ListBadgeCard(
                                title = "Favoritos",
                                subtitle = when {
                                    allFav -> "Ya están"
                                    alreadyFav > 0 -> "+${items.size - alreadyFav} nuevos"
                                    favoriteUris.isEmpty() -> "Añadir marcados a ★"
                                    else -> "${favoriteUris.size} en total"
                                },
                                icon = Icons.Filled.Star,
                                checked = allFav,
                                enabled = !allFav,
                            ) {
                                scope.launch {
                                    val fresh = items.filter { it.uri.toString() !in favoriteUris }
                                    fresh.forEach { repo.toggleFavorite(it) }
                                    addToListItems = null
                                    selectedIds.clear()
                                    toast(
                                        if (fresh.isEmpty()) {
                                            "Ya estaban en favoritos"
                                        } else {
                                            "Añadidos ${fresh.size} a favoritos"
                                        },
                                    )
                                }
                            }
                        }
                        items(filtered, key = { it.id }) { playlist ->
                            val inList = remember(playlist.id, playlist.items) {
                                playlist.items.map { it.uri }.toSet()
                            }
                            val already = selectedUris.count { it in inList }
                            val allInside = selectedUris.isNotEmpty() && already == selectedUris.size
                            ListBadgeCard(
                                title = playlist.name,
                                subtitle = when {
                                    allInside -> "Ya están"
                                    already > 0 -> "+${selectedUris.size - already} nuevos"
                                    else -> "${playlist.items.size} videos"
                                },
                                icon = Icons.Filled.VideoLibrary,
                                checked = allInside,
                                enabled = !allInside,
                            ) {
                                scope.launch {
                                    val fresh = items.filter { it.uri.toString() !in inList }
                                    repo.addToPlaylist(playlist.id, items.map { it.toPlaylistItem() })
                                    addToListItems = null
                                    selectedIds.clear()
                                    toast(
                                        if (fresh.isEmpty()) {
                                            "Ya estaban en “${playlist.name}”"
                                        } else {
                                            "Añadidos ${fresh.size} nuevos a “${playlist.name}”"
                                        },
                                    )
                                }
                            }
                        }
                        if (filtered.isEmpty() && playlists.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text(
                                    text = "Sin listas con ese nombre",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addToListItems = null }) { Text("Cerrar") } },
        )
    }

    detailPlaylist?.let { playlist ->
        val live = playlists.firstOrNull { it.id == playlist.id } ?: playlist
        val entries = remember(live.id, live.items, videos) { resolvePlaylistEntries(live, videos) }
        val staleMapping = remember(entries) {
            entries.mapNotNull { entry ->
                val current = entry.video?.uri?.toString()
                if (current != null && current != entry.item.uri) entry.item.uri to current else null
            }.toMap()
        }
        LaunchedEffect(live.id, staleMapping) {
            if (staleMapping.isNotEmpty()) repo.relinkPlaylistUris(live.id, staleMapping)
        }
        BackHandler { detailPlaylist = null }
        PlaylistDetailView(
            playlist = live,
            entries = entries,
            watchedProgress = watchedProgress,
            onClose = { detailPlaylist = null },
            onPlayAll = {
                onOpenWall(entries.map { it.asVideoItem() })
                detailPlaylist = null
            },
            onPlayOne = { entry ->
                onOpenWall(listOf(entry.asVideoItem()))
                detailPlaylist = null
            },
            onPlaySelected = { selected ->
                onOpenWall(selected.map { it.asVideoItem() })
                detailPlaylist = null
            },
            onRemove = { entry ->
                scope.launch { repo.removeFromPlaylist(live.id, entry.item.uri) }
            },
            onRemoveSelected = { selected ->
                scope.launch {
                    selected.forEach { repo.removeFromPlaylist(live.id, it.item.uri) }
                }
            },
            onDeleteSelected = { selected ->
                val deletables = selected.mapNotNull { it.video }
                if (deletables.isEmpty()) {
                    toast("Solo disponible para videos del dispositivo")
                } else {
                    showDeleteConfirm = deletables
                }
            },
            onExport = {
                scope.launch {
                    val uri = M3u.saveToDownloads(context, live.name, M3u.export(live.items))
                    toast(if (uri != null) "Exportada a Descargas/MagikVLC" else "No se pudo exportar")
                }
            },
            onRename = {
                detailPlaylist = null
                renameTarget = live
            },
        )
    }

    showDeleteConfirm?.let { items ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar definitivamente") },
            text = {
                Text(
                    if (items.size == 1) "¿Borrar “${items.first().name}” del dispositivo? No se puede deshacer."
                    else "¿Borrar ${items.size} videos del dispositivo? No se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = null
                    doDelete(items)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") } },
        )
    }
}

/** Barra aislada: solo ella se recompone con el progreso del pre-generador. */
@Composable
private fun WarmProgressBar() {
    val progress by ThumbnailWarmer.progress.collectAsStateWithLifecycle()
    val current = progress ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Vistas prioritarias ${current.done}/${current.total}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { current.done.toFloat() / current.total.coerceAtLeast(1) },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { ThumbnailWarmer.cancel() }) { Text("Parar") }
    }
}

@Composable
private fun ListBadgeCard(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = bg,
        contentColor = fg,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VlcScrollbarThumb(
    state: LazyGridState,
    alpha: Float,
    onInteract: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val thumbColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(48.dp)
            .pointerInput(state) {
                detectTapGestures { offset ->
                    onInteract(true)
                    scope.launch { jumpTo(state, offset.y, size.height.toFloat()) }
                }
            }
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { onInteract(true) },
                    onDragCancel = { onInteract(false) },
                    onDragEnd = { onInteract(false) },
                ) { change, _ ->
                    change.consume()
                    onInteract(true)
                    scope.launch { jumpTo(state, change.position.y, size.height.toFloat()) }
                }
            }
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                val info = state.layoutInfo
                val total = info.totalItemsCount
                if (total <= 0) return@drawBehind
                val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                val viewport = info.visibleItemsInfo.size.coerceAtLeast(1)
                val covered = viewport.toFloat() / total
                val minimumThumb = minOf(88.dp.toPx(), size.height)
                val thumbH = (size.height * covered).coerceIn(minimumThumb, size.height)
                val maxTop = size.height - thumbH
                val frac = if (total <= 1) 0f else first.toFloat() / (total - 1).toFloat()
                val top = maxTop * frac
                val thickness = 16.dp.toPx()
                val trackWidth = 3.dp.toPx()
                val left = (size.width - thickness) / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(size.width / 2f - trackWidth / 2f, 0f),
                    size = Size(trackWidth, size.height),
                    cornerRadius = CornerRadius(trackWidth / 2f),
                )
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(left, top),
                    size = Size(thickness, thumbH),
                    cornerRadius = CornerRadius(thickness / 2f),
                )
            },
    )
}

private suspend fun jumpTo(state: LazyGridState, y: Float, height: Float) {
    val total = state.layoutInfo.totalItemsCount
    if (total <= 1) return
    val frac = (y / height).coerceIn(0f, 1f)
    val index = (frac * (total - 1)).roundToInt()
    state.scrollToItem(index)
}

@Composable
private fun DuplicatesTab(
    report: DuplicateScanReport?,
    scanning: Boolean,
    progress: DuplicateScanProgress?,
    cancelled: Boolean,
    notificationWarning: Boolean,
    deepScan: Boolean,
    watchedProgress: Map<String, WatchedProgress>,
    onDeepScanChange: (Boolean) -> Unit,
    selectedUris: Set<String>,
    onDelete: (List<VideoItem>) -> Unit,
    onToggleSelection: (VideoItem) -> Unit,
    onClearSelection: () -> Unit,
    onSelectDuplicates: (List<VideoItem>, List<VideoItem>) -> Unit,
    onRename: (VideoItem) -> Unit,
    onRescan: () -> Unit,
    onCancel: () -> Unit,
) {
    if (cancelled && !scanning && report == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Escaneo cancelado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRescan) { Text("Volver a buscar") }
            }
        }
        return
    }
    if (scanning || report == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (notificationWarning) {
                    Text(
                        text = "Las notificaciones están desactivadas; Android podría ocultar el progreso del panel.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = when (progress?.phase) {
                        DuplicateScanPhase.PREPARING -> "Preparando candidatos…"
                        DuplicateScanPhase.QUICK_HASH -> "Comparando muestras ${progress.completed}/${progress.total}…"
                        DuplicateScanPhase.FULL_HASH -> "Confirmando copias ${progress.completed}/${progress.total}…"
                        null -> "Buscando duplicados…"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comparación profunda", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = deepScan, onCheckedChange = onDeepScanChange)
                }
                if (progress != null && progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.completed.toFloat() / progress.total },
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .padding(top = 10.dp),
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups = report.groups
    val probables = report.probableGroups
    var verifying by remember(report) { mutableStateOf(setOf<String>()) }
    var verdicts by remember(report) { mutableStateOf(mapOf<String, Boolean>()) }
    var compareGroup by remember(report) { mutableStateOf<DuplicateGroup?>(null) }
    val duplicateListState = rememberLazyListState()
    fun groupKey(items: List<VideoItem>): String =
        items.map { it.uri.toString() }.sorted().joinToString(",")
    val selectableVideos = buildList {
        groups.forEach { addAll(it.items) }
        probables.forEach { addAll(it.items) }
    }.associateBy { it.uri.toString() }
    val selectedVideos = selectedUris.mapNotNull { selectableVideos[it] }
    val probableUris = probables.flatMap { it.items }.map { it.uri.toString() }.toSet()
    val selectedProbables = selectedVideos.count { it.uri.toString() in probableUris }
    Column(modifier = Modifier.fillMaxSize()) {
        val stats = buildString {
            append("Escaneados ${report.scannedCount} vídeos")
            if (report.candidateRuns > 0) append(" · ${report.candidateRuns} candidatos por tamaño")
            if (probables.isNotEmpty()) {
                append(" · ${probables.size} probables")
                val byName = probables.count { "nombre" in it.reason }
                val byRes = probables.count { "resolución" in it.reason }
                if (byName > 0) append(" ($byName por nombre")
                if (byRes > 0) {
                    if (byName > 0) append(", ") else append(" (")
                    append("$byRes por resolución")
                }
                if (byName > 0 || byRes > 0) append(")")
            }
            if (report.unreadable > 0) append(" · ${report.unreadable} no legibles (omitidos)")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Comparación profunda",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "También compara copias de tamaño casi igual (±64 KB). Escaneo más lento.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = deepScan,
                onCheckedChange = onDeepScanChange,
                enabled = true,
            )
        }
        if (notificationWarning) {
            Text(
                text = "Las notificaciones están desactivadas; Android podría ocultar el progreso del panel.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        Text(
            text = stats,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        TextButton(onClick = onRescan, modifier = Modifier.align(Alignment.End)) {
            Text("Volver a buscar")
        }
        if (selectedVideos.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = buildString {
                            append("${selectedVideos.size} seleccionados")
                            if (selectedProbables > 0) append(" · $selectedProbables probables")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearSelection) { Text("Limpiar") }
                    Button(onClick = { onDelete(selectedVideos) }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("Borrar", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
        if (report.error != null) {
            Text(
                text = "Error al escanear: ${report.error}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (groups.isEmpty() && probables.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DuplicateScanUiPolicy.noGroupsMessage(report.error),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = duplicateListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (groups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Exactos (${groups.size}): mismo contenido confirmado por hash.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(groups, key = { group -> group.items.first().uri }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            watchedProgress = watchedProgress,
                            selectedUris = selectedUris,
                            onToggleSelection = onToggleSelection,
                            onRename = onRename,
                            onCompare = { compareGroup = group },
                        )
                    }
                }
                if (probables.isNotEmpty()) {
                    item {
                        Text(
                            text = "Probables (${probables.size}): verifica antes de borrar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(probables, key = { group -> groupKey(group.items) }) { group ->
                        val key = groupKey(group.items)
                        ProbableGroupCard(
                            group = group,
                            watchedProgress = watchedProgress,
                            verifying = key in verifying,
                            verdict = verdicts[key],
                            onVerify = {
                                if (key in verifying || key in verdicts) return@ProbableGroupCard
                                verifying = verifying + key
                                scope.launch {
                                    val ok = VideosDataSource.verifyProbableGroup(context, group.items)
                                    verifying = verifying - key
                                    verdicts = verdicts + (key to ok)
                                }
                            },
                            selectedUris = selectedUris,
                            onToggleSelection = onToggleSelection,
                            onRename = onRename,
                        )
                    }
                }
            }
        }
    }
    compareGroup?.let { group ->
        CompareDuplicatesDialog(
            group = group,
            onDismiss = { compareGroup = null },
            onSelectOthers = { keepUri ->
                onSelectDuplicates(group.items, group.items.filter { it.uri.toString() != keepUri })
                compareGroup = null
            },
        )
    }
}

@Composable
private fun DuplicateVideoRow(
    video: VideoItem,
    index: Int,
    metadata: String,
    selected: Boolean,
    watched: WatchedProgress?,
    onToggleSelection: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 54.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            VideoThumb(
                video = video,
                modifier = Modifier.fillMaxSize(),
                showStorageBadge = false,
            )
            WatchedOverlay(watched)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = "${index + 1}. ${video.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Renombrar video",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        StorageBadge(
            storage = video.storage,
            modifier = Modifier.padding(end = 4.dp),
        )
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggleSelection() },
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    watchedProgress: Map<String, WatchedProgress>,
    selectedUris: Set<String>,
    onToggleSelection: (VideoItem) -> Unit,
    onRename: (VideoItem) -> Unit,
    onCompare: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${group.items.size} copias",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "liberar ${formatBytes(group.freedBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCompare, modifier = Modifier.align(Alignment.End)) {
                Text("Comparar y conservar")
            }
            group.items.forEachIndexed { i, video ->
                DuplicateVideoRow(
                    video = video,
                    index = i,
                    metadata = (video.relativePath.ifBlank { "Memoria" }) + " · " + formatBytes(video.sizeBytes),
                    selected = video.uri.toString() in selectedUris,
                    watched = video.watchedKey()?.let(watchedProgress::get),
                    onToggleSelection = { onToggleSelection(video) },
                    onRename = { onRename(video) },
                )
            }
        }
    }
}

@Composable
private fun ProbableGroupCard(
    group: ProbableGroup,
    watchedProgress: Map<String, WatchedProgress>,
    verifying: Boolean,
    verdict: Boolean?,
    onVerify: () -> Unit,
    selectedUris: Set<String>,
    onToggleSelection: (VideoItem) -> Unit,
    onRename: (VideoItem) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${group.items.size} posibles",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = group.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onVerify, enabled = !verifying && verdict == null) {
                    Text(if (verifying) "Verificando…" else "Verificar")
                }
            }
            if (verdict != null) {
                Text(
                    text = if (verdict) {
                        "Verificado: mismo contenido ✓"
                    } else {
                        "No se pudo confirmar que sean el mismo video"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (verdict) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            group.items.forEachIndexed { i, video ->
                DuplicateVideoRow(
                    video = video,
                    index = i,
                    metadata = formatDuration(video.durationMs) + " · " +
                        (video.relativePath.ifBlank { "Memoria" }) + " · " +
                        formatBytes(video.sizeBytes),
                    selected = video.uri.toString() in selectedUris,
                    watched = video.watchedKey()?.let(watchedProgress::get),
                    onToggleSelection = { onToggleSelection(video) },
                    onRename = { onRename(video) },
                )
            }
        }
    }
}

@Composable
private fun CompareDuplicatesDialog(
    group: DuplicateGroup,
    onDismiss: () -> Unit,
    onSelectOthers: (String) -> Unit,
) {
    var keepUri by remember(group) { mutableStateOf(group.items.first().uri.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        title = { Text("Conservar una copia") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Elige la copia que quieres conservar. Las demás se añadirán a la selección para revisarlas antes de borrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                group.items.forEach { video ->
                    val uri = video.uri.toString()
                    val selected = uri == keepUri
                    Surface(
                        onClick = { keepUri = uri },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 144.dp, height = 81.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                VideoThumb(
                                    video = video,
                                    modifier = Modifier.fillMaxSize(),
                                    showStorageBadge = false,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        append(formatDuration(video.durationMs))
                                        append(" · ")
                                        append(formatBytes(video.sizeBytes))
                                        if (video.width > 0 && video.height > 0) {
                                            append(" · ${video.width}×${video.height}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = video.relativePath.ifBlank { "Memoria" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                StorageBadge(storage = video.storage, modifier = Modifier.padding(top = 4.dp))
                            }
                            RadioButton(selected = selected, onClick = { keepUri = uri })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelectOthers(keepUri) }) {
                Text("Seleccionar las demás")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

/** Sesión de traslado: guarda progreso para reanudar tras consentimiento del SO. */
private data class MoveSession(
    val items: List<VideoItem>,
    val destVolume: String,
    val destPath: String,
    val nextIndex: Int,
    val moved: Int = 0,
    val failed: Int = 0,
    val writeRetries: Int = 0,
    val copiedUri: String? = null,
)

private data class MoveFolderDest(
    val volume: String,
    val volumeLabel: String,
    val relativePath: String,
    val count: Int,
)

private fun volumeLabelFor(volume: String): String =
    if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY || volume == "external_primary") {
        "Memoria interna"
    } else {
        "Tarjeta SD"
    }

@Composable
private fun MoveToFolderDialog(
    items: List<VideoItem>,
    allVideos: List<VideoItem>,
    onConfirm: (destVolume: String, relativePath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val supportsMove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val destinations = remember(allVideos) {
        allVideos
            .map { video -> VideosDataSource.sourceVolumeOf(video) to video.relativePath.trimEnd('/') }
            .filter { (_, path) -> path.isNotBlank() }
            .groupBy { it.first }
            .toSortedMap(compareBy { volume -> volume != MediaStore.VOLUME_EXTERNAL_PRIMARY })
            .map { (volume, entries) ->
                val label = volumeLabelFor(volume)
                entries
                    .map { (_, path) -> path }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }
                    .map { path ->
                        MoveFolderDest(
                            volume = volume,
                            volumeLabel = label,
                            relativePath = path,
                            count = allVideos.count { video ->
                                VideosDataSource.sourceVolumeOf(video) == volume &&
                                    video.relativePath.trimEnd('/') == path
                            },
                        )
                    }
            }
    }

    var selectedDest by remember { mutableStateOf<MoveFolderDest?>(null) }
    var newFolderVolume by remember { mutableStateOf<String?>(null) }
    var newFolderName by remember { mutableStateOf("") }

    val chosenVolume = selectedDest?.volume ?: newFolderVolume
    val dest = selectedDest
    val sanitizedNewName = newFolderName.trim().replace(Regex("[\\\\/]+"), " ").trim()
    val chosenPath = when {
        dest != null -> dest.relativePath
        newFolderVolume != null && sanitizedNewName.isNotEmpty() -> sanitizedNewName
        else -> null
    }
    val alreadyThere = chosenPath != null && chosenVolume != null && items.all { item ->
        VideosDataSource.sourceVolumeOf(item) == chosenVolume &&
            item.relativePath.trimEnd('/') == chosenPath
    }
    val canConfirm = supportsMove && chosenPath != null && chosenVolume != null && !alreadyThere

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        title = { Text(if (items.size == 1) "Mover video" else "Mover ${items.size} videos") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!supportsMove) {
                    Text(
                        text = "Mover archivos requiere Android 10 o más",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (alreadyThere) {
                    Text(
                        text = "Los videos ya están en esa carpeta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (section in destinations) {
                    val volume = section.firstOrNull()?.volume ?: continue
                    Text(
                        text = section.firstOrNull()?.volumeLabel.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    section.forEach { dest ->
                        val isCurrent = items.all { item ->
                            VideosDataSource.sourceVolumeOf(item) == dest.volume &&
                                item.relativePath.trimEnd('/') == dest.relativePath
                        }
                        Surface(
                            onClick = {
                                selectedDest = dest
                                newFolderVolume = null
                            },
                            enabled = !isCurrent,
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else if (selectedDest == dest) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (selectedDest == dest) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedDest == dest,
                                    onClick = null,
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                    Text(
                                        text = dest.relativePath.substringAfterLast('/'),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = buildString {
                                            append(dest.relativePath)
                                            append(" · ${dest.count} videos")
                                            if (isCurrent) append(" · ubicación actual")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            newFolderVolume = volume
                            selectedDest = null
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = if (newFolderVolume == volume) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Nueva carpeta en ${section.firstOrNull()?.volumeLabel.orEmpty()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (newFolderVolume == volume) {
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    singleLine = true,
                                    placeholder = { Text("Nombre de la carpeta") },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(chosenVolume!!, chosenPath!!)
                },
            ) { Text("Mover aquí") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    videos: List<VideoItem>,
    watchedProgress: Map<String, WatchedProgress>,
    onOpen: (Playlist) -> Unit,
    onDetail: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    val videosByUri = remember(videos) { videos.associateBy { it.uri.toString() } }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Nueva lista", modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = onImport) { Text("Importar .m3u") }
        }
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Sin listas — crea una o importa un .m3u",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val cover = remember(playlist.id, playlist.items, videosByUri) {
                        playlist.items.mapNotNull { videosByUri[it.uri] }.firstOrNull()
                    }
                    Card(
                        onClick = { onDetail(playlist) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                 if (cover != null) {
                                     VideoThumb(
                                         video = cover,
                                         modifier = Modifier.fillMaxSize(),
                                     )
                                     WatchedOverlay(cover.watchedKey()?.let(watchedProgress::get))
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.VideoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).size(30.dp),
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                                ) {
                                    Text(
                                        text = "${playlist.items.size}",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${playlist.items.size} videos · ${dateOf(playlist.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(onClick = { onOpen(playlist) }) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Abrir en muro",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                IconButton(onClick = { onDelete(playlist) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Eliminar lista",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    entries: List<PlaylistEntry>,
    watchedProgress: Map<String, WatchedProgress>,
    onClose: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayOne: (PlaylistEntry) -> Unit,
    onPlaySelected: (List<PlaylistEntry>) -> Unit,
    onRemove: (PlaylistEntry) -> Unit,
    onRemoveSelected: (List<PlaylistEntry>) -> Unit,
    onDeleteSelected: (List<PlaylistEntry>) -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
) {
    val knownDuration = entries.mapNotNull { it.video?.durationMs?.takeIf { duration -> duration > 0 } }.sum()
    var selectedUris by remember(playlist.id) { mutableStateOf(setOf<String>()) }
    val selecting = selectedUris.isNotEmpty()
    val selectedEntries = remember(entries, selectedUris) {
        entries.filter { it.item.uri in selectedUris }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selecting) {
                        "${selectedUris.size} seleccionados"
                    } else if (knownDuration > 0) {
                        "${entries.size} videos · ${formatDuration(knownDuration)}"
                    } else {
                        "${entries.size} videos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (selecting) {
                TextButton(onClick = {
                    selectedUris = if (selectedUris.size == entries.size) {
                        emptySet()
                    } else {
                        entries.map { it.item.uri }.toSet()
                    }
                }) {
                    Text(if (selectedUris.size == entries.size) "Nada" else "Todo")
                }
            }
            IconButton(onClick = onPlayAll, enabled = entries.isNotEmpty()) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir todo",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onExport) { Text(".m3u") }
            TextButton(onClick = onRename) { Text("Renombrar") }
        }
        if (entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Vacía — añade videos desde la biblioteca.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.item.uri }) { entry ->
                    val selected = entry.item.uri in selectedUris
                    PlaylistGridCell(
                        entry = entry,
                        watched = entry.video?.watchedKey()?.let(watchedProgress::get),
                        selected = selected,
                        selecting = selecting,
                        onToggleSelect = {
                            selectedUris = if (selected) {
                                selectedUris - entry.item.uri
                            } else {
                                selectedUris + entry.item.uri
                            }
                        },
                        onPlay = { onPlayOne(entry) },
                        onRemove = { onRemove(entry) },
                    )
                }
            }
            if (selecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "${selectedUris.size}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Button(onClick = { onPlaySelected(selectedEntries) }) { Text("En muro") }
                            TextButton(onClick = { onRemoveSelected(selectedEntries) }) { Text("Quitar") }
                            TextButton(
                                onClick = {
                                    onDeleteSelected(selectedEntries)
                                    selectedUris = emptySet()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text("Eliminar") }
                            TextButton(onClick = { selectedUris = emptySet() }) { Text("✕") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistGridCell(
    entry: PlaylistEntry,
    watched: WatchedProgress?,
    selected: Boolean,
    selecting: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val video = entry.video
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.combinedClickable(
            onClick = { if (selecting) onToggleSelect() else onPlay() },
            onLongClick = onToggleSelect,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (video != null) {
                    VideoThumb(
                        video = video,
                        modifier = Modifier.fillMaxSize(),
                    )
                    WatchedOverlay(watched)
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    ) {
                        Text(
                            text = formatDuration(video.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).size(30.dp),
                    )
                }
                if (selected) {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Quitar de la lista",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Text(
                text = entry.item.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

private fun dateOf(millis: Long): String = runCatching {
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
}.getOrDefault("")

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Necesito acceso a tus videos",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "La biblioteca escanea MediaStore (solo lectura). No subimos nada.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            Button(onClick = onRequest) {
                Text("Conceder acceso")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    video: VideoItem,
    selected: Boolean,
    isFavorite: Boolean,
    watched: WatchedProgress?,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .aspectRatio(0.95f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium),
        ) {
            VideoThumb(
                video = video,
                modifier = Modifier.fillMaxSize(),
            )
            WatchedOverlay(watched)
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp).size(40.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.8f),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            ) {
                Text(
                    text = formatDuration(video.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (selected) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
        Text(
            text = video.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (video.sizeBytes > 0 || video.height > 0) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (video.sizeBytes > 0) {
                    Chip(text = formatBytes(video.sizeBytes))
                }
                if (video.height > 0) {
                    Chip(text = "${video.height}p")
                }
                if (video.mimeType.isNotBlank()) {
                    val codec = video.mimeType.substringAfterLast('/').uppercase()
                        .let { if (it.length <= 5) it else "" }
                    if (codec.isNotBlank()) Chip(text = codec)
                }
            }
        }
    }
}

/** Barra fina tipo VLC y check cuando el video se completó. Debe vivir dentro de un Box de miniatura. */
@Composable
private fun BoxScope.WatchedOverlay(progress: WatchedProgress?) {
    val watched = progress ?: return
    if (watched.fraction > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(watched.fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
    if (watched.completed) {
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Visto",
                tint = Color.White,
                modifier = Modifier.padding(3.dp).size(14.dp),
            )
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
