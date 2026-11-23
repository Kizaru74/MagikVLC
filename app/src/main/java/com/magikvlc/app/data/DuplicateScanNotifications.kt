package com.magikvlc.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import java.util.UUID

object DuplicateScanNotifications {
    const val CHANNEL_ID = "duplicate_scan"
    const val NOTIFICATION_ID = 4107

    fun foregroundInfo(
        context: Context,
        workId: UUID,
        phase: String? = null,
        completed: Int = 0,
        total: Int = 0,
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Buscando duplicados")
            .setContentText(phase ?: "Preparando biblioteca…")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), completed.coerceAtLeast(0), total <= 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(workId),
            )
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Análisis de duplicados", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progreso del análisis de duplicados de la biblioteca"
            },
        )
    }
}
