package com.ahmesh.app.domain.model

enum class BiometricAuditOutcome {
    SUCCEEDED,
    FAILED_ATTEMPT,
    CANCELED,
    ERROR
}

data class BiometricAuditRecord(
    val id: Long = 0L,
    val outcome: BiometricAuditOutcome,
    val isSuccess: Boolean,
    val authMethod: String,
    val detail: String? = null,
    val attemptedAt: Long = System.currentTimeMillis()
)
