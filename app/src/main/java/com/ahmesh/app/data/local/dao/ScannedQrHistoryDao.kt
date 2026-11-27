package com.ahmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ahmesh.app.data.local.entity.ScannedQrHistoryEntity
import kotlinx.coroutines.flow.Flow

data class QrScanStatsTuple(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int
)

@Dao
interface ScannedQrHistoryDao {
    @Query("SELECT * FROM scanned_qr_history WHERE isSuccess = 1 ORDER BY scannedAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<ScannedQrHistoryEntity>>

    @Query(
        """
        SELECT 
            COUNT(*) AS totalCount,
            COALESCE(SUM(CASE WHEN isSuccess = 1 THEN 1 ELSE 0 END), 0) AS successCount,
            COALESCE(SUM(CASE WHEN isSuccess = 0 THEN 1 ELSE 0 END), 0) AS failureCount
        FROM scanned_qr_history
        """
    )
    fun observeScanStats(): Flow<QrScanStatsTuple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(entry: ScannedQrHistoryEntity)

    @Query("DELETE FROM scanned_qr_history")
    suspend fun clearHistory()
}
