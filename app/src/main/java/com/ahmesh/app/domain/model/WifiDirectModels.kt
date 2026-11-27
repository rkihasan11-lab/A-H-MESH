package com.ahmesh.app.domain.model

const val DEFAULT_P2P_SOCKET_PORT = 8988

enum class WifiPeerConnectionStatus {
    AVAILABLE,
    INVITED,
    CONNECTED,
    FAILED,
    UNAVAILABLE
}

data class WifiDirectPeer(
    val deviceName: String,
    val deviceAddress: String,
    val primaryDeviceType: String = "",
    val isGroupOwner: Boolean = false,
    val status: WifiPeerConnectionStatus = WifiPeerConnectionStatus.AVAILABLE
)

data class WifiDirectLinkInfo(
    val isGroupFormed: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerIp: String? = null,
    val peerIp: String? = null,
    val localP2pIp: String? = null,
    val serverPort: Int = DEFAULT_P2P_SOCKET_PORT,
    val isSocketServerRunning: Boolean = false,
    val connectedPeerName: String? = null,
    val connectedPeerAddress: String? = null,
    val lastError: String? = null
) {
    val suggestedTargetHost: String?
        get() = when {
            !isGroupFormed -> peerIp ?: groupOwnerIp
            isGroupOwner -> peerIp
            else -> groupOwnerIp
        }
}
