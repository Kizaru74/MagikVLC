package com.magikvlc.app.data

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DuplicateScanWorkRepository(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    val workInfos: Flow<List<WorkInfo>> = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)

    /** Initial UI restoration never replaces an already queued/running mode. */
    suspend fun ensureScan(deepScan: Boolean) = withContext(Dispatchers.IO) {
        val current = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        val hasActive = current.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }
        if (hasActive) return@withContext
        enqueueRequest(deepScan, ExistingWorkPolicy.KEEP)
    }

    /** Explicit refresh: replace active work and its report, regardless of the current mode. */
    suspend fun startFresh(deepScan: Boolean) = withContext(Dispatchers.IO) {
        enqueueRequest(deepScan, ExistingWorkPolicy.REPLACE)
    }

    suspend fun enqueue(deepScan: Boolean) = withContext(Dispatchers.IO) {
        val current = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        val active = current.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        val activeMode = active?.let { modeTag(true) in it.tags }
        val decision = DuplicateScanWorkPolicy.decision(
            active = active != null,
            requestedDeepScan = deepScan,
            activeDeepScan = activeMode,
        )
        if (decision == DuplicateScanWorkPolicy.Decision.KEEP) return@withContext
        val policy = if (decision == DuplicateScanWorkPolicy.Decision.REPLACE) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }
        enqueueRequest(deepScan, policy)
    }

    private fun enqueueRequest(deepScan: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DuplicateScanWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(DuplicateScanWorker.KEY_DEEP_SCAN, deepScan)
                    .build(),
            )
            .addTag(TAG)
            .addTag(modeTag(deepScan))
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "duplicate_scan"
        const val TAG = "duplicate_scan"

        fun modeTag(deepScan: Boolean) = "duplicate_scan_mode_${if (deepScan) "deep" else "basic"}"
    }
}
