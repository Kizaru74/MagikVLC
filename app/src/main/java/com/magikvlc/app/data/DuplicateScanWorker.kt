package com.magikvlc.app.data

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.magikvlc.app.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException

class DuplicateScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val reportStore = DuplicateScanReportStore(appContext.filesDir)

    override suspend fun getForegroundInfo(): ForegroundInfo =
        DuplicateScanNotifications.foregroundInfo(applicationContext, id)

    override suspend fun doWork(): Result {
        val deepScan = inputData.getBoolean(KEY_DEEP_SCAN, false)
        val workId = id.toString()
        val startedAt = SystemClock.elapsedRealtime()
        setForeground(getForegroundInfo())
        DiagnosticLog.log("DUPLICATES", "start work=$workId deep=$deepScan")
        return try {
            val videos = VideosDataSource.loadForDuplicateScan(applicationContext, VideoSort.NEWEST)
            DiagnosticLog.log("DUPLICATES", "snapshot work=$workId videos=${videos.size}")
            if (videos.isEmpty()) {
                DiagnosticLog.log("DUPLICATES", "empty snapshot work=$workId; saved explicit empty-library error")
                val emptyReport = StoredDuplicateScanReport(
                    scannedCount = 0,
                    candidateRuns = 0,
                    unreadable = 0,
                    error = "La biblioteca no contiene videos para analizar",
                    exactGroups = emptyList(),
                    probableGroups = emptyList(),
                )
                reportStore.save(workId, emptyReport)
                Result.success(output(workId, 0))
            } else {
                var lastUpdateAt = 0L
                var lastPhase: VideosDataSource.DuplicateScanPhase? = null
                val report = VideosDataSource.findDuplicates(
                    applicationContext,
                    videos,
                    deepScan = deepScan,
                ) { progress ->
                    val now = SystemClock.elapsedRealtime()
                    val phaseChanged = progress.phase != lastPhase
                    val finishedPhase = progress.total > 0 && progress.completed >= progress.total
                    if (phaseChanged || finishedPhase || now - lastUpdateAt >= PROGRESS_INTERVAL_MS) {
                        val phaseText = when (progress.phase) {
                            VideosDataSource.DuplicateScanPhase.PREPARING -> "Preparando candidatos…"
                            VideosDataSource.DuplicateScanPhase.QUICK_HASH -> "Comparando muestras"
                            VideosDataSource.DuplicateScanPhase.FULL_HASH -> "Confirmando copias"
                        }
                        setProgress(
                            Data.Builder()
                                .putString(KEY_PHASE, progress.phase.name)
                                .putInt(KEY_COMPLETED, progress.completed)
                                .putInt(KEY_TOTAL, progress.total)
                                .putBoolean(KEY_DEEP_SCAN, deepScan)
                                .build(),
                        )
                        setForeground(
                            DuplicateScanNotifications.foregroundInfo(
                                applicationContext,
                                id,
                                phaseText,
                                progress.completed,
                                progress.total,
                            ),
                        )
                        lastPhase = progress.phase
                        lastUpdateAt = now
                    }
                }
                reportStore.save(workId, report.toStored())
                DiagnosticLog.log(
                    "DUPLICATES",
                    "complete work=$workId scanned=${report.scannedCount} candidates=${report.candidateRuns} " +
                        "exact=${report.groups.size} probable=${report.probableGroups.size} unreadable=${report.unreadable} " +
                        "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                Result.success(output(workId, report.scannedCount))
            }
        } catch (cancelled: CancellationException) {
            DiagnosticLog.log("DUPLICATES", "cancelled work=$workId")
            throw cancelled
        } catch (error: Exception) {
            DiagnosticLog.log("DUPLICATES", "error work=$workId message=${error.message}")
            reportStore.save(
                workId,
                StoredDuplicateScanReport(
                    scannedCount = 0,
                    candidateRuns = 0,
                    unreadable = 0,
                    error = error.message ?: "Error desconocido al analizar duplicados",
                    exactGroups = emptyList(),
                    probableGroups = emptyList(),
                ),
            )
            Result.failure(output(workId, 0))
        }
    }

    private fun output(workId: String, scannedCount: Int) = Data.Builder()
        .putString(KEY_REPORT_ID, workId)
        .putInt(KEY_SCANNED_COUNT, scannedCount)
        .build()

    companion object {
        const val KEY_DEEP_SCAN = "deep_scan"
        const val KEY_REPORT_ID = "report_id"
        const val KEY_PHASE = "phase"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_SCANNED_COUNT = "scanned_count"
        private const val PROGRESS_INTERVAL_MS = 400L
    }
}
