package com.magikvlc.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.magikvlc.app.player.MediaSource
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Serializable
data class PersistedTile(
    val uri: String? = null,
    val url: String? = null,
    val positionMs: Long = 0L,
    val rotationDegrees: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

@Serializable
data class SessionData(
    val layout: Int = 1,
    val tiles: List<PersistedTile> = emptyList(),
)

class SessionRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): SessionData? {
        val raw = context.sessionDataStore.data.first()[KEY_SESSION] ?: return null
        return runCatching { json.decodeFromString<SessionData>(raw) }.getOrNull()
    }

    suspend fun save(layout: Int, tiles: List<PersistedTile>) {
        runCatching {
            context.sessionDataStore.edit { prefs ->
                prefs[KEY_SESSION] = json.encodeToString(SessionData(layout, tiles))
            }
        }
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("session_json")
    }
}

fun PersistedTile.toSource(): MediaSource? = when {
    uri != null -> MediaSource.Local(android.net.Uri.parse(uri))
    url != null -> MediaSource.Network(url)
    else -> null
}

fun MediaSource.toPersisted(
    positionMs: Long,
    rotationDegrees: Float = 0f,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
): PersistedTile = when (this) {
    is MediaSource.Local -> PersistedTile(uri = uri.toString(), positionMs = positionMs, rotationDegrees = rotationDegrees, zoom = zoom, panX = panX, panY = panY)
    is MediaSource.Network -> PersistedTile(url = url, positionMs = positionMs, rotationDegrees = rotationDegrees, zoom = zoom, panX = panX, panY = panY)
}
