package com.ahmesh.app.domain.model

enum class P2PConnectionState {
    CONNECTED,
    DISCOVERING,
    READY,
    PERMISSION_REQUIRED,
    OFFLINE
}

enum class P2PTransportMode {
    WIFI_DIRECT,
    LOCAL_LAN,
    STANDBY
}

data class P2PConnectionStatus(
    val state: P2PConnectionState = P2PConnectionState.READY,
    val transportMode: P2PTransportMode = P2PTransportMode.STANDBY,
    val isWifiEnabled: Boolean = false,
    val isWifiDirectSupported: Boolean = false,
    val hasNearbyPermission: Boolean = false,
    val isDiscovering: Boolean = false,
    val connectedPeersCount: Int = 0,
    val discoveredPeersCount: Int = 0
)
