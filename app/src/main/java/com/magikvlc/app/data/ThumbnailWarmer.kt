package com.magikvlc.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import com.magikvlc.app.diagnostics.DiagnosticLog

/**
 * Pre-genera miniaturas prioritarias en segundo plano.
 * La cola se puede pausar al cambiar de pestaña y reanudar sin repetir
 * elementos ya preparados.
 */
object ThumbnailWarmer {

    data class Progress(val done: Int, val total: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val runId = AtomicLong(0L)
    private var job: Job? = null
    private var targetVideos: List<VideoItem> = emptyList()
    private var completedKeys = mutableSetOf<String>()
    private var attemptedKeys = mutableSetOf<String>()
    private var scrollingCheck: () -> Boolean = { false }
    private var paused = false

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    fun start(
        context: Context,
        videos: List<VideoItem>,
        isScrolling: () -> Boolean,
    ) {
        if (videos.isEmpty()) {
            cancel()
            return
        }
        val app = context.applicationContext
        var launchJob: Job? = null
        synchronized(stateLock) {
            val nextTarget = ThumbnailWarmQueue.select(videos)
            val sameTarget = ThumbnailWarmQueue.sameTarget(
                previousKeys = targetVideos.map(ThumbnailWarmQueue::key),
                nextKeys = nextTarget.map(ThumbnailWarmQueue::key),
            )
            val wasPaused = paused
            targetVideos = nextTarget
            scrollingCheck = isScrolling
            paused = false
            if (sameTarget && !wasPaused && job?.isActive == true) {
                DiagnosticLog.log("THUMBNAIL", "warm unchanged videos=${nextTarget.size}")
                return
            }
            // Cambiar la lista invalida el worker anterior de forma explícita.
            // Se conservan completedKeys para no repetir miniaturas ya listas.
            job?.cancel()
            val currentRun = runId.incrementAndGet()
            attemptedKeys.clear()
            DiagnosticLog.log(
                "THUMBNAIL",
                "warm ${if (sameTarget && wasPaused) "resume" else "start"} videos=${nextTarget.size}",
            )
            launchJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    warmUntilComplete(app, currentRun)
                } finally {
                    synchronized(stateLock) {
                        if (runId.get() == currentRun) job = null
                    }
                }
            }
            job = launchJob
        }
        launchJob?.start()
    }

    /** Pausa sin borrar la cola ni el progreso ya realizado. */
    fun pause(reason: String = "unspecified") {
        DiagnosticLog.log("THUMBNAIL", "warm pause reason=$reason")
        synchronized(stateLock) {
            paused = true
            runId.incrementAndGet()
            job?.cancel()
            job = null
        }
    }

    /** Detiene y borra la cola; se usa cuando el usuario pulsa «Parar». */
    fun cancel() {
        DiagnosticLog.log("THUMBNAIL", "warm cancel")
        synchronized(stateLock) {
            paused = true
            runId.incrementAndGet()
            job?.cancel()
            job = null
            targetVideos = emptyList()
            completedKeys.clear()
            attemptedKeys.clear()
            _progress.value = null
        }
    }

    private suspend fun warmUntilComplete(context: Context, workerRun: Long) {
        var lastEmit = 0L
        while (true) {
            coroutineContext.ensureActive()
            val videos: List<VideoItem>
            val scrollCheck: () -> Boolean
            synchronized(stateLock) {
                if (!isCurrent(workerRun)) return
                videos = targetVideos
                scrollCheck = scrollingCheck
            }
            if (videos.isEmpty()) {
                publish(workerRun, null)
                return
            }

            val pending = synchronized(stateLock) {
                videos.filter {
                    val key = ThumbnailWarmQueue.key(it)
                    key !in completedKeys && key !in attemptedKeys
                }
            }
            val done = synchronized(stateLock) {
                ThumbnailWarmQueue.completedCount(
                    videos.map(ThumbnailWarmQueue::key),
                    completedKeys,
                )
            }
            publish(workerRun, Progress(done, videos.size))
            if (pending.isEmpty()) {
                publish(workerRun, null)
                return
            }

            for (video in pending) {
                coroutineContext.ensureActive()
                while (scrollCheck()) {
                    delay(300)
                    coroutineContext.ensureActive()
                    if (!isCurrent(workerRun)) return
                }
                val warmed = try {
                    ThumbnailCache.warm(context, video)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }
                if (!isCurrent(workerRun)) return
                synchronized(stateLock) {
                    if (isCurrent(workerRun)) {
                        attemptedKeys += ThumbnailWarmQueue.key(video)
                        if (ThumbnailWarmQueue.shouldMarkCompleted(warmed)) {
                            completedKeys += ThumbnailWarmQueue.key(video)
                        }
                    }
                }
                val currentTarget = synchronized(stateLock) {
                    targetVideos
                }
                val currentDone = synchronized(stateLock) {
                    ThumbnailWarmQueue.completedCount(
                        currentTarget.map(ThumbnailWarmQueue::key),
                        completedKeys,
                    )
                }
                // Throttle: evita recomponer la UI por cada video de una biblioteca grande.
                if (currentDone >= currentTarget.size || System.currentTimeMillis() - lastEmit >= 500L) {
                    publish(workerRun, Progress(currentDone, currentTarget.size))
                    lastEmit = System.currentTimeMillis()
                }
            }
        }
    }

    private fun isCurrent(workerRun: Long): Boolean =
        ThumbnailWarmQueue.canPublish(workerRun, runId.get(), paused)

    private fun publish(workerRun: Long, value: Progress?) {
        synchronized(stateLock) {
            if (isCurrent(workerRun)) _progress.value = value
        }
    }
}
