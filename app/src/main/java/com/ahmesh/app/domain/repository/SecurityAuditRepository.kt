package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.BiometricAuditRecord
import kotlinx.coroutines.flow.Flow

interface SecurityAuditRepository {
    fun observePairingBiometricAuditLogs(): Flow<List<BiometricAuditRecord>>
    fun observeAllBiometricAuditLogs(): Flow<List<BiometricAuditRecord>>
    suspend fun getAllPairingBiometricAuditLogs(): List<BiometricAuditRecord>
    suspend fun recordPairingBiometricAttempt(record: BiometricAuditRecord)
    suspend fun clearPairingBiometricAuditLogs()
}
