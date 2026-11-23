package com.magikvlc.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Caché de miniaturas en disco (LRU por fecha de acceso, tope 150 MB).
 * Hace instantáneas las revisitas a la biblioteca, sobre todo con SD lenta.
 */
object ThumbnailDiskCache {

    private const val MAX_BYTES = 150L * 1024 * 1024
    private const val ENFORCE_EVERY_PUTS = 20
    private val putsSinceEnforce = java.util.concurrent.atomic.AtomicInteger(0)

    private fun dir(context: Context): File =
        File(context.cacheDir, "thumbs").apply { mkdirs() }

    private fun fileFor(dir: File, item: VideoItem): File =
        File(dir, "${item.id}-${item.modifiedSec}-${item.sizeBytes}.jpg")

    suspend fun get(context: Context, item: VideoItem): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(dir(context), item)
            if (!file.exists()) return@runCatching null
            file.setLastModified(System.currentTimeMillis())
            val options = BitmapFactory.Options().apply {
                // 16 bits: la mitad de RAM que ARGB_8888, indistinguible en celdas.
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    suspend fun put(context: Context, item: VideoItem, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            val directory = dir(context)
            val file = fileFor(directory, item)
            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis())
                return@runCatching
            }
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            // Listar el directorio en cada guardado frena la SD: solo cada N.
            if (putsSinceEnforce.incrementAndGet() >= ENFORCE_EVERY_PUTS) {
                putsSinceEnforce.set(0)
                enforceCap(directory)
            }
        }
    }

    fun exists(context: Context, item: VideoItem): Boolean = runCatching {
        fileFor(dir(context), item).exists()
    }.getOrDefault(false)

    /** Borra las entradas de un video por su ID de MediaStore (tras eliminar). */
    fun removeByMediaId(context: Context, mediaId: Long) {
        runCatching {
            dir(context).listFiles { file -> file.name.startsWith("$mediaId-") }
                ?.forEach { it.delete() }
        }
    }

    fun clear(context: Context) {
        runCatching {
            dir(context).listFiles()?.forEach { it.delete() }
        }
    }

    fun sizeBytes(context: Context): Long = runCatching {
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)

    private fun enforceCap(directory: File) {
        val files = directory.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= MAX_BYTES) return
            total -= file.length()
            file.delete()
        }
    }
}
