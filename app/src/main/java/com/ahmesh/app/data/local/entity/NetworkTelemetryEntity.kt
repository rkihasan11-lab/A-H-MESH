package com.ahmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.TelemetryEventType

@Entity(
    tableName = "network_telemetry_samples",
    indices = [Index(value = ["timestamp"])]
)
data class NetworkTelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val connectionState: String,
    val transportMode: String,
    val stabilityScore: Int,
    val discoveredPeersCount: Int,
    val connectedPeersCount: Int,
    val bytesTransferred: Long,
    val direction: String,
    val eventType: String
) {
    fun toDomain(): NetworkTelemetryRecord = NetworkTelemetryRecord(
        id = id,
        timestamp = timestamp,
        connectionState = runCatching { P2PConnectionState.valueOf(connectionState) }
            .getOrDefault(P2PConnectionState.READY),
        transportMode = runCatching { P2PTransportMode.valueOf(transportMode) }
            .getOrDefault(P2PTransportMode.STANDBY),
        stabilityScore = stabilityScore.coerceIn(0, 100),
        discoveredPeersCount = discoveredPeersCount.coerceAtLeast(0),
        connectedPeersCount = connectedPeersCount.coerceAtLeast(0),
        bytesTransferred = bytesTransferred.coerceAtLeast(0L),
        direction = direction,
        eventType = runCatching { TelemetryEventType.valueOf(eventType) }
            .getOrDefault(TelemetryEventType.STATE_CHANGE)
    )

    companion object {
        fun fromDomain(record: NetworkTelemetryRecord): NetworkTelemetryEntity = NetworkTelemetryEntity(
            id = record.id,
            timestamp = record.timestamp,
            connectionState = record.connectionState.name,
            transportMode = record.transportMode.name,
            stabilityScore = record.stabilityScore.coerceIn(0, 100),
            discoveredPeersCount = record.discoveredPeersCount.coerceAtLeast(0),
            connectedPeersCount = record.connectedPeersCount.coerceAtLeast(0),
            bytesTransferred = record.bytesTransferred.coerceAtLeast(0L),
            direction = record.direction,
            eventType = record.eventType.name
        )
    }
}
