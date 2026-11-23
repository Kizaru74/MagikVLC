package com.magikvlc.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

object ThumbnailCache {

    /** La SD se satura con muchas peticiones a la vez: como máximo 3 en vuelo. */
    private val ioSlots = Semaphore(3)

    /** El pre-generador usa su propio carril de 1 permiso y cede ante lo visible. */
    private val warmSlots = Semaphore(1)
    private val visibleLoads = AtomicInteger(0)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    suspend fun get(context: Context, item: VideoItem): Bitmap? = withContext(Dispatchers.IO) {
        val key = keyFor(item)
        synchronized(cache) { cache.get(key) }?.let { return@withContext it }
        visibleLoads.incrementAndGet()
        try {
            ioSlots.withPermit {
                // Otra celda pudo rellenarla mientras esperábamos turno.
                synchronized(cache) { cache.get(key) }?.let { return@withPermit it }
                ensureActive()
                val app = context.applicationContext
                ThumbnailDiskCache.get(app, item)?.let { disk ->
                    synchronized(cache) { cache.put(key, disk) }
                    return@withPermit disk
                }
                val bitmap = VideosDataSource.loadThumbnail(app, item)?.let(::to565)
                if (bitmap != null) {
                    synchronized(cache) { cache.put(key, bitmap) }
                    ThumbnailDiskCache.put(app, item, bitmap)
                }
                bitmap
            }
        } finally {
            visibleLoads.decrementAndGet()
        }
    }

    /** Normaliza a 16 bits para que la caché en memoria rinda el doble. */
    private fun to565(src: Bitmap): Bitmap {
        if (src.config == Bitmap.Config.RGB_565 || src.isRecycled) return src
        val copy = runCatching { src.copy(Bitmap.Config.RGB_565, false) }.getOrNull()
        if (copy != null) {
            runCatching { src.recycle() }
            return copy
        }
        return src
    }

    /** Carga de fondo: espera a que no haya cargas visibles en vuelo. */
    private suspend fun getLowPriority(context: Context, item: VideoItem): Bitmap? {
        while (visibleLoads.get() > 0) {
            delay(200)
        }
        return warmSlots.withPermit {
            get(context, item)
        }
    }

    /** Asegura la miniatura en caché sin devolverla (para pre-generación). */
    suspend fun warm(context: Context, item: VideoItem): Boolean {
        val key = keyFor(item)
        val app = context.applicationContext
        if (synchronized(cache) { cache.get(key) } != null) return true
        if (ThumbnailDiskCache.exists(app, item)) {
            // La traerá get() a memoria cuando se vea; evita decodificar ahora.
            return true
        }
        return getLowPriority(app, item) != null
    }

    fun removeByUri(context: Context, uri: String) {
        synchronized(cache) {
            cache.snapshot().keys.filter { it.startsWith("$uri#") }.forEach { cache.remove(it) }
        }
        canonicalMediaId(uri)?.toLongOrNull()?.let { id ->
            ThumbnailDiskCache.removeByMediaId(context.applicationContext, id)
        }
    }

    fun clear(context: Context) {
        synchronized(cache) { cache.evictAll() }
        ThumbnailDiskCache.clear(context.applicationContext)
    }

    private fun keyFor(item: VideoItem): String = "${item.uri}#${item.id}"

    private fun cacheSizeKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxKb / 8).coerceIn(8 * 1024, 64 * 1024)
    }
}
