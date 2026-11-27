package com.ahmesh.app.data.repository

import com.ahmesh.app.data.local.dao.BiometricAuditLogDao
import com.ahmesh.app.data.local.entity.BiometricAuditLogEntity
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.repository.SecurityAuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityAuditRepositoryImpl @Inject constructor(
    private val biometricAuditLogDao: BiometricAuditLogDao
) : SecurityAuditRepository {

    override fun observePairingBiometricAuditLogs(): Flow<List<BiometricAuditRecord>> {
        return biometricAuditLogDao.observeAuditLogs().map { entries ->
            entries.map { it.toDomain() }
        }
    }

    override fun observeAllBiometricAuditLogs(): Flow<List<BiometricAuditRecord>> {
        return biometricAuditLogDao.observeAllAuditLogs().map { entries ->
            entries.map { it.toDomain() }
        }
    }

    override suspend fun getAllPairingBiometricAuditLogs(): List<BiometricAuditRecord> {
        return biometricAuditLogDao.getAllAuditLogsOnce().map { it.toDomain() }
    }

    override suspend fun recordPairingBiometricAttempt(record: BiometricAuditRecord) {
        biometricAuditLogDao.insertAuditLog(BiometricAuditLogEntity.fromDomain(record))
    }

    override suspend fun clearPairingBiometricAuditLogs() {
        biometricAuditLogDao.clearAuditLogs()
    }
}
