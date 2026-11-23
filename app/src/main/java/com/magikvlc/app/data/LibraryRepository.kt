package com.magikvlc.app.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.libraryPrefs: DataStore<Preferences> by preferencesDataStore(name = "library")

@Serializable
data class PlaylistItem(
    val uri: String,
    val name: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long? = null,
    val modifiedSec: Long? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<PlaylistItem> = emptyList(),
)

/** Favorito con metadatos: permite re-enlazar la ruta al mover entre volúmenes. */
@Serializable
data class SavedFavorite(
    val uri: String,
    val name: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long? = null,
    val modifiedSec: Long? = null,
) {
    companion object {
        fun from(video: VideoItem, keepUri: String? = null): SavedFavorite = SavedFavorite(
            uri = keepUri ?: video.uri.toString(),
            name = video.name,
            durationMs = video.durationMs,
            sizeBytes = video.sizeBytes.takeIf { it > 0L },
            modifiedSec = video.modifiedSec.takeIf { it > 0L },
        )
    }
}

/** Progreso máximo visto de un video, persistido para la línea tipo VLC. */
@Serializable
data class WatchedProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

    val completed: Boolean
        get() = fraction >= 0.95f
}

class LibraryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val sort: Flow<VideoSort> = context.libraryPrefs.data
        .map { prefs ->
            runCatching { VideoSort.valueOf(prefs[KEY_SORT] ?: VideoSort.NEWEST.name) }
                .getOrDefault(VideoSort.NEWEST)
        }
        .catch { emit(VideoSort.NEWEST) }

    suspend fun setSort(sort: VideoSort) {
        context.libraryPrefs.edit { it[KEY_SORT] = sort.name }
    }

    val favorites: Flow<List<SavedFavorite>> = context.libraryPrefs.data
        .map { prefs -> decodeFavorites(prefs) }
        .catch { emit(emptyList()) }

    /** Solo URIs, para comprobaciones de pertenencia en la UI (estrellas, contador). */
    val favoritesUris: Flow<Set<String>> = favorites.map { list -> list.map { it.uri }.toSet() }

    val watchedProgress: Flow<Map<String, WatchedProgress>> = context.libraryPrefs.data
        .map { prefs ->
            val raw = prefs[KEY_WATCHED_PROGRESS] ?: return@map emptyMap()
            runCatching { json.decodeFromString<Map<String, WatchedProgress>>(raw) }
                .getOrDefault(emptyMap())
        }
        .catch { emit(emptyMap()) }

    /** Guarda el máximo alcanzado: retroceder no acorta la barra ya vista. */
    suspend fun savePlaybackProgress(uri: Uri, positionMs: Long, durationMs: Long) {
        if (positionMs < 0L || durationMs <= 0L) return
        val key = mediaWatchKey(uri, durationMs) ?: return
        context.libraryPrefs.edit { prefs ->
            val current = prefs[KEY_WATCHED_PROGRESS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, WatchedProgress>>(raw) }
                    .getOrDefault(emptyMap())
            }.orEmpty()
            val previous = current[key]
            val next = WatchedProgress(
                positionMs = maxOf(previous?.positionMs ?: 0L, positionMs.coerceAtMost(durationMs)),
                durationMs = durationMs,
            )
            if (next != previous) {
                prefs[KEY_WATCHED_PROGRESS] = json.encodeToString(current + (key to next))
            }
        }
    }

    private fun mediaWatchKey(uri: Uri, fallbackDurationMs: Long): String? {
        val projection = arrayOf(
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val size = cursor.getLong(0)
                val duration = cursor.getLong(1).takeIf { it > 0L } ?: fallbackDurationMs
                watchedKey(size, duration)
            }
        }.getOrNull()
    }

    private fun decodeFavorites(prefs: Preferences): List<SavedFavorite> {
        val raw = prefs[KEY_FAVORITES_JSON]
        if (raw != null) {
            return runCatching { json.decodeFromString<List<SavedFavorite>>(raw) }
                .getOrDefault(emptyList())
        }
        // Migración de la forma vieja (StringSet de URIs, sin metadatos).
        val legacy = prefs[KEY_FAVORITES] ?: return emptyList()
        return legacy.map { SavedFavorite(uri = it) }
    }

    private suspend fun writeFavorites(items: List<SavedFavorite>) {
        context.libraryPrefs.edit { prefs ->
            prefs[KEY_FAVORITES_JSON] = json.encodeToString(items)
            prefs.remove(KEY_FAVORITES)
        }
    }

    /** Reemplaza la lista completa de favoritos (migración de URIs / enriquecimiento). */
    suspend fun setFavorites(items: List<SavedFavorite>) {
        writeFavorites(items)
    }

    /** Alterna favorito. Devuelve el nuevo estado (true = ahora es favorito). */
    suspend fun toggleFavorite(item: VideoItem): Boolean {
        var result = false
        val current = context.libraryPrefs.data.first()
        val favs = decodeFavorites(current)
        val uri = item.uri.toString()
        val next = if (favs.any { it.uri == uri }) {
            favs.filterNot { it.uri == uri }
        } else {
            favs + SavedFavorite.from(item)
        }
        result = next.any { it.uri == uri }
        writeFavorites(next)
        return result
    }

    val playlists: Flow<List<Playlist>> = context.libraryPrefs.data
        .map { prefs ->
            val raw = prefs[KEY_PLAYLISTS] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<Playlist>>(raw) }.getOrDefault(emptyList())
        }
        .catch { emit(emptyList()) }

    suspend fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(id = UUID.randomUUID().toString(), name = name.trim())
        savePlaylists(playlists.first() + playlist)
        return playlist
    }

    suspend fun renamePlaylist(id: String, name: String) {
        savePlaylists(playlists.first().map {
            if (it.id == id) it.copy(name = name.trim()) else it
        })
    }

    suspend fun deletePlaylist(id: String) {
        savePlaylists(playlists.first().filterNot { it.id == id })
    }

    suspend fun addToPlaylist(id: String, items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        savePlaylists(playlists.first().map { playlist ->
            if (playlist.id != id) return@map playlist
            val existing = playlist.items.map { it.uri }.toSet()
            playlist.copy(items = playlist.items + items.filterNot { it.uri in existing })
        })
    }

    suspend fun removeFromPlaylist(id: String, uri: String) {
        savePlaylists(playlists.first().map { playlist ->
            if (playlist.id != id) playlist
            else playlist.copy(items = playlist.items.filterNot { it.uri == uri })
        })
    }

    /** Re-enlaza URIs viejas (`.../external/...`) a las actuales por volumen. */
    suspend fun relinkPlaylistUris(id: String, mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        savePlaylists(playlists.first().map { playlist ->
            if (playlist.id != id) return@map playlist
            playlist.copy(items = playlist.items.map { item ->
                val newUri = mapping[item.uri]
                if (newUri != null && newUri != item.uri) item.copy(uri = newUri) else item
            })
        })
    }

    /**
     * Re-enlaza todas las listas tras un escaneo (video movido entre volúmenes) y
     * persiste las rutas nuevas. Devuelve cuántas entradas quedaron corregidas.
     */
    suspend fun relinkAllPlaylists(videos: List<VideoItem>): Int {
        if (videos.isEmpty()) return 0
        var fixed = 0
        val updated = playlists.first().map { playlist ->
            val mapping = resolvePlaylistEntries(playlist, videos).mapNotNull { entry ->
                val current = entry.video?.uri?.toString()
                if (current != null && current != entry.item.uri) {
                    fixed++
                    entry.item.uri to current
                } else {
                    null
                }
            }.toMap()
            if (mapping.isEmpty()) {
                playlist
            } else {
                playlist.copy(
                    items = playlist.items.map { item ->
                        val newUri = mapping[item.uri]
                        if (newUri != null) item.copy(uri = newUri) else item
                    },
                )
            }
        }
        if (fixed > 0) savePlaylists(updated)
        return fixed
    }

    /** Limpia favoritos y listas tras un borrado definitivo del MediaStore. */
    suspend fun pruneDeleted(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val set = uris.toSet()
        val favs = decodeFavorites(context.libraryPrefs.data.first())
        if (favs.isNotEmpty()) writeFavorites(favs.filterNot { it.uri in set })
        val raw = context.libraryPrefs.data.first()[KEY_PLAYLISTS] ?: return
        val lists = runCatching { json.decodeFromString<List<Playlist>>(raw) }.getOrDefault(emptyList())
        savePlaylists(lists.map { it.copy(items = it.items.filterNot { item -> item.uri in set }) })
    }

    private suspend fun savePlaylists(lists: List<Playlist>) {
        context.libraryPrefs.edit { it[KEY_PLAYLISTS] = json.encodeToString(lists) }
    }

    private companion object {
        val KEY_SORT = stringPreferencesKey("video_sort")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        val KEY_FAVORITES_JSON = stringPreferencesKey("favorites_json")
        val KEY_WATCHED_PROGRESS = stringPreferencesKey("watched_progress_json")
        val KEY_PLAYLISTS = stringPreferencesKey("playlists_json")
    }
}

