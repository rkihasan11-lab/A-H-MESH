package com.ahmesh.app.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ahmesh.app.domain.model.DEFAULT_P2P_SOCKET_PORT
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.model.WifiDirectPeer
import com.ahmesh.app.domain.model.WifiPeerConnectionStatus
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.WifiDirectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidWifiDirectManager @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkStatusMonitor, WifiDirectRepository {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val wifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val p2pChannel: WifiP2pManager.Channel? = runCatching {
        wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
    }.getOrNull()

    private val isWifiDirectSupported: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    private var isP2pFrameworkEnabled: Boolean = false
    private var isDiscovering: Boolean = false
    private var isGroupFormed: Boolean = false
    private var discoveredPeersCount: Int = 0
    private var connectedPeersCount: Int = 0

    private val _status = MutableStateFlow(computeStatus())
    override val status: StateFlow<P2PConnectionStatus> = _status.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<WifiDirectPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<WifiDirectPeer>> = _discoveredPeers.asStateFlow()

    private val _linkInfo = MutableStateFlow(WifiDirectLinkInfo())
    override val linkInfo: StateFlow<WifiDirectLinkInfo> = _linkInfo.asStateFlow()

    private val _connectionNotice = MutableStateFlow<String?>(null)
    override val connectionNotice: StateFlow<String?> = _connectionNotice.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshStatus()
        override fun onLost(network: Network) = refreshStatus()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = refreshStatus()
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    isP2pFrameworkEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!isP2pFrameworkEnabled) {
                        isDiscovering = false
                        discoveredPeersCount = 0
                        connectedPeersCount = 0
                        isGroupFormed = false
                        _discoveredPeers.value = emptyList()
                        _linkInfo.update { current ->
                            current.copy(
                                isGroupFormed = false,
                                isGroupOwner = false,
                                groupOwnerIp = null,
                                peerIp = null,
                                localP2pIp = null,
                                connectedPeerName = null,
                                connectedPeerAddress = null
                            )
                        }
                    }
                    refreshStatus()
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestCurrentPeers()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val p2pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                            WifiP2pInfo::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    }
                    applyWifiP2pInfo(p2pInfo)
                    requestCurrentPeers()
                    refreshConnectionInfo()
                    refreshStatus()
                }

                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val discoveryState = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
                    )
                    isDiscovering = discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    refreshStatus()
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    refreshStatus()
                }

                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    refreshStatus()
                }
            }
        }
    }

    init {
        registerCallbacks()
        refreshStatus()
        refreshConnectionInfo()
    }

    private fun registerCallbacks() {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                p2pReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun refreshStatus() {
        _status.value = computeStatus()
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (!hasNearbyPermission()) {
            refreshStatus()
            return
        }
        val manager = wifiP2pManager
        val channel = p2pChannel
        if (manager != null && channel != null && isWifiDirectSupported) {
            isDiscovering = true
            refreshStatus()
            try {
                manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        isDiscovering = true
                        requestCurrentPeers()
                        refreshStatus()
                    }

                    override fun onFailure(reason: Int) {
                        isDiscovering = false
                        _connectionNotice.value = mapP2pFailureReason(reason)
                        refreshStatus()
                    }
                })
            } catch (_: SecurityException) {
                isDiscovering = false
                refreshStatus()
            }
        } else {
            refreshStatus()
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        isDiscovering = false
        val manager = wifiP2pManager
        val channel = p2pChannel
        if (manager != null && channel != null) {
            runCatching {
                manager.stopPeerDiscovery(channel, null)
            }
        }
        refreshStatus()
    }

    @SuppressLint("MissingPermission")
    override fun connectToPeer(deviceAddress: String) {
        if (!hasNearbyPermission()) {
            _connectionNotice.value = "Nearby Wi-Fi permission is required before pairing via Wi-Fi Direct."
            refreshStatus()
            return
        }
        val manager = wifiP2pManager
        val channel = p2pChannel
        if (manager == null || channel == null || !isWifiDirectSupported) {
            _connectionNotice.value = "Wi-Fi Direct hardware is unavailable on this device."
            return
        }

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }

        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _discoveredPeers.update { peers ->
                        peers.map { peer ->
                            if (peer.deviceAddress == deviceAddress) {
                                peer.copy(status = WifiPeerConnectionStatus.INVITED)
                            } else {
                                peer
                            }
                        }
                    }
                    _connectionNotice.value = null
                    refreshConnectionInfo()
                }

                override fun onFailure(reason: Int) {
                    _connectionNotice.value = mapP2pFailureReason(reason)
                    refreshStatus()
                }
            })
        } catch (_: SecurityException) {
            _connectionNotice.value = "Permission denied while initiating Wi-Fi Direct connection."
        }
    }

    @SuppressLint("MissingPermission")
    override fun cancelOrDisconnect() {
        val manager = wifiP2pManager
        val channel = p2pChannel
        if (manager != null && channel != null) {
            runCatching {
                manager.cancelConnect(channel, null)
            }
            runCatching {
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        resetGroupState()
                    }

                    override fun onFailure(reason: Int) {
                        resetGroupState()
                    }
                })
            }.onFailure {
                resetGroupState()
            }
        } else {
            resetGroupState()
        }
    }

    @SuppressLint("MissingPermission")
    override fun refreshConnectionInfo() {
        val manager = wifiP2pManager ?: return
        val channel = p2pChannel ?: return
        runCatching {
            manager.requestConnectionInfo(channel) { info ->
                applyWifiP2pInfo(info)
                refreshStatus()
            }
        }
        if (hasNearbyPermission()) {
            runCatching {
                manager.requestGroupInfo(channel) { group ->
                    if (group != null) {
                        val firstClient = group.clientList.firstOrNull()
                        val peerName = if (group.isGroupOwner) {
                            firstClient?.deviceName
                        } else {
                            group.owner?.deviceName
                        }
                        val peerAddr = if (group.isGroupOwner) {
                            firstClient?.deviceAddress
                        } else {
                            group.owner?.deviceAddress
                        }
                        _linkInfo.update { current ->
                            current.copy(
                                connectedPeerName = peerName ?: current.connectedPeerName,
                                connectedPeerAddress = peerAddr ?: current.connectedPeerAddress
                            )
                        }
                    }
                }
            }
        }
    }

    override fun registerResolvedPeerIp(ipAddress: String) {
        val trimmed = ipAddress.trim()
        if (trimmed.isBlank()) return
        _linkInfo.update { current ->
            if (current.peerIp == trimmed) current else current.copy(peerIp = trimmed)
        }
    }

    override fun updateSocketServerState(isRunning: Boolean, port: Int) {
        _linkInfo.update { current ->
            current.copy(
                isSocketServerRunning = isRunning,
                serverPort = if (port > 0) port else DEFAULT_P2P_SOCKET_PORT
            )
        }
    }

    override fun clearNotice() {
        _connectionNotice.value = null
    }

    private fun resetGroupState() {
        isGroupFormed = false
        connectedPeersCount = 0
        _linkInfo.update { current ->
            current.copy(
                isGroupFormed = false,
                isGroupOwner = false,
                groupOwnerIp = null,
                peerIp = null,
                localP2pIp = null,
                connectedPeerName = null,
                connectedPeerAddress = null,
                lastError = null
            )
        }
        requestCurrentPeers()
        refreshStatus()
    }

    private fun applyWifiP2pInfo(p2pInfo: WifiP2pInfo?) {
        val formed = p2pInfo?.groupFormed == true
        isGroupFormed = formed
        if (formed) {
            connectedPeersCount = maxOf(connectedPeersCount, 1)
        } else {
            connectedPeersCount = 0
        }

        val ownerIp = p2pInfo?.groupOwnerAddress?.hostAddress
        val localIp = resolveLocalP2pIpAddress()
        _linkInfo.update { current ->
            current.copy(
                isGroupFormed = formed,
                isGroupOwner = p2pInfo?.isGroupOwner == true,
                groupOwnerIp = ownerIp,
                localP2pIp = localIp,
                peerIp = if (formed && p2pInfo?.isGroupOwner == false) ownerIp else if (!formed) null else current.peerIp,
                connectedPeerName = if (formed) current.connectedPeerName else null,
                connectedPeerAddress = if (formed) current.connectedPeerAddress else null
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentPeers() {
        if (!hasNearbyPermission()) return
        val manager = wifiP2pManager ?: return
        val channel = p2pChannel ?: return
        try {
            manager.requestPeers(channel) { peerList ->
                val devices = peerList?.deviceList.orEmpty()
                val mappedPeers = devices.map { device ->
                    WifiDirectPeer(
                        deviceName = device.deviceName?.takeIf { it.isNotBlank() } ?: device.deviceAddress,
                        deviceAddress = device.deviceAddress.orEmpty(),
                        primaryDeviceType = device.primaryDeviceType.orEmpty(),
                        isGroupOwner = device.isGroupOwner,
                        status = device.status.toPeerStatus()
                    )
                }.sortedBy { it.status.ordinal }

                _discoveredPeers.value = mappedPeers
                discoveredPeersCount = mappedPeers.size
                connectedPeersCount = mappedPeers.count {
                    it.status == WifiPeerConnectionStatus.CONNECTED
                }.let { count ->
                    if (isGroupFormed) maxOf(count, 1) else count
                }
                val connectedPeer = mappedPeers.firstOrNull { it.status == WifiPeerConnectionStatus.CONNECTED }
                if (connectedPeer != null) {
                    _linkInfo.update { current ->
                        current.copy(
                            connectedPeerName = connectedPeer.deviceName,
                            connectedPeerAddress = connectedPeer.deviceAddress
                        )
                    }
                }
                refreshStatus()
            }
        } catch (_: SecurityException) {
            refreshStatus()
        }
    }

    private fun computeStatus(): P2PConnectionStatus {
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val hasLocalWifiNetwork = hasActiveWifiOrLocalTransport()
        val radioActive = wifiEnabled || hasLocalWifiNetwork || isP2pFrameworkEnabled
        val permissionGranted = hasNearbyPermission()

        val transportMode = when {
            isWifiDirectSupported && (isP2pFrameworkEnabled || wifiEnabled) -> P2PTransportMode.WIFI_DIRECT
            hasLocalWifiNetwork -> P2PTransportMode.LOCAL_LAN
            else -> P2PTransportMode.STANDBY
        }

        val state = when {
            !radioActive -> P2PConnectionState.OFFLINE
            connectedPeersCount > 0 || isGroupFormed -> P2PConnectionState.CONNECTED
            !permissionGranted && isWifiDirectSupported -> P2PConnectionState.PERMISSION_REQUIRED
            isDiscovering -> P2PConnectionState.DISCOVERING
            else -> P2PConnectionState.READY
        }

        return P2PConnectionStatus(
            state = state,
            transportMode = transportMode,
            isWifiEnabled = radioActive,
            isWifiDirectSupported = isWifiDirectSupported,
            hasNearbyPermission = permissionGranted,
            isDiscovering = isDiscovering,
            connectedPeersCount = connectedPeersCount,
            discoveredPeersCount = discoveredPeersCount
        )
    }

    private fun hasActiveWifiOrLocalTransport(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
    }

    private fun hasNearbyPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun resolveLocalP2pIpAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val p2pMatch = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .map { iface.name.lowercase() to it.hostAddress.orEmpty() }
                }
            p2pMatch.firstOrNull { (name, ip) ->
                name.contains("p2p") || ip.startsWith("192.168.49.")
            }?.second ?: p2pMatch.firstOrNull { (_, ip) ->
                ip.startsWith("192.168.") || ip.startsWith("10.")
            }?.second
        }.getOrNull()
    }

    private fun Int.toPeerStatus(): WifiPeerConnectionStatus = when (this) {
        WifiP2pDevice.CONNECTED -> WifiPeerConnectionStatus.CONNECTED
        WifiP2pDevice.INVITED -> WifiPeerConnectionStatus.INVITED
        WifiP2pDevice.FAILED -> WifiPeerConnectionStatus.FAILED
        WifiP2pDevice.UNAVAILABLE -> WifiPeerConnectionStatus.UNAVAILABLE
        else -> WifiPeerConnectionStatus.AVAILABLE
    }

    private fun mapP2pFailureReason(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is unsupported on this hardware."
        WifiP2pManager.BUSY -> "Wi-Fi Direct radio is busy. Try again shortly."
        WifiP2pManager.ERROR -> "Wi-Fi Direct framework error occurred. Toggle Wi-Fi and retry."
        else -> "Wi-Fi Direct operation failed (code $reason)."
    }
}
