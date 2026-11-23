package com.magikvlc.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class StoredVideoItem(
    val id: Long,
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val relativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val storage: String,
    val volumeName: String?,
    val modifiedSec: Long,
)

@Serializable
data class StoredDuplicateGroup(
    val items: List<StoredVideoItem>,
    val freedBytes: Long,
)

@Serializable
data class StoredProbableGroup(
    val items: List<StoredVideoItem>,
    val reason: String,
)

@Serializable
data class StoredDuplicateScanReport(
    val scannedCount: Int,
    val candidateRuns: Int,
    val unreadable: Int,
    val error: String?,
    val exactGroups: List<StoredDuplicateGroup>,
    val probableGroups: List<StoredProbableGroup>,
)

class DuplicateScanReportStore(private val rootDirectory: File) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(workId: String, report: StoredDuplicateScanReport) = withContext(Dispatchers.IO) {
        val directory = File(rootDirectory, "duplicate_scan_reports")
        check(directory.exists() || directory.mkdirs()) { "No se pudo crear el directorio de reportes" }
        val destination = File(directory, "${encodeKey(workId)}.json")
        val temporary = File(directory, "${destination.name}.tmp")
        val backup = File(directory, "${destination.name}.bak")
        FileOutputStream(temporary).use { output ->
            output.write(json.encodeToString(report).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            if (backup.exists() && !backup.delete()) error("No se pudo limpiar el reporte anterior")
            if (destination.exists() && !destination.renameTo(backup)) {
                error("No se pudo respaldar el reporte anterior")
            }
            if (!temporary.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("No se pudo publicar el reporte nuevo")
            }
            backup.delete()
        } finally {
            temporary.delete()
        }
    }

    suspend fun load(workId: String): StoredDuplicateScanReport? = withContext(Dispatchers.IO) {
        val file = File(File(rootDirectory, "duplicate_scan_reports"), "${encodeKey(workId)}.json")
        if (!file.isFile) return@withContext null
        runCatching { json.decodeFromString<StoredDuplicateScanReport>(file.readText(Charsets.UTF_8)) }
            .getOrNull()
    }

    private fun encodeKey(value: String): String = value.toByteArray(Charsets.UTF_8)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

fun VideosDataSource.DuplicateScanReport.toStored(): StoredDuplicateScanReport = StoredDuplicateScanReport(
    scannedCount = scannedCount,
    candidateRuns = candidateRuns,
    unreadable = unreadable,
    error = error,
    exactGroups = groups.map { group ->
        StoredDuplicateGroup(group.items.map(VideoItem::toStored), group.freedBytes)
    },
    probableGroups = probableGroups.map { group ->
        StoredProbableGroup(group.items.map(VideoItem::toStored), group.reason)
    },
)

fun StoredDuplicateScanReport.toDomain(): VideosDataSource.DuplicateScanReport = VideosDataSource.DuplicateScanReport(
    groups = exactGroups.map { group ->
        VideosDataSource.DuplicateGroup(group.items.map(StoredVideoItem::toDomain), group.freedBytes)
    },
    scannedCount = scannedCount,
    candidateRuns = candidateRuns,
    unreadable = unreadable,
    error = error,
    probableGroups = probableGroups.map { group ->
        VideosDataSource.ProbableGroup(group.items.map(StoredVideoItem::toDomain), group.reason)
    },
)

private fun VideoItem.toStored() = StoredVideoItem(
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

private fun StoredVideoItem.toDomain() = VideoItem(
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
