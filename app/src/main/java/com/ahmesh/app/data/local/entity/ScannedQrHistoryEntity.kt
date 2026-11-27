package com.ahmesh.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ahmesh.app.domain.model.ScannedQrRecord

@Entity(tableName = "scanned_qr_history")
data class ScannedQrHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nodeId: String,
    val displayName: String,
    val publicKeyFingerprint: String,
    val rawPayload: String,
    val scannedAt: Long,
    @ColumnInfo(defaultValue = "1") val isSuccess: Boolean = true,
    val failureReason: String? = null
) {
    fun toDomain(): ScannedQrRecord = ScannedQrRecord(
        id = id,
        nodeId = nodeId,
        displayName = displayName,
        publicKeyFingerprint = publicKeyFingerprint,
        rawPayload = rawPayload,
        scannedAt = scannedAt,
        isSuccess = isSuccess,
        failureReason = failureReason
    )

    companion object {
        fun fromDomain(record: ScannedQrRecord): ScannedQrHistoryEntity = ScannedQrHistoryEntity(
            id = record.id,
            nodeId = record.nodeId,
            displayName = record.displayName,
            publicKeyFingerprint = record.publicKeyFingerprint,
            rawPayload = record.rawPayload,
            scannedAt = record.scannedAt,
            isSuccess = record.isSuccess,
            failureReason = record.failureReason
        )
    }
}
