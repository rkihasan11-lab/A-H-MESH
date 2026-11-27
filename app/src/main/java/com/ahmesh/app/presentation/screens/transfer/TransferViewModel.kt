package com.ahmesh.app.presentation.screens.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.data.apps.InstalledAppsProvider
import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.data.storage.AhMeshStorageManager
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.DEFAULT_P2P_SOCKET_PORT
import com.ahmesh.app.domain.model.InstalledAppInfo
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.PendingOutgoingFile
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferUiState(
    val conversationId: String = DEFAULT_CONVERSATION_ID,
    val localNodeId: String = "",
    val recipientId: String = DEFAULT_RECIPIENT_ID,
    val peerName: String = DEFAULT_PEER_NAME,
    val targetHost: String = "",
    val linkInfo: WifiDirectLinkInfo = WifiDirectLinkInfo(),
    val isServerListening: Boolean = false,
    val serverPort: Int = DEFAULT_P2P_SOCKET_PORT,
    val messages: List<P2PMessage> = emptyList(),
    val activeTransfers: List<ActiveTransferState> = emptyList(),
    val messageDraft: String = "",
    val fileSizeLimitBytes: Long = TransferSizeLimits.DEFAULT_WARNING_BYTES,
    val pendingOversizedItem: PendingOutgoingFile? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appSearchQuery: String = "",
    val storageFolderDisplay: String = "",
    val errorMessage: String? = null
) {
    val fileSizeLimitMb: Int
        get() = (fileSizeLimitBytes / (1024L * 1024L)).toInt().coerceAtLeast(1)

    val filteredInstalledApps: List<InstalledAppInfo>
        get() {
            val q = appSearchQuery.trim().lowercase()
            if (q.isBlank()) return installedApps
            return installedApps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

    companion object {
        const val DEFAULT_CONVERSATION_ID = "ah_mesh_direct"
        const val DEFAULT_RECIPIENT_ID = "ah_peer"
        const val DEFAULT_PEER_NAME = "Nearby Peer"
    }
}

private data class LocalTransferControls(
    val conversationId: String = TransferUiState.DEFAULT_CONVERSATION_ID,
    val recipientId: String = TransferUiState.DEFAULT_RECIPIENT_ID,
    val peerName: String = TransferUiState.DEFAULT_PEER_NAME,
    val targetHostOverride: String? = null,
    val messageDraft: String = "",
    val pendingOversizedItem: PendingOutgoingFile? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appSearchQuery: String = "",
    val errorMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferRepository: P2PTransferRepository,
    private val wifiDirectRepository: WifiDirectRepository,
    private val messageRepository: MessageRepository,
    private val localIdentityRepository: LocalNodeIdentityRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val storageManager: AhMeshStorageManager
) : ViewModel() {

    private val controls = MutableStateFlow(LocalTransferControls())

    private val conversationMessages = controls
        .flatMapLatest { state ->
            combine(
                messageRepository.observeConversation(state.conversationId),
                messageRepository.observeAllMessages().onStart { emit(emptyList()) }
            ) { scopedMessages, allMessages ->
                val isDirectOrMacSession = state.conversationId == TransferUiState.DEFAULT_CONVERSATION_ID ||
                    state.conversationId.contains(':')
                val extraMatched = if (isDirectOrMacSession) {
                    allMessages.filter {
                        it.conversationId == state.conversationId ||
                            it.senderId == state.recipientId ||
                            it.recipientId == state.recipientId ||
                            it.conversationId == TransferUiState.DEFAULT_CONVERSATION_ID
                    }
                } else {
                    allMessages.filter {
                        it.conversationId == state.conversationId ||
                            it.senderId == state.conversationId ||
                            it.recipientId == state.conversationId
                    }
                }
                (scopedMessages + extraMatched)
                    .distinctBy { it.id }
                    .sortedBy { it.timestamp }
            }
        }

    private val networkSnapshotFlow = combine(
        wifiDirectRepository.linkInfo,
        transferRepository.isServerListening,
        transferRepository.serverPort,
        transferRepository.activeTransfers,
        transferRepository.fileSizeWarningThresholdBytes
    ) { linkInfo, isListening, port, transfers, sizeLimit ->
        NetworkSnapshot(
            linkInfo = linkInfo,
            isServerListening = isListening,
            serverPort = port,
            activeTransfers = transfers,
            fileSizeLimitBytes = sizeLimit
        )
    }

    val uiState: StateFlow<TransferUiState> = combine(
        controls,
        conversationMessages,
        networkSnapshotFlow,
        localIdentityRepository.localIdentity
    ) { local, messages, net, identity ->
        val resolvedHost = local.targetHostOverride
            ?: net.linkInfo.suggestedTargetHost.orEmpty()
        TransferUiState(
            conversationId = local.conversationId,
            localNodeId = identity.nodeId,
            recipientId = local.recipientId,
            peerName = net.linkInfo.connectedPeerName?.takeIf { it.isNotBlank() } ?: local.peerName,
            targetHost = resolvedHost,
            linkInfo = net.linkInfo,
            isServerListening = net.isServerListening,
            serverPort = net.serverPort,
            messages = messages,
            activeTransfers = net.activeTransfers,
            messageDraft = local.messageDraft,
            fileSizeLimitBytes = net.fileSizeLimitBytes,
            pendingOversizedItem = local.pendingOversizedItem,
            installedApps = local.installedApps,
            isLoadingApps = local.isLoadingApps,
            appSearchQuery = local.appSearchQuery,
            storageFolderDisplay = storageManager.getAppSafeStorageFolderPath(),
            errorMessage = local.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransferUiState(
            localNodeId = localIdentityRepository.localIdentity.value.nodeId,
            storageFolderDisplay = storageManager.getAppSafeStorageFolderPath()
        )
    )

    init {
        transferRepository.ensureServerStarted()
        wifiDirectRepository.refreshConnectionInfo()
    }

    fun refreshConnection() {
        transferRepository.ensureServerStarted()
        wifiDirectRepository.refreshConnectionInfo()
    }

    fun setPeerContext(peerId: String?, peerName: String?, host: String?) {
        controls.update { current ->
            val resolvedConversation = peerId?.takeIf { it.isNotBlank() }
                ?: TransferUiState.DEFAULT_CONVERSATION_ID
            val resolvedRecipient = peerId?.takeIf { it.isNotBlank() }
                ?: TransferUiState.DEFAULT_RECIPIENT_ID
            val resolvedName = peerName?.takeIf { it.isNotBlank() }
                ?: TransferUiState.DEFAULT_PEER_NAME
            val resolvedHost = host?.takeIf { it.isNotBlank() } ?: current.targetHostOverride
            current.copy(
                conversationId = resolvedConversation,
                recipientId = resolvedRecipient,
                peerName = resolvedName,
                targetHostOverride = resolvedHost
            )
        }
    }

    fun onTargetHostChange(host: String) {
        controls.update { it.copy(targetHostOverride = host) }
    }

    fun onMessageDraftChange(text: String) {
        controls.update { it.copy(messageDraft = text) }
    }

    fun setFileSizeLimitMb(limitMb: Int) {
        val clamped = limitMb.coerceIn(1, 2048)
        transferRepository.setFileSizeWarningThresholdBytes(clamped * 1024L * 1024L)
    }

    fun sendTextMessage() {
        val state = uiState.value
        val draft = state.messageDraft.trim()
        if (draft.isBlank()) return
        val host = state.targetHost.trim()
        if (host.isBlank()) {
            controls.update {
                it.copy(errorMessage = "Connect a Wi-Fi Direct peer or enter a peer IP address first.")
            }
            return
        }

        controls.update { it.copy(messageDraft = "", errorMessage = null) }
        viewModelScope.launch {
            val result = transferRepository.sendTextMessage(
                conversationId = state.conversationId,
                recipientId = state.recipientId,
                targetHost = host,
                targetPort = state.serverPort,
                text = draft
            )
            result.onFailure { error ->
                controls.update {
                    it.copy(errorMessage = error.message ?: "Failed to send P2P message.")
                }
            }
        }
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val inspected = storageManager.inspectPickedUri(uri)
            if (inspected == null) {
                controls.update {
                    it.copy(errorMessage = "Could not inspect the selected file.")
                }
                return@launch
            }
            submitOutgoingFileWithSizeCheck(inspected)
        }
    }

    fun loadInstalledApps() {
        if (controls.value.isLoadingApps) return
        controls.update { it.copy(isLoadingApps = true) }
        viewModelScope.launch {
            val apps = installedAppsProvider.loadLaunchableApps()
            controls.update {
                it.copy(
                    installedApps = apps,
                    isLoadingApps = false
                )
            }
        }
    }

    fun onAppSearchQueryChange(query: String) {
        controls.update { it.copy(appSearchQuery = query) }
    }

    fun onInstalledAppSelected(app: InstalledAppInfo) {
        val pendingApk = installedAppsProvider.toPendingOutgoingApk(app)
        submitOutgoingFileWithSizeCheck(pendingApk)
    }

    private fun submitOutgoingFileWithSizeCheck(item: PendingOutgoingFile) {
        val state = uiState.value
        if (state.targetHost.isBlank()) {
            controls.update {
                it.copy(errorMessage = "Connect a Wi-Fi Direct peer or enter a peer IP address before sending.")
            }
            return
        }
        val limit = state.fileSizeLimitBytes
        if (item.sizeBytes > limit) {
            controls.update { it.copy(pendingOversizedItem = item) }
        } else {
            dispatchFileTransfer(item)
        }
    }

    fun confirmOversizedTransfer() {
        val pending = controls.value.pendingOversizedItem ?: return
        controls.update { it.copy(pendingOversizedItem = null) }
        dispatchFileTransfer(pending)
    }

    fun dismissOversizedWarning() {
        controls.update { it.copy(pendingOversizedItem = null) }
    }

    private fun dispatchFileTransfer(item: PendingOutgoingFile) {
        val state = uiState.value
        val host = state.targetHost.trim()
        if (host.isBlank()) {
            controls.update {
                it.copy(errorMessage = "Target peer IP address is required for socket transfer.")
            }
            return
        }
        controls.update { it.copy(errorMessage = null) }
        viewModelScope.launch {
            val result = transferRepository.sendFileOrApk(
                conversationId = state.conversationId,
                recipientId = state.recipientId,
                targetHost = host,
                targetPort = state.serverPort,
                item = item
            )
            result.onFailure { error ->
                controls.update {
                    it.copy(errorMessage = error.message ?: "Socket file transfer failed.")
                }
            }
        }
    }

    fun clearCompletedTransfers() {
        transferRepository.clearCompletedTransfers()
    }

    fun dismissError() {
        controls.update { it.copy(errorMessage = null) }
    }

    private data class NetworkSnapshot(
        val linkInfo: WifiDirectLinkInfo,
        val isServerListening: Boolean,
        val serverPort: Int,
        val activeTransfers: List<ActiveTransferState>,
        val fileSizeLimitBytes: Long
    )
}