/** Huella estable entre URI, renombrado y movimiento de un mismo archivo. */
fun watchedKey(sizeBytes: Long, durationMs: Long): String? =
    if (sizeBytes > 0L && durationMs > 0L) {
        // Copiar entre volúmenes cambia DATE_MODIFIED, por eso no forma parte de la clave.
        "$sizeBytes:$durationMs"
    } else {
        null
    }

fun VideoItem.watchedKey(): String? = watchedKey(sizeBytes, durationMs)

fun VideoItem.toPlaylistItem(): PlaylistItem =
    PlaylistItem(
        uri = uri.toString(),
        name = name,
        durationMs = durationMs,
        sizeBytes = sizeBytes.takeIf { it > 0L },
        modifiedSec = modifiedSec.takeIf { it > 0L },
    )

fun PlaylistItem.toVideoItem(): VideoItem = VideoItem(
    id = uri.hashCode().toLong(),
    uri = android.net.Uri.parse(uri),
    name = name,
    durationMs = durationMs,
    sizeBytes = 0L,
    relativePath = "",
)

data class PlaylistEntry(
    val item: PlaylistItem,
    val video: VideoItem?,
)

/** ID numérico final de una URI MediaStore (`.../<id>`), o null si no aplica. */
fun canonicalMediaId(uri: String): String? {
    val last = uri.trimEnd('/').substringAfterLast('/')
    return last.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
}

