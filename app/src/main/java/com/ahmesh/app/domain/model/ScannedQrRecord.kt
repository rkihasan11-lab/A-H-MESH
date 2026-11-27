package com.ahmesh.app.domain.model

import kotlin.math.roundToInt

data class ScannedQrRecord(
    val id: Long = 0L,
    val nodeId: String,
    val displayName: String,
    val publicKeyFingerprint: String,
    val rawPayload: String,
    val scannedAt: Long,
    val isSuccess: Boolean = true,
    val failureReason: String? = null
)

data class QrScanStatistics(
    val totalScans: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0
) {
    val successRatePercent: Int
        get() = if (totalScans <= 0) 0 else ((successCount * 100f) / totalScans).roundToInt()

    val successRatio: Float
        get() = if (totalScans <= 0) 0f else (successCount.toFloat() / totalScans.toFloat()).coerceIn(0f, 1f)

    val failureRatio: Float
        get() = if (totalScans <= 0) 0f else (failureCount.toFloat() / totalScans.toFloat()).coerceIn(0f, 1f)
}
