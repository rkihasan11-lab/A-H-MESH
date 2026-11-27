package com.ahmesh.app.domain.model

import java.util.UUID

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class TransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELED
}

data class ActiveTransferState(
    val transferId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val messageType: MessageType,
    val direction: TransferDirection,
    val status: TransferStatus,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val peerLabel: String,
    val savedUri: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = when {
            totalBytes > 0L -> (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            status == TransferStatus.COMPLETED -> 1f
            else -> 0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val sourceApkPath: String,
    val apkSizeBytes: Long
)

data class PendingOutgoingFile(
    val fileName: String,
    val mimeType: String,
    val messageType: MessageType,
    val sizeBytes: Long,
    val uriString: String? = null,
    val localFilePath: String? = null,
    val packageName: String? = null
)

object TransferSizeLimits {
    const val DEFAULT_WARNING_BYTES = 50L * 1024L * 1024L // 50 MB
    val PRESET_LIMITS_MB = listOf(10, 25, 50, 100, 250)

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(java.util.Locale.US, "%.2f GB", gb)
    }
}