fun resolvePlaylistEntries(playlist: Playlist, videos: List<VideoItem>): List<PlaylistEntry> {
    val byUri = videos.associateBy { it.uri.toString() }
    val byId = videos.groupBy { canonicalMediaId(it.uri.toString()) }
    return playlist.items.map { item ->
        val direct = byUri[item.uri]
        val fallback = if (direct == null) {
            // Migración: las listas viejas guardan `.../external/...`; las nuevas
            // usan el volumen (`external_primary`/UUID). Solo se acepta si hay
            // un único candidato para no enlazar el video equivocado.
            canonicalMediaId(item.uri)?.let { id -> byId[id]?.singleOrNull() }
        } else {
            null
        }
        // Huella: el video se movió de volumen (cambia el ID). Sin I/O, solo se
        // acepta si hay un único candidato con el mismo nombre + duración ±2 s
        // y (si se guardó) el mismo tamaño.
        val byFingerprint = if (direct == null && fallback == null) {
            fingerprintMatch(item, videos)
        } else {
            null
        }
        PlaylistEntry(item = item, video = direct ?: fallback ?: byFingerprint)
    }
}

/** Match por huella para playlists tras mover el archivo entre volúmenes. */
private fun fingerprintMatch(item: PlaylistItem, videos: List<VideoItem>): VideoItem? {
    if (item.name.isBlank() || item.durationMs <= 0L) return null
    val hits = videos.filter { v ->
        VideosDataSource.normalizeName(v.name) == VideosDataSource.normalizeName(item.name) &&
            kotlin.math.abs(v.durationMs - item.durationMs) <= 2000L &&
            (item.sizeBytes == null || item.sizeBytes == v.sizeBytes)
    }
    return hits.singleOrNull()
}

