package com.ahmesh.app.domain.model

enum class LiveStreamWindow(val maxPoints: Int) {
    WINDOW_30S(20),
    WINDOW_60S(40),
    WINDOW_2M(80)
}

data class LiveSeriesVisibility(
    val showStability: Boolean = true,
    val showOutgoingTraffic: Boolean = true,
    val showIncomingTraffic: Boolean = true
)

data class LiveMetricSample(
    val timestamp: Long,
    val timeLabel: String,
    val stabilityPercent: Int,
    val outgoingKbPerTick: Float,
    val incomingKbPerTick: Float,
    val connectedPeers: Int,
    val discoveredPeers: Int,
    val activeTransfersCount: Int,
    val connectionState: P2PConnectionState,
    val transportMode: P2PTransportMode
) {
    val totalTrafficKb: Float
        get() = outgoingKbPerTick + incomingKbPerTick
}
