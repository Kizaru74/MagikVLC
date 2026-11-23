package com.magikvlc.app.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.coroutines.coroutineContext

@Serializable
private data class CachedVideo(
    val id: Long,
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val relativePath: String,
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val storage: String = StorageKind.UNKNOWN.name,
    val volumeName: String? = null,
    val modifiedSec: Long = 0L,
)

@Serializable
private data class CachedScan(
    val videos: List<CachedVideo> = emptyList(),
)

@Serializable
private data class DuplicateHashCache(
    val entries: Map<String, CachedDuplicateHash> = emptyMap(),
)

@Serializable
private data class CachedDuplicateHash(
    val sizeBytes: Long,
    val modifiedSec: Long,
    val quickHash: String,
    val fullHash: String? = null,
)

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val relativePath: String,
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val storage: StorageKind = StorageKind.UNKNOWN,
    val volumeName: String? = null,
    val modifiedSec: Long = 0L,
)

enum class StorageKind {
    INTERNAL,
    SD,
    UNKNOWN,
}

object VideosDataSource {

    private val cacheJson = Json { ignoreUnknownKeys = true }

    private val REGEX_ACCENTS = Regex("\\p{M}+")
    private val REGEX_ANDROID_PREFIX = Regex("^(vid|video|screenshot)[_-]?\\d{6,}(?:[_-]\\d{4,6})?[_-]?")
    private val REGEX_INDEX = Regex("\\(\\s*\\d+\\s*\\)")
    private val REGEX_COPY = Regex("\\b(copy|copia)(?:\\s*\\d+)?\\b")
    private val REGEX_QUALITY = Regex("\\b(?:\\d{3,4}p|4k|8k|uhd|fhd|hd)\\b")
    private val REGEX_RELEASE = Regex(
        "\\b(?:web[- ]?dl|web[- ]?rip|bluray|blu[- ]?ray|brrip|bdrip|hdrip|dvdrip|x264|x265|h264|h265|hevc|avc|av1|aac|ac3|dts)\\b",
    )
    private val REGEX_SEPARATORS = Regex("[._\\-]+")
    private val REGEX_SPACES = Regex("\\s+")
    @Volatile
    private var memoryCache: List<VideoItem>? = null

    private fun cacheFile(context: Context): File =
        File(context.cacheDir, "video_scan_v1.json")

    private fun duplicateHashCacheFile(context: Context): File =
        File(context.cacheDir, "duplicate_hashes_v1.json")

    /** Caché del último escaneo: biblioteca al instante en la siguiente apertura. */
    fun loadCached(context: Context, sort: VideoSort = VideoSort.NEWEST): List<VideoItem>? {
        val cached = memoryCache ?: run {
            val file = cacheFile(context)
            if (!file.exists()) return null
            runCatching {
                cacheJson.decodeFromString<CachedScan>(file.readText())
                    .videos
                    .map { it.toItem() }
            }.getOrNull()?.also { memoryCache = it }
        }
        return cached?.let { sortItems(it, sort) }
    }

    private fun writeCache(context: Context, items: List<VideoItem>) {
        runCatching {
            cacheFile(context).writeText(
                cacheJson.encodeToString(
                    CachedScan(items.map { it.toCached() })
                )
            )
        }
    }

    private fun loadDuplicateHashCache(context: Context): MutableMap<String, CachedDuplicateHash> =
        runCatching {
            cacheJson.decodeFromString<DuplicateHashCache>(duplicateHashCacheFile(context).readText())
                .entries
                .toMutableMap()
        }.getOrDefault(mutableMapOf())

    private fun writeDuplicateHashCache(context: Context, entries: Map<String, CachedDuplicateHash>) {
        runCatching {
            duplicateHashCacheFile(context).writeText(
                cacheJson.encodeToString(DuplicateHashCache(entries))
            )
        }
    }

    private fun CachedVideo.toItem(): VideoItem = VideoItem(
        id = id,
        uri = Uri.parse(uri),
        name = name,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        relativePath = relativePath,
        mimeType = mimeType,
        width = width,
        height = height,
        storage = runCatching { StorageKind.valueOf(storage) }.getOrDefault(StorageKind.UNKNOWN),
        volumeName = volumeName,
        modifiedSec = modifiedSec,
    )

