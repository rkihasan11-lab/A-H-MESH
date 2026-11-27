package com.ahmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord

@Entity(tableName = "biometric_audit_logs")
data class BiometricAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val outcome: BiometricAuditOutcome,
    val isSuccess: Boolean,
    val authMethod: String,
    val detail: String?,
    val attemptedAt: Long
) {
    fun toDomain(): BiometricAuditRecord = BiometricAuditRecord(
        id = id,
        outcome = outcome,
        isSuccess = isSuccess,
        authMethod = authMethod,
        detail = detail,
        attemptedAt = attemptedAt
    )

    companion object {
        fun fromDomain(record: BiometricAuditRecord): BiometricAuditLogEntity =
            BiometricAuditLogEntity(
                id = record.id,
                outcome = record.outcome,
                isSuccess = record.isSuccess,
                authMethod = record.authMethod,
                detail = record.detail,
                attemptedAt = record.attemptedAt
            )
    }
}
