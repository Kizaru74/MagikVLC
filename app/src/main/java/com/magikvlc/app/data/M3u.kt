package com.magikvlc.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Exportación/importación de listas en formato M3U estándar (compatible con VLC). */
object M3u {

    fun export(items: List<PlaylistItem>): String = buildString {
        appendLine("#EXTM3U")
        items.forEach { item ->
            appendLine("#EXTINF:-1,${item.name.replace("\n", " ")}")
            appendLine(item.uri)
        }
    }

    fun parse(text: String): List<PlaylistItem> {
        val items = mutableListOf<PlaylistItem>()
        var pendingName: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#EXTINF")) {
                pendingName = line.substringAfter(",", "").trim().ifEmpty { null }
            } else if (!line.startsWith("#")) {
                val name = pendingName
                    ?: line.substringAfterLast('/').substringAfterLast('\\').ifEmpty { line }
                items += PlaylistItem(uri = line, name = name)
                pendingName = null
            }
        }
        return items
    }

    suspend fun saveToDownloads(context: Context, fileName: String, content: String): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9 _.-]"), "_").ifBlank { "lista" } + ".m3u"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, "audio/x-mpegurl")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/MagikVLC")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                    ) ?: return@runCatching null
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray())
                    }
                    uri
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "MagikVLC",
                    ).apply { mkdirs() }
                    val file = File(dir, safeName)
                    file.writeText(content)
                    Uri.fromFile(file)
                }
            }.getOrNull()
        }

    suspend fun readUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }
}