    private fun VideoItem.toCached(): CachedVideo = CachedVideo(
        id = id,
        uri = uri.toString(),
        name = name,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        relativePath = relativePath,
        mimeType = mimeType,
        width = width,
        height = height,
        storage = storage.name,
        volumeName = volumeName,
        modifiedSec = modifiedSec,
    )

    private data class LoadedRow(val item: VideoItem, val modifiedSec: Long)

    data class VideoLoadResult(val videos: List<VideoItem>, val error: String? = null)

    suspend fun load(context: Context, sort: VideoSort = VideoSort.NEWEST): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = scan(context, sort)
        memoryCache = result
        writeCache(context, result)
        result
    }

    /** Library load variant that never treats a failed/partial MediaStore query as an empty library. */
    suspend fun loadWithStatus(context: Context, sort: VideoSort = VideoSort.NEWEST): VideoLoadResult =
        withContext(Dispatchers.IO) {
            try {
                val items = scan(context, sort, failOnQueryError = true)
                memoryCache = items
                writeCache(context, items)
                VideoLoadResult(items)
            } catch (error: Exception) {
                VideoLoadResult(emptyList(), error.message ?: "No se pudo consultar MediaStore")
            }
        }

    /** A duplicate worker must distinguish an actually empty library from a failed MediaStore query. */
    suspend fun loadForDuplicateScan(context: Context, sort: VideoSort = VideoSort.NEWEST): List<VideoItem> =
        loadWithStatus(context, sort).let { result ->
            result.error?.let { throw IllegalStateException(it) }
            result.videos
        }

    private fun scan(context: Context, sort: VideoSort, failOnQueryError: Boolean = false): List<VideoItem> {
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
        } else {
            setOf(MediaStore.VOLUME_EXTERNAL)
        }
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Video.Media.RELATIVE_PATH)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.DATA)
            }
        }.toTypedArray()
        val rows = mutableListOf<LoadedRow>()
        try {
            for (volumeName in volumes) {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(volumeName)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val cursor = context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    null
                )
                if (cursor == null && failOnQueryError) {
                    throw IllegalStateException("MediaStore devolvió una consulta vacía para $volumeName")
                }
                cursor?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    // RELATIVE_PATH solo existe en API 29+; en older es -1.
                    val pathCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val modifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        val storage = storageFor(volumeName, dataPath)
                        val modified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                        rows += LoadedRow(
                            item = VideoItem(
                                id = id,
                                uri = uri,
                                name = cursor.getString(nameCol) ?: "video_$id",
                                durationMs = cursor.getLong(durCol),
                                sizeBytes = cursor.getLong(sizeCol),
                                relativePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "",
                                mimeType = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else "",
                                width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                                height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                                storage = storage,
                                volumeName = volumeName.takeIf { it.isNotBlank() },
                                modifiedSec = modified,
                            ),
                            modifiedSec = modified,
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            if (failOnQueryError) throw error
        }
        return sortRows(rows, sort).map { it.item }
    }

    private fun storageFor(volumeName: String, dataPath: String): StorageKind {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return when (volumeName) {
                MediaStore.VOLUME_EXTERNAL_PRIMARY -> StorageKind.INTERNAL
                MediaStore.VOLUME_EXTERNAL -> StorageKind.UNKNOWN
                else -> if (volumeName.isBlank()) StorageKind.UNKNOWN else StorageKind.SD
            }
        }
        if (dataPath.isBlank()) return StorageKind.UNKNOWN
        val primary = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull().orEmpty()
        if (primary.isNotBlank() && (dataPath == primary || dataPath.startsWith("$primary/"))) {
            return StorageKind.INTERNAL
        }
        val segments = dataPath.split('/').filter { it.isNotBlank() }
        if (segments.size >= 2 && segments[0] == "storage" && segments[1] != "emulated") {
            return StorageKind.SD
        }
        return StorageKind.UNKNOWN
    }

    private fun sortRows(rows: List<LoadedRow>, sort: VideoSort): List<LoadedRow> {
        val byName = compareBy<LoadedRow> { it.item.name.lowercase() }
        return when (sort) {
            VideoSort.NEWEST -> rows.sortedWith(compareByDescending<LoadedRow> { it.modifiedSec }.thenBy { it.item.name.lowercase() })
            VideoSort.OLDEST -> rows.sortedWith(compareBy<LoadedRow> { it.modifiedSec }.thenBy { it.item.name.lowercase() })
            VideoSort.NAME -> rows.sortedWith(byName)
            VideoSort.LONGEST -> rows.sortedWith(compareByDescending<LoadedRow> { it.item.durationMs }.thenBy { it.item.name.lowercase() })
            VideoSort.SHORTEST -> rows.sortedWith(compareBy<LoadedRow> { it.item.durationMs }.thenBy { it.item.name.lowercase() })
        }
    }

    private fun sortItems(items: List<VideoItem>, sort: VideoSort): List<VideoItem> {
        val name = { i: VideoItem -> i.name.lowercase() }
        return when (sort) {
            VideoSort.NEWEST -> items.sortedWith(compareByDescending<VideoItem> { it.modifiedSec }.thenBy(name))
            VideoSort.OLDEST -> items.sortedWith(compareBy<VideoItem> { it.modifiedSec }.thenBy(name))
            VideoSort.NAME -> items.sortedWith(compareBy(name))
            VideoSort.LONGEST -> items.sortedWith(compareByDescending<VideoItem> { it.durationMs }.thenBy(name))
            VideoSort.SHORTEST -> items.sortedWith(compareBy<VideoItem> { it.durationMs }.thenBy(name))
        }
    }

    suspend fun loadThumbnail(
        context: Context,
        item: VideoItem,
        size: Size = Size(384, 384),
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(item.uri, size, null)
            } else {
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    item.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null,
                )
            }
        }.getOrNull()
    }

    data class DuplicateGroup(
        val items: List<VideoItem>,
        val freedBytes: Long,
    )

    data class ProbableGroup(
        val items: List<VideoItem>,
        val reason: String,
    )

    data class DuplicateScanReport(
        val groups: List<DuplicateGroup>,
        val scannedCount: Int,
        val candidateRuns: Int,
        val unreadable: Int,
        val error: String? = null,
        val probableGroups: List<ProbableGroup> = emptyList(),
    )

    enum class DuplicateScanPhase { PREPARING, QUICK_HASH, FULL_HASH }

    data class DuplicateScanProgress(
        val phase: DuplicateScanPhase,
        val completed: Int,
        val total: Int,
    )

    private const val SAMPLE_BYTES = 256 * 1024

    /** Margen de tamaño para el modo profundo: atrapa re-multiplexados. */
    private const val DEEP_SIZE_TOLERANCE = 64L * 1024

    /**
     * Agrupa candidatos por tamaño. Con tolerance = 0 exige tamaño idéntico;
     * con tolerance > 0 encadena archivos cuyos tamaños caben en la ventana,
     * lo que permite comparar copias con metadatos/re-encode casi idénticos.
     */
    private fun sizeCandidateRuns(
        candidates: List<VideoItem>,
        tolerance: Long,
    ): List<List<VideoItem>> {
        val sorted = candidates.sortedBy { it.sizeBytes }
        val runs = mutableListOf<MutableList<VideoItem>>()
        for (video in sorted) {
            val last = runs.lastOrNull()
            if (last != null && video.sizeBytes - last.last().sizeBytes <= tolerance) {
                last += video
            } else {
                runs += mutableListOf(video)
            }
        }
        return runs.filter { it.size > 1 }
    }

    /**
     * Normaliza un nombre de archivo para comparación: quita extensión,
     * convierte a minúsculas, elimina prefijos comunes de Android/copia.
     * Las regex están precompiladas porque favoritos y playlists la invocan
     * por cada par candidato durante el re-enlace.
     */
    internal fun normalizeName(name: String): String {
        val base = name.substringBeforeLast('.', name).lowercase()
        val withoutAccents = Normalizer.normalize(base, Normalizer.Form.NFD)
            .replace(REGEX_ACCENTS, "")
        return withoutAccents
            .replace(REGEX_ANDROID_PREFIX, "")
            .replace(REGEX_INDEX, "")
            .replace(REGEX_COPY, "")
            .replace(REGEX_QUALITY, "")
            .replace(REGEX_RELEASE, "")
            .replace(REGEX_SEPARATORS, " ")
            .replace(REGEX_SPACES, " ")
            .trim()
    }

    /**
     * Detección escalonada para bibliotecas grandes:
     * 1. Candidatos por tamaño (sin I/O): una copia binaria exacta conserva tamaño.
     * 2. Hash de inicio/medio/final solo para candidatos del mismo tamaño.
     * 3. SHA-256 completo solo para coincidencias de las muestras.
     *
     * Los hashes se reutilizan mientras URI, tamaño y fecha de modificación no cambien.
     */
    suspend fun findDuplicates(
        context: Context,
        items: List<VideoItem>,
        deepScan: Boolean = false,
        onProgress: suspend (DuplicateScanProgress) -> Unit = {},
    ): DuplicateScanReport =
        withContext(Dispatchers.IO) {
            val candidates = items.filter { it.sizeBytes > 0L }
            val currentUris = candidates.map { it.uri.toString() }.toSet()
            val cachedHashes = loadDuplicateHashCache(context)
                .filterKeys { it in currentUris }
                .toMutableMap()
            var unreadable = 0
            try {
                val sizeCandidates = sizeCandidateRuns(
                    candidates,
                    tolerance = if (deepScan) DEEP_SIZE_TOLERANCE else 0L,
                )
                    .flatten()
                    .distinctBy { it.uri.toString() }
                onProgress(DuplicateScanProgress(DuplicateScanPhase.PREPARING, 0, sizeCandidates.size))

                val quickMatches = mutableListOf<Pair<VideoItem, String>>()
                sizeCandidates.forEachIndexed { index, video ->
                    coroutineContext.ensureActive()
                    val key = video.uri.toString()
                    val cached = cachedHashes[key]
                        ?.takeIf { it.sizeBytes == video.sizeBytes && it.modifiedSec == video.modifiedSec }
                    val quick = cached?.quickHash ?: quickFingerprint(context, video.uri, video.sizeBytes)
                    if (quick == null) {
                        unreadable++
                    } else {
                        cachedHashes[key] = CachedDuplicateHash(
                            sizeBytes = video.sizeBytes,
                            modifiedSec = video.modifiedSec,
                            quickHash = quick,
                            fullHash = cached?.fullHash,
                        )
                        quickMatches += video to quick
                    }
                    onProgress(DuplicateScanProgress(DuplicateScanPhase.QUICK_HASH, index + 1, sizeCandidates.size))
                }

                val fullCandidates = quickMatches
                    .groupBy { (_, hash) -> hash }
                    .values
                    .filter { it.size > 1 }
                    .flatten()
                val fullMatches = mutableListOf<Pair<VideoItem, String>>()
                fullCandidates.forEachIndexed { index, (video, _) ->
                    coroutineContext.ensureActive()
                    val key = video.uri.toString()
                    val cached = cachedHashes[key]
                        ?.takeIf { it.sizeBytes == video.sizeBytes && it.modifiedSec == video.modifiedSec }
                    val full = cached?.fullHash ?: fullContentHash(context, video.uri)
                    if (full == null) {
                        unreadable++
                    } else {
                        cachedHashes[key] = CachedDuplicateHash(
                            sizeBytes = video.sizeBytes,
                            modifiedSec = video.modifiedSec,
                            quickHash = cached?.quickHash.orEmpty(),
                            fullHash = full,
                        )
                        fullMatches += video to full
                    }
                    onProgress(DuplicateScanProgress(DuplicateScanPhase.FULL_HASH, index + 1, fullCandidates.size))
                }

                val groups = fullMatches
                    .groupBy { it.second }
                    .values
                    .filter { it.size > 1 }
                    .map { matches ->
                        val members = matches.map { it.first }
                        DuplicateGroup(
                            items = members,
                            freedBytes = members.drop(1).sumOf { it.sizeBytes },
                        )
                    }
                android.util.Log.d(
                    "MagikVLC",
                    "duplicates: videos=${candidates.size} candidatos=${sizeCandidates.size} " +
                        "grupos=${groups.size} noLegibles=$unreadable"
                )
                val exactUris = groups.flatMap { it.items.map { video -> video.uri.toString() } }.toSet()
                DuplicateScanReport(
                    groups = groups,
                    scannedCount = items.size,
                    candidateRuns = sizeCandidates.size,
                    unreadable = unreadable,
                    probableGroups = clusterProbables(items.filter { it.uri.toString() !in exactUris }),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MagikVLC", "findDuplicates falló", e)
                DuplicateScanReport(
                    groups = emptyList(),
                    scannedCount = items.size,
                    candidateRuns = 0,
                    unreadable = unreadable,
                    error = e.message ?: "Error desconocido al escanear",
                )
            } finally {
                // También conserva el trabajo ya hecho si el usuario cancela.
                writeDuplicateHashCache(context, cachedHashes)
            }
        }

    /** Hash rápido: muestras de inicio, medio y final usando seek real del descriptor. */
    private fun quickFingerprint(context: Context, uri: Uri, sizeBytes: Long): String? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                listOf(
                    0L,
                    (sizeBytes / 4L - SAMPLE_BYTES / 2L).coerceAtLeast(0L),
                    (sizeBytes / 2L - SAMPLE_BYTES / 2L).coerceAtLeast(0L),
                    (sizeBytes * 3L / 4L - SAMPLE_BYTES / 2L).coerceAtLeast(0L),
                    (sizeBytes - SAMPLE_BYTES).coerceAtLeast(0L),
                ).distinct().forEach { offset ->
                    input.channel.position(offset)
                    updateDigest(input, digest, SAMPLE_BYTES)
                }
                digest.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
            }
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun fullContentHash(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun updateDigest(
        input: java.io.InputStream,
        digest: MessageDigest,
        byteCount: Int,
    ) {
        val buffer = ByteArray(byteCount)
        var remaining = byteCount
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    /**
     * Probables sin I/O: conecta videos que superan la puntuación de
     * [DuplicateHeuristics] y conserva la razón/confianza de la mejor coincidencia.
     */
    private suspend fun clusterProbables(items: List<VideoItem>): List<ProbableGroup> {
        if (items.size < 2) return emptyList()
        val parent = IntArray(items.size) { it }
        val matches = mutableMapOf<Pair<Int, Int>, DuplicateHeuristics.Match>()

        fun find(start: Int): Int {
            var root = start
            while (parent[root] != root) root = parent[root]
            var current = start
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }

        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) parent[rightRoot] = leftRoot
        }

        forEachUnorderedPair(
            itemCount = items.size,
            checkActive = { coroutineContext.ensureActive() },
        ) { left, right ->
            val match = DuplicateHeuristics.compare(items[left], items[right]) ?: return@forEachUnorderedPair
            matches[left to right] = match
            union(left, right)
        }

        return items.indices
            .groupBy(::find)
            .values
            .mapNotNull { indexes ->
                if (indexes.size < 2) return@mapNotNull null
                val members = indexes.map(items::get)
                val best = matches
                    .filterKeys { it.first in indexes && it.second in indexes }
                    .values
                    .maxByOrNull { it.score }
                    ?: return@mapNotNull null
                ProbableGroup(
                    items = members,
                    reason = "Confianza ${best.score}%: ${best.reason}",
                )
            }
    }

    /**
     * Verificación profunda de un grupo probable: compara el hash perceptual
     * (aHash 8×8) de 3 frames de cada video. Solo archivos locales.
     */
    suspend fun verifyProbableGroup(context: Context, items: List<VideoItem>): Boolean =
        withContext(Dispatchers.IO) {
            if (items.size < 2) return@withContext false
            val hashes = items.map { video ->
                if (video.uri.scheme != "content") return@withContext false
                frameHashes(context, video.uri, video.durationMs) ?: return@withContext false
            }
            for (i in hashes.indices) {
                for (j in i + 1 until hashes.size) {
                    for (k in hashes[i].indices) {
                        if (hamming(hashes[i][k], hashes[j][k]) > 10) return@withContext false
                    }
                }
            }
            true
        }

    private fun frameHashes(context: Context, uri: Uri, durationMs: Long): List<Long>? = runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = if (durationMs > 0) {
                durationMs
            } else {
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
            if (duration <= 0) return@runCatching null
            listOf(0.25, 0.5, 0.75).map { fraction ->
                val frame = retriever.getFrameAtTime(
                    (duration * fraction * 1000).toLong(),
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: return@runCatching null
                averageHash(frame).also { frame.recycle() }
            }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    private fun averageHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        return try {
            val pixels = IntArray(64)
            small.getPixels(pixels, 0, 8, 0, 0, 8, 8)
            var sum = 0L
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (r * 30 + g * 59 + b * 11) / 100
            }
            val avg = sum / 64
            var hash = 0L
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if ((r * 30 + g * 59 + b * 11) / 100 >= avg) {
                    hash = hash or (1L shl i)
                }
            }
            hash
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    sealed interface RenameOutcome {
        data object Renamed : RenameOutcome
        /** Android 11+ (y 10 recuperable): el sistema pide confirmación al usuario. */
        data class NeedsSystemConfirm(val intentSender: android.content.IntentSender) : RenameOutcome
        data class Failed(val message: String) : RenameOutcome
    }

    /**
     * Renombrado del archivo en el MediaStore. Mantiene la extensión si el usuario
     * no la escribe. En API 30+ (y 29 recuperable) el SO pide consentimiento vía IntentSender;
     * vuelve a llamar tras la confirmación para completar el cambio.
     */
    suspend fun renameFile(context: Context, uri: Uri, oldName: String, newName: String): RenameOutcome =
        withContext(Dispatchers.IO) {
            val clean = newName.trim()
                .replace('/', '_').replace('\\', '_')
            if (clean.isEmpty()) return@withContext RenameOutcome.Failed("El nombre no puede estar vacío")
            val extension = oldName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it != oldName }
            val finalName = if (extension != null && !clean.endsWith(".$extension", ignoreCase = true)) {
                "$clean.$extension"
            } else {
                clean
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
            }
            try {
                if (context.contentResolver.update(uri, values, null, null) > 0) {
                    return@withContext RenameOutcome.Renamed
                }
                RenameOutcome.Failed("No se pudo actualizar el nombre")
            } catch (e: RecoverableSecurityException) {
                // API 29 sin createWriteRequest; se usa el sender del sistema.
                return@withContext RenameOutcome.NeedsSystemConfirm(e.userAction.actionIntent.intentSender)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        val pending = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                        return@withContext RenameOutcome.NeedsSystemConfirm(pending.intentSender)
                    }.getOrElse {
                        RenameOutcome.Failed("Sin permiso para renombrar: ${it.message}")
                    }
                } else {
                    RenameOutcome.Failed(e.message ?: "Sin permiso para renombrar")
                }
            } catch (t: Throwable) {
                RenameOutcome.Failed(t.message ?: "Error al renombrar")
            }
        }

    sealed interface DeleteOutcome {
        data object Deleted : DeleteOutcome
        /** Android 11+ (y 10 recuperable): el sistema pide confirmación al usuario. */
        data class NeedsSystemConfirm(val intentSender: android.content.IntentSender) : DeleteOutcome
        data class Partial(val deleted: Int, val total: Int) : DeleteOutcome
        data class Failed(val message: String) : DeleteOutcome
    }

    /**
     * Borrado definitivo del MediaStore. En API 30+ (y 29 recuperable) el SO
     * exige confirmación del usuario vía IntentSender.
     */
    suspend fun deleteUris(context: Context, uris: List<Uri>): DeleteOutcome =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext DeleteOutcome.Deleted
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pending = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    return@withContext DeleteOutcome.NeedsSystemConfirm(pending.intentSender)
                }
                var deleted = 0
                var recoverable: android.content.IntentSender? = null
                uris.forEach { uri ->
                    try {
                        if (context.contentResolver.delete(uri, null, null) > 0) deleted++
                    } catch (e: RecoverableSecurityException) {
                        recoverable = e.userAction.actionIntent.intentSender
                    } catch (_: SecurityException) {
                    }
                }
                recoverable?.let { return@withContext DeleteOutcome.NeedsSystemConfirm(it) }
                if (deleted == uris.size) DeleteOutcome.Deleted
                else DeleteOutcome.Partial(deleted, uris.size)
            } catch (t: Throwable) {
                DeleteOutcome.Failed(t.message ?: "Error al eliminar")
            }
        }

    sealed interface MoveStep {
        data class Moved(val crossVolume: Boolean = false) : MoveStep
        /** Falta permiso de escritura: el SO pide confirmación (API 29/30+). */
        data class NeedWriteConfirm(val sender: android.content.IntentSender) : MoveStep
        /** Copia lista: falta consentimiento para borrar el original. */
        data class NeedDeleteConfirm(val newUri: Uri, val sender: android.content.IntentSender) : MoveStep
        data class Failed(val message: String) : MoveStep
    }

    /** Normaliza la ruta destino: sin barras en extremos al comparar, con '/' final al guardar. */
    internal fun normalizeDestPath(path: String, withSlash: Boolean = true): String {
        val cleaned = path.trim().trim('/').let { if (it.isBlank()) "" else it }
        if (cleaned.isEmpty()) return ""
        return if (withSlash) "$cleaned/" else cleaned
    }

    fun sourceVolumeOf(item: VideoItem): String =
        item.volumeName?.takeIf { it.isNotBlank() } ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            } else {
                MediaStore.VOLUME_EXTERNAL
            }
        }

    suspend fun abandonMove(context: Context, newUri: Uri): Unit = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(newUri, null, null) }
    }

    /**
     * Mueve un video a (volumen, carpeta). Vía rápida: actualizar RELATIVE_PATH en
     * el MediaStore (mismo volumen, sin copiar bytes). Vía robusta: copiar a la
     * carpeta destino (necesaria si cambia el volumen) y borrar el original, que
     * en API 30+ exige consentimiento del usuario vía IntentSender.
     */
    suspend fun moveSingle(
        context: Context,
        item: VideoItem,
        destVolume: String,
        destRelativePath: String,
    ): MoveStep = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext MoveStep.Failed("Requiere Android 10 o más")
        }
        val relPath = normalizeDestPath(destRelativePath)
        if (relPath.isEmpty()) return@withContext MoveStep.Failed("Carpeta destino no válida")
        val sameVolume = sourceVolumeOf(item) == destVolume
        if (sameVolume) {
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
                }
                if (context.contentResolver.update(item.uri, values, null, null) > 0) {
                    MoveStep.Moved()
                } else {
                    crossVolumeMove(context, item, destVolume, relPath)
                }
            } catch (e: RecoverableSecurityException) {
                MoveStep.NeedWriteConfirm(e.userAction.actionIntent.intentSender)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pending = runCatching {
                        MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))
                    }.getOrNull()
                    if (pending != null) {
                        MoveStep.NeedWriteConfirm(pending.intentSender)
                    } else {
                        crossVolumeMove(context, item, destVolume, relPath)
                    }
                } else {
                    crossVolumeMove(context, item, destVolume, relPath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                crossVolumeMove(context, item, destVolume, relPath)
            }
        } else {
            crossVolumeMove(context, item, destVolume, relPath)
        }
    }

    private suspend fun crossVolumeMove(
        context: Context,
        item: VideoItem,
        destVolume: String,
        relPath: String,
    ): MoveStep {
        val newUri = insertPendingCopy(context, item, destVolume, relPath)
            ?: return MoveStep.Failed("No se pudo copiar a la carpeta destino")
        return when (val outcome = deleteUris(context, listOf(item.uri))) {
            is DeleteOutcome.Deleted -> MoveStep.Moved(crossVolume = true)
            is DeleteOutcome.NeedsSystemConfirm -> MoveStep.NeedDeleteConfirm(newUri, outcome.intentSender)
            is DeleteOutcome.Partial -> {
                if (outcome.deleted > 0) {
                    MoveStep.Moved(crossVolume = true)
                } else {
                    abandonMove(context, newUri)
                    MoveStep.Failed("No se pudo borrar el original")
                }
            }
            is DeleteOutcome.Failed -> {
                abandonMove(context, newUri)
                MoveStep.Failed(outcome.message)
            }
        }
    }

    /** ¿Existe ya ese nombre exacto en la carpeta destino del volumen? */
    private fun nameTakenInDest(context: Context, volume: String, relPath: String, name: String): Boolean =
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.getContentUri(volume),
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.RELATIVE_PATH}=? AND ${MediaStore.Video.Media.DISPLAY_NAME}=?",
                arrayOf(relPath, name),
                null,
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)

    private suspend fun insertPendingCopy(
        context: Context,
        item: VideoItem,
        destVolume: String,
        relPath: String,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // MediaStore acepta nombres repetidos pero puede fusionar/ignorar la
            // inserción: buscamos un nombre libre en la carpeta destino.
            val ext = item.name.substringAfterLast('.', "")
            val base = item.name.substringBeforeLast('.', item.name).trim().ifEmpty { "video" }
            var candidate = item.name
            var suffix = 1
            while (nameTakenInDest(context, destVolume, relPath, candidate) && suffix < 200) {
                candidate = if (ext.isBlank()) "$base ($suffix)" else "$base ($suffix).$ext"
                suffix++
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, candidate)
                put(MediaStore.Video.Media.MIME_TYPE, item.mimeType.ifBlank { "video/*" })
                put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val newUri = context.contentResolver.insert(
                MediaStore.Video.Media.getContentUri(destVolume),
                values,
            ) ?: return@withContext null
            val copied = runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(newUri)?.use { out ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                        true
                    } ?: false
                } ?: false
            }.getOrElse { false }
            if (!copied) {
                runCatching { context.contentResolver.delete(newUri, null, null) }
                return@withContext null
            }
            runCatching {
                context.contentResolver.update(
                    newUri,
                    android.content.ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
            }
            newUri
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
}
