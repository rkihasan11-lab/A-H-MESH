package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.model.WifiDirectPeer
import kotlinx.coroutines.flow.StateFlow

interface WifiDirectRepository {
    val discoveredPeers: StateFlow<List<WifiDirectPeer>>
    val linkInfo: StateFlow<WifiDirectLinkInfo>
    val connectionNotice: StateFlow<String?>

    fun connectToPeer(deviceAddress: String)
    fun cancelOrDisconnect()
    fun refreshConnectionInfo()
    fun registerResolvedPeerIp(ipAddress: String)
    fun updateSocketServerState(isRunning: Boolean, port: Int)
    fun clearNotice()
}
