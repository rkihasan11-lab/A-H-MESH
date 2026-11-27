package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import kotlinx.coroutines.flow.Flow

interface NetworkTelemetryRepository {
    fun observeAllTelemetry(): Flow<List<NetworkTelemetryRecord>>
    suspend fun recordSample(record: NetworkTelemetryRecord)
    suspend fun recordLiveProbe()
    suspend fun clearTelemetryHistory()
}