data class FavoriteMatch(
    val videos: List<VideoItem>,
    /** Nueva lista a persistir cuando hubo re-enlace o enriquecimiento, o null. */
    val migrated: List<SavedFavorite>?,
)

/** Entrada pura de strings para emparejar favoritos sin depender de Android. */
internal data class FavoriteRecord(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long?,
    val modifiedSec: Long?,
)

internal data class FavoriteMatchResult(
    val matchedVideoUris: List<String>,
    val migrated: List<SavedFavorite>?,
)

internal fun matchFavoritesCore(
    videos: List<FavoriteRecord>,
    favorites: List<SavedFavorite>,
): FavoriteMatchResult {
    if (favorites.isEmpty()) return FavoriteMatchResult(emptyList(), null)
    val byUri = videos.associateBy { it.uri }
    val byId = videos.groupBy { canonicalMediaId(it.uri) }
    // El nombre normalizado es caro: se calcula una vez por video, no por par.
    val byName = videos.groupBy { VideosDataSource.normalizeName(it.name) }
    val matched = mutableListOf<FavoriteRecord>()
    val migrated = favorites.toMutableList()
    var changed = false

    fun place(index: Int, video: FavoriteRecord, keepUri: String? = null) {
        matched += video
        val fresh = SavedFavorite(
            uri = keepUri ?: video.uri,
            name = video.name,
            durationMs = video.durationMs,
            sizeBytes = video.sizeBytes,
            modifiedSec = video.modifiedSec,
        )
        if (migrated[index] != fresh) {
            migrated[index] = fresh
            changed = true
        }
    }

    for ((index, stored) in favorites.withIndex()) {
        // 1) URI directa (y aquí además enriquecemos metadatos si faltaban).
        val direct = byUri[stored.uri]
        if (direct != null) {
            place(index, direct, keepUri = stored.uri)
            continue
        }
        // 2) ID canónico (migración extern→volumen con el mismo ID).
        val candidate = canonicalMediaId(stored.uri)?.let { id -> byId[id]?.singleOrNull() }
        if (candidate != null) {
            place(index, candidate)
            continue
        }
        // 3) Huella: el archivo se movió entre volúmenes (cambia el ID y la ruta).
        val hit = if (stored.name.isNotBlank() && stored.durationMs > 0L) {
            val name = VideosDataSource.normalizeName(stored.name)
            byName[name]?.singleOrNull { v ->
                kotlin.math.abs(v.durationMs - stored.durationMs) <= 2000L &&
                    (stored.sizeBytes == null || stored.sizeBytes == v.sizeBytes)
            }
        } else {
            null
        }
        if (hit != null) {
            place(index, hit)
        }
    }

    return FavoriteMatchResult(
        matchedVideoUris = matched.map { it.uri },
        migrated = if (changed) migrated else null,
    )
}

fun matchFavorites(
    videos: List<VideoItem>,
    favorites: List<SavedFavorite>,
): FavoriteMatch {
    if (favorites.isEmpty()) return FavoriteMatch(emptyList(), null)
    val result = matchFavoritesCore(
        videos = videos.map { v ->
            FavoriteRecord(
                uri = v.uri.toString(),
                name = v.name,
                durationMs = v.durationMs,
                sizeBytes = v.sizeBytes.takeIf { it > 0L },
                modifiedSec = v.modifiedSec.takeIf { it > 0L },
            )
        },
        favorites = favorites,
    )
    val order = result.matchedVideoUris.toSet()
    return FavoriteMatch(
        videos = videos.filter { it.uri.toString() in order },
        migrated = result.migrated,
    )
}

fun PlaylistEntry.asVideoItem(): VideoItem = video ?: item.toVideoItem()
