package com.ahmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ahmesh.app.data.local.entity.NetworkTelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkTelemetryDao {
    @Query("SELECT * FROM network_telemetry_samples ORDER BY timestamp ASC")
    fun observeAllSamples(): Flow<List<NetworkTelemetryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: NetworkTelemetryEntity)

    @Query("DELETE FROM network_telemetry_samples")
    suspend fun clearAll()
}
