package com.ahmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ahmesh.app.data.local.entity.BiometricAuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BiometricAuditLogDao {
    @Query("SELECT * FROM biometric_audit_logs ORDER BY attemptedAt DESC, id DESC LIMIT 50")
    fun observeAuditLogs(): Flow<List<BiometricAuditLogEntity>>

    @Query("SELECT * FROM biometric_audit_logs ORDER BY attemptedAt DESC, id DESC")
    fun observeAllAuditLogs(): Flow<List<BiometricAuditLogEntity>>

    @Query("SELECT * FROM biometric_audit_logs ORDER BY attemptedAt DESC, id DESC")
    suspend fun getAllAuditLogsOnce(): List<BiometricAuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entry: BiometricAuditLogEntity)

    @Query("DELETE FROM biometric_audit_logs")
    suspend fun clearAuditLogs()
}
