package com.ahmesh.app.presentation.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.core.localization.AppLanguage
import com.ahmesh.app.core.localization.LanguageManager
import com.ahmesh.app.core.theme.AppThemeMode
import com.ahmesh.app.core.theme.ThemeManager
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.DEFAULT_P2P_SOCKET_PORT
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.model.WifiDirectPeer
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainUiState(
    val networkStatus: P2PConnectionStatus = P2PConnectionStatus(),
    val peers: List<User> = emptyList(),
    val discoveredWifiPeers: List<WifiDirectPeer> = emptyList(),
    val linkInfo: WifiDirectLinkInfo = WifiDirectLinkInfo(),
    val activeTransfers: List<ActiveTransferState> = emptyList(),
    val isSocketServerListening: Boolean = false,
    val serverPort: Int = DEFAULT_P2P_SOCKET_PORT,
    val lastScannedQr: ScannedQrRecord? = null,
    val recentMessage: P2PMessage? = null,
    val totalMessagesCount: Int = 0,
    val connectionNotice: String? = null,
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    val isDarkMode: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val networkStatusMonitor: NetworkStatusMonitor,
    private val wifiDirectRepository: WifiDirectRepository,
    private val transferRepository: P2PTransferRepository,
    messageRepository: MessageRepository,
    private val languageManager: LanguageManager,
    private val themeManager: ThemeManager
) : ViewModel() {

    private val p2pSnapshotFlow = combine(
        networkStatusMonitor.status,
        wifiDirectRepository.discoveredPeers,
        wifiDirectRepository.linkInfo,
        wifiDirectRepository.connectionNotice,
        transferRepository.activeTransfers
    ) { status, discoveredPeers, linkInfo, notice, transfers ->
        P2PSnapshot(
            status = status,
            discoveredPeers = discoveredPeers,
            linkInfo = linkInfo,
            notice = notice,
            transfers = transfers
        )
    }

    private val activitySnapshotFlow = combine(
        transferRepository.isServerListening,
        transferRepository.serverPort,
        messageRepository.observeScannedQrHistory().onStart { emit(emptyList()) },
        messageRepository.observeAllMessages().onStart { emit(emptyList()) }
    ) { isListening, port, scanHistory, allMessages ->
        ActivitySnapshot(
            isSocketServerListening = isListening,
            serverPort = port,
            lastScannedQr = scanHistory.firstOrNull(),
            recentMessage = allMessages.maxByOrNull { it.timestamp },
            totalMessagesCount = allMessages.size
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        p2pSnapshotFlow,
        activitySnapshotFlow,
        messageRepository.observeAllUsers(),
        languageManager.currentLanguage,
        themeManager.themeMode
    ) { p2p, activity, users, language, themeMode ->
        MainUiState(
            networkStatus = p2p.status,
            peers = users,
            discoveredWifiPeers = p2p.discoveredPeers,
            linkInfo = p2p.linkInfo,
            activeTransfers = p2p.transfers,
            isSocketServerListening = activity.isSocketServerListening,
            serverPort = activity.serverPort,
            lastScannedQr = activity.lastScannedQr,
            recentMessage = activity.recentMessage,
            totalMessagesCount = activity.totalMessagesCount,
            connectionNotice = p2p.notice,
            currentLanguage = language,
            isDarkMode = themeMode == AppThemeMode.DARK
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            networkStatus = networkStatusMonitor.status.value,
            discoveredWifiPeers = wifiDirectRepository.discoveredPeers.value,
            linkInfo = wifiDirectRepository.linkInfo.value,
            activeTransfers = transferRepository.activeTransfers.value,
            isSocketServerListening = transferRepository.isServerListening.value,
            serverPort = transferRepository.serverPort.value,
            connectionNotice = wifiDirectRepository.connectionNotice.value,
            currentLanguage = languageManager.currentLanguage.value,
            isDarkMode = themeManager.themeMode.value == AppThemeMode.DARK
        )
    )

    init {
        transferRepository.ensureServerStarted()
    }

    fun toggleDiscovery() {
        val current = networkStatusMonitor.status.value
        if (current.isDiscovering) {
            networkStatusMonitor.stopDiscovery()
        } else {
            networkStatusMonitor.startDiscovery()
        }
    }

    fun stopDiscovery() {
        networkStatusMonitor.stopDiscovery()
    }

    fun refreshNetworkStatus() {
        networkStatusMonitor.refreshStatus()
        wifiDirectRepository.refreshConnectionInfo()
    }

    fun connectToWifiPeer(deviceAddress: String) {
        wifiDirectRepository.connectToPeer(deviceAddress)
    }

    fun disconnectWifiDirect() {
        wifiDirectRepository.cancelOrDisconnect()
    }

    fun dismissConnectionNotice() {
        wifiDirectRepository.clearNotice()
    }

    fun toggleLanguage() {
        val next = when (languageManager.currentLanguage.value) {
            AppLanguage.ENGLISH -> AppLanguage.BENGALI
            AppLanguage.BENGALI -> AppLanguage.ENGLISH
        }
        languageManager.setLanguage(next)
    }

    fun setDarkMode(enabled: Boolean) {
        themeManager.setDarkMode(enabled)
    }

    fun toggleTheme() {
        themeManager.toggleTheme()
    }

    private data class P2PSnapshot(
        val status: P2PConnectionStatus,
        val discoveredPeers: List<WifiDirectPeer>,
        val linkInfo: WifiDirectLinkInfo,
        val notice: String?,
        val transfers: List<ActiveTransferState>
    )

    private data class ActivitySnapshot(
        val isSocketServerListening: Boolean,
        val serverPort: Int,
        val lastScannedQr: ScannedQrRecord?,
        val recentMessage: P2PMessage?,
        val totalMessagesCount: Int
    )
}
