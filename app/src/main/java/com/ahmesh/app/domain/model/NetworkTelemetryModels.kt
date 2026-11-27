package com.ahmesh.app.domain.model

enum class TelemetryEventType {
    STATE_CHANGE,
    TRANSFER_COMPLETE,
    TRANSFER_FAILED,
    PERIODIC_PROBE
}

enum class TelemetryTimeRange(
    val durationMs: Long?,
    val bucketCount: Int
) {
    LAST_15_MIN(15L * 60L * 1000L, 6),
    LAST_HOUR(60L * 60L * 1000L, 6),
    LAST_24_HOURS(24L * 60L * 60L * 1000L, 8),
    ALL_TIME(null, 8)
}

enum class TrafficChartMetric {
    THROUGHPUT_KB,
    TRANSFER_COUNT
}

data class NetworkTelemetryRecord(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val connectionState: P2PConnectionState,
    val transportMode: P2PTransportMode,
    val stabilityScore: Int,
    val discoveredPeersCount: Int,
    val connectedPeersCount: Int,
    val bytesTransferred: Long = 0L,
    val direction: String = DIRECTION_SNAPSHOT,
    val eventType: TelemetryEventType = TelemetryEventType.STATE_CHANGE
) {
    companion object {
        const val DIRECTION_SNAPSHOT = "SNAPSHOT"
        const val DIRECTION_OUTGOING = "OUTGOING"
        const val DIRECTION_INCOMING = "INCOMING"

        fun computeStabilityScore(
            status: P2PConnectionStatus,
            isGroupFormed: Boolean = false
        ): Int {
            return when (status.state) {
                P2PConnectionState.CONNECTED -> {
                    val base = if (isGroupFormed) 96 else 90
                    (base + minOf(status.connectedPeersCount, 4)).coerceIn(0, 100)
                }
                P2PConnectionState.DISCOVERING -> {
                    val peerBonus = minOf(status.discoveredPeersCount * 3, 12)
                    (74 + peerBonus).coerceIn(0, 92)
                }
                P2PConnectionState.READY -> {
                    when {
                        status.transportMode == P2PTransportMode.WIFI_DIRECT && status.hasNearbyPermission -> 82
                        status.transportMode == P2PTransportMode.LOCAL_LAN -> 76
                        else -> 65
                    }
                }
                P2PConnectionState.PERMISSION_REQUIRED -> 42
                P2PConnectionState.OFFLINE -> 12
            }
        }
    }
}

data class TrafficBucketPoint(
    val label: String,
    val outgoingBytes: Long,
    val incomingBytes: Long,
    val transferCount: Int,
    val failedCount: Int,
    val bucketStartMs: Long,
    val bucketEndMs: Long
) {
    val totalBytes: Long
        get() = outgoingBytes + incomingBytes
}

data class StabilityTimelinePoint(
    val label: String,
    val stabilityPercent: Int,
    val connectedPeers: Int,
    val discoveredPeers: Int,
    val state: P2PConnectionState,
    val transportMode: P2PTransportMode,
    val timestamp: Long
)

data class TransportDistributionItem(
    val transportMode: P2PTransportMode,
    val sampleCount: Int,
    val sharePercent: Int
)
