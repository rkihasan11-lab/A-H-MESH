package com.ahmesh.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.data.apps.InstalledAppsProvider
import com.ahmesh.app.data.network.P2PSocketTransferManager
import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.data.storage.AhMeshStorageManager
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.InstalledAppInfo
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.PairingPayload
import com.ahmesh.app.domain.model.PendingOutgoingFile
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.TransferDirection
import com.ahmesh.app.domain.model.TransferStatus
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.model.WifiDirectPeer
import com.ahmesh.app.domain.model.WifiPeerConnectionStatus
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import com.ahmesh.app.presentation.screens.main.MainScreenContent
import com.ahmesh.app.presentation.screens.main.MainUiState
import com.ahmesh.app.presentation.screens.pairing.PairingFeedback
import com.ahmesh.app.presentation.screens.pairing.PairingScreenContent
import com.ahmesh.app.presentation.screens.pairing.PairingUiState
import com.ahmesh.app.presentation.screens.transfer.TransferScreenContent
import com.ahmesh.app.presentation.screens.transfer.TransferUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class P2PSocketTransferTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun socketManagerStreamsTextAndApkOverRealLoopbackTcpSocket() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val messageRepo = InMemoryMessageRepository()
        val storageManager = AhMeshStorageManager(context)
        val wifiRepo = RecordingWifiDirectRepository()
        val identityRepo = object : LocalNodeIdentityRepository(
            PairingPayload(
                nodeId = "ah-sender",
                displayName = "Sender Node",
                publicKey = "sender-key"
            )
        ) {}

        val transferManager = P2PSocketTransferManager(
            messageRepository = messageRepo,
            storageManager = storageManager,
            localIdentityRepository = identityRepo,
            wifiDirectRepository = wifiRepo
        )

        val port = transferManager.startServerOnPort(0)
        assertTrue(port > 0)
        assertTrue(transferManager.isServerListening.value)

        try {
            // 1. Stream text message over TCP socket
            val textResult = transferManager.sendTextMessage(
                conversationId = "conv-1",
                recipientId = "ah-receiver",
                targetHost = "127.0.0.1",
                targetPort = port,
                text = "Hello AH Mesh offline TCP!"
            )
            assertTrue(textResult.isSuccess)
            assertEquals(MessageStatus.DELIVERED, textResult.getOrThrow().status)

            // Wait briefly for server coroutine to commit incoming copy
            repeat(20) {
                if (messageRepo.savedMessages.value.size >= 2) return@repeat
                delay(25)
            }
            assertTrue(
                messageRepo.savedMessages.value.any {
                    it.ciphertext == "Hello AH Mesh offline TCP!" && it.status == MessageStatus.DELIVERED
                }
            )

            // 2. Stream a multi-chunk APK payload (72 KB) over TCP socket
            val sourceApk = File(context.cacheDir, "sample_mesh_plugin.apk")
            val payloadBytes = ByteArray(72 * 1024) { idx -> (idx % 251).toByte() }
            sourceApk.writeBytes(payloadBytes)

            val apkItem = PendingOutgoingFile(
                fileName = "SamplePlugin_v1.2.apk",
                mimeType = "application/vnd.android.package-archive",
                messageType = MessageType.APK,
                sizeBytes = payloadBytes.size.toLong(),
                localFilePath = sourceApk.absolutePath,
                packageName = "com.example.plugin"
            )

            val fileResult = transferManager.sendFileOrApk(
                conversationId = "conv-1",
                recipientId = "ah-receiver",
                targetHost = "127.0.0.1",
                targetPort = port,
                item = apkItem
            )
            assertTrue(fileResult.isSuccess)
            val completedTransfers = transferManager.activeTransfers.value.filter {
                it.status == TransferStatus.COMPLETED && it.fileName == "SamplePlugin_v1.2.apk"
            }
            assertTrue(completedTransfers.isNotEmpty())
            assertEquals(payloadBytes.size.toLong(), completedTransfers.first().bytesTransferred)
            sourceApk.delete()
        } finally {
            transferManager.stopServer()
        }
    }

    @Test
    fun installedAppsProviderFormatsSanitizedApkMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = InstalledAppsProvider(context)
        val pending = provider.toPendingOutgoingApk(
            InstalledAppInfo(
                packageName = "com.ahmesh.app",
                appName = "AH Mesh",
                versionName = "1.0.0",
                sourceApkPath = "/data/app/base.apk",
                apkSizeBytes = 12_500_000L
            )
        )

        assertEquals("AH_Mesh_v1.0.0.apk", pending.fileName)
        assertEquals(MessageType.APK, pending.messageType)
        assertEquals("application/vnd.android.package-archive", pending.mimeType)
        assertEquals(12_500_000L, pending.sizeBytes)
    }

    @Test
    fun transferScreenDisplaysLiveProgressLimitChipsAndOversizedWarningDialog() {
        var confirmedOversized = false
        var showWarning by mutableStateOf(false)
        val baseState = TransferUiState(
            peerName = "Galaxy-Peer",
            targetHost = "192.168.49.1",
            isServerListening = true,
            serverPort = 8988,
            activeTransfers = listOf(
                ActiveTransferState(
                    transferId = "tr-1",
                    fileName = "mesh_backup.zip",
                    mimeType = "application/zip",
                    messageType = MessageType.FILE,
                    direction = TransferDirection.OUTGOING,
                    status = TransferStatus.TRANSFERRING,
                    bytesTransferred = 5L * 1024L * 1024L,
                    totalBytes = 10L * 1024L * 1024L,
                    peerLabel = "Galaxy-Peer"
                )
            ),
            storageFolderDisplay = "Download/AH Mesh"
        )
        val oversizedItem = PendingOutgoingFile(
            fileName = "big_installer.apk",
            mimeType = "application/vnd.android.package-archive",
            messageType = MessageType.APK,
            sizeBytes = 80L * 1024L * 1024L
        )

        composeRule.setContent {
            AHMeshTheme {
                TransferScreenContent(
                    uiState = baseState.copy(
                        pendingOversizedItem = if (showWarning) oversizedItem else null
                    ),
                    onNavigateBack = {},
                    onTargetHostChange = {},
                    onMessageDraftChange = {},
                    onSendMessage = {},
                    onPickFileClick = {},
                    onOpenApkPickerClick = {},
                    onSelectLimitMb = {},
                    onClearCompleted = {},
                    onConfirmOversized = { confirmedOversized = true },
                    onDismissOversized = {},
                    onDismissError = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_port_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_target_host_input").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_limit_chip_50").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_lazy_list").performScrollToKey("tr_tr-1")
        composeRule.onNodeWithTag("transfer_progress_card_tr-1").assertIsDisplayed()
        composeRule.onNodeWithText("50%").assertIsDisplayed()

        showWarning = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_size_warning_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_size_warning_confirm").performClick()
        assertTrue(confirmedOversized)
    }

    @Test
    fun mainScreenDisplaysWifiDirectLinkAndDiscoveredPeerConnectAction() {
        var connectedAddress: String? = null
        composeRule.setContent {
            AHMeshTheme {
                MainScreenContent(
                    uiState = MainUiState(
                        networkStatus = P2PConnectionStatus(
                            state = P2PConnectionState.CONNECTED,
                            transportMode = P2PTransportMode.WIFI_DIRECT,
                            isWifiEnabled = true,
                            isWifiDirectSupported = true,
                            hasNearbyPermission = true,
                            connectedPeersCount = 1,
                            discoveredPeersCount = 1
                        ),
                        discoveredWifiPeers = listOf(
                            WifiDirectPeer(
                                deviceName = "Pixel-Relay",
                                deviceAddress = "aa:bb:cc:dd:ee:01",
                                status = WifiPeerConnectionStatus.AVAILABLE
                            )
                        ),
                        linkInfo = WifiDirectLinkInfo(
                            isGroupFormed = true,
                            isGroupOwner = false,
                            groupOwnerIp = "192.168.49.1",
                            peerIp = "192.168.49.1",
                            isSocketServerRunning = true,
                            serverPort = 8988,
                            connectedPeerName = "Pixel-Relay"
                        )
                    ),
                    isAppLockEnabled = true,
                    onOpenSecuritySettings = {},
                    onNetworkActionClick = {},
                    onRefreshNetwork = {},
                    onStopDiscovery = {},
                    onToggleLanguage = {},
                    onOpenPairing = {},
                    onConnectWifiPeer = { connectedAddress = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("main_lazy_list").performScrollToKey("wifi_direct_session_card")
        composeRule.onNodeWithTag("wifi_direct_session_card").assertIsDisplayed()
        composeRule.onNodeWithTag("main_lazy_list").performScrollToKey("wifi_peer_aa:bb:cc:dd:ee:01")
        composeRule.onNodeWithTag("wifi_peer_card_aa:bb:cc:dd:ee:01").assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_peer_action_aa:bb:cc:dd:ee:01").performClick()
        assertEquals("aa:bb:cc:dd:ee:01", connectedAddress)
    }

    @Test
    fun mainScreenLiveMeshStatusCardDisplaysTransportPeersSocketAndRecentActivityWithoutPhase1Text() {
        var transferOpened = false
        composeRule.setContent {
            AHMeshTheme {
                MainScreenContent(
                    uiState = MainUiState(
                        networkStatus = P2PConnectionStatus(
                            state = P2PConnectionState.READY,
                            transportMode = P2PTransportMode.WIFI_DIRECT,
                            isWifiEnabled = true,
                            isWifiDirectSupported = true,
                            hasNearbyPermission = true,
                            connectedPeersCount = 1,
                            discoveredPeersCount = 3
                        ),
                        isSocketServerListening = true,
                        serverPort = 8988,
                        lastScannedQr = ScannedQrRecord(
                            nodeId = "ah-nova99",
                            displayName = "Nova-Mesh",
                            publicKeyFingerprint = "A1B2C3D4",
                            rawPayload = "AHMESH1:nova",
                            scannedAt = 1_760_000_000_000L,
                            isSuccess = true
                        )
                    ),
                    isAppLockEnabled = true,
                    onOpenSecuritySettings = {},
                    onNetworkActionClick = {},
                    onRefreshNetwork = {},
                    onStopDiscovery = {},
                    onToggleLanguage = {},
                    onOpenPairing = {},
                    onOpenTransfer = { _, _, _ -> transferOpened = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("main_lazy_list").performScrollToKey("live_mesh_status_card")
        composeRule.onNodeWithTag("empty_mesh_placeholder").assertIsDisplayed()
        composeRule.onNodeWithTag("live_mesh_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText("Offline P2P Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Transport: Wi-Fi Direct").assertIsDisplayed()
        composeRule.onNodeWithText("1 linked • 3 nearby").assertIsDisplayed()
        composeRule.onNodeWithText("TCP :8988 Listening").assertIsDisplayed()
        composeRule.onNodeWithText("Last Paired Peer: Nova-Mesh (ah-nova99)").assertIsDisplayed()
        composeRule.onNodeWithText("Phase 1 foundation initialized", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("live_mesh_transfer_button").performClick()
        assertTrue(transferOpened)
    }

    @Test
    fun pairingScreenBannerAndTrustedPeerOpenTransferDirectly() {
        var openedPeerId: String? = null
        var openedPeerName: String? = null
        val peer = User(
            id = "ah-peer77",
            displayName = "Orchid-Node",
            publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsample",
            avatarColor = 0xFF0284C7L,
            lastSeenAt = 1_760_000_000_000L,
            isTrusted = true
        )

        composeRule.setContent {
            AHMeshTheme {
                PairingScreenContent(
                    uiState = PairingUiState(
                        trustedPeers = listOf(peer),
                        trustedPeersCount = 1,
                        pairingFeedback = PairingFeedback.Paired(peer = peer)
                    ),
                    biometricCapability = com.ahmesh.app.domain.model.BiometricCapability.AVAILABLE,
                    onNavigateBack = {},
                    onRequestQrUnlock = {},
                    onOpenBiometricSetup = {},
                    onLockQr = {},
                    onDisplayNameChange = {},
                    onRotateSession = {},
                    onManualCodeChange = {},
                    onManualSubmit = {},
                    onQrCodeDetected = {},
                    onScanFailed = {},
                    onRetryScan = {},
                    onClearScanHistory = {},
                    onClearBiometricAuditLogs = {},
                    onSaveAuditLog = {},
                    onShareAuditLog = {},
                    onDismissFeedback = {},
                    onOpenPeerTransfer = { id, name ->
                        openedPeerId = id
                        openedPeerName = name
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pairing_feedback_open_transfer_button")
            .performScrollTo()
            .performClick()
        assertEquals("ah-peer77", openedPeerId)
        assertEquals("Orchid-Node", openedPeerName)

        openedPeerId = null
        openedPeerName = null
        composeRule.onNodeWithTag("pairing_trusted_peer_ah-peer77")
            .performScrollTo()
            .performClick()
        assertEquals("ah-peer77", openedPeerId)
        assertEquals("Orchid-Node", openedPeerName)
    }

    @Test
    fun socketIncomingNormalizesLocalRecipientConversationIdToSenderAndRegistersPeer() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiverMessageRepo = InMemoryMessageRepository()
        val storageManager = AhMeshStorageManager(context)
        val wifiRepo = RecordingWifiDirectRepository()

        val receiverIdentity = object : LocalNodeIdentityRepository(
            PairingPayload(
                nodeId = "ah-bob",
                displayName = "Bob Node",
                publicKey = "bob-key"
            )
        ) {}
        val senderIdentity = object : LocalNodeIdentityRepository(
            PairingPayload(
                nodeId = "ah-alice",
                displayName = "Alice Node",
                publicKey = "alice-key"
            )
        ) {}

        val receiverServer = P2PSocketTransferManager(
            messageRepository = receiverMessageRepo,
            storageManager = storageManager,
            localIdentityRepository = receiverIdentity,
            wifiDirectRepository = wifiRepo
        )
        val senderClient = P2PSocketTransferManager(
            messageRepository = InMemoryMessageRepository(),
            storageManager = storageManager,
            localIdentityRepository = senderIdentity,
            wifiDirectRepository = wifiRepo
        )

        val port = receiverServer.startServerOnPort(0)
        try {
            val sendResult = senderClient.sendTextMessage(
                conversationId = "ah-bob",
                recipientId = "ah-bob",
                targetHost = "127.0.0.1",
                targetPort = port,
                text = "Ping from Alice"
            )
            assertTrue(sendResult.isSuccess)

            repeat(20) {
                if (receiverMessageRepo.savedMessages.value.isNotEmpty()) return@repeat
                delay(25)
            }

            val received = receiverMessageRepo.savedMessages.value.single()
            assertEquals("ah-alice", received.conversationId)
            assertEquals("ah-alice", received.senderId)
            assertTrue(receiverMessageRepo.knownUsers.value.any { it.id == "ah-alice" && it.displayName == "Alice Node" })
        } finally {
            receiverServer.stopServer()
        }
    }

    @Test
    fun transferScreenDisplaysOpenableApkBubbleAndRefreshAction() {
        var refreshed = false
        var openedMessage: P2PMessage? = null
        val apkMsg = P2PMessage(
            id = "msg-apk-1",
            conversationId = "ah-alice",
            senderId = "ah-alice",
            recipientId = "ah-bob",
            ciphertext = "SensorTool_v2.apk",
            type = MessageType.APK,
            status = MessageStatus.DELIVERED,
            payloadUri = "content://downloads/public_downloads/1",
            payloadSize = 4_200_000L,
            timestamp = 1_760_000_000_000L
        )

        composeRule.setContent {
            AHMeshTheme {
                TransferScreenContent(
                    uiState = TransferUiState(
                        conversationId = "ah-alice",
                        localNodeId = "ah-bob",
                        recipientId = "ah-alice",
                        peerName = "Alice Node",
                        targetHost = "192.168.49.1",
                        messages = listOf(apkMsg)
                    ),
                    onNavigateBack = {},
                    onTargetHostChange = {},
                    onMessageDraftChange = {},
                    onSendMessage = {},
                    onPickFileClick = {},
                    onOpenApkPickerClick = {},
                    onSelectLimitMb = {},
                    onClearCompleted = {},
                    onConfirmOversized = {},
                    onDismissOversized = {},
                    onDismissError = {},
                    onRefreshConnection = { refreshed = true },
                    onOpenAttachment = { openedMessage = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_refresh_link_button").performClick()
        assertTrue(refreshed)

        composeRule.onNodeWithTag("transfer_lazy_list").performScrollToKey("msg_msg-apk-1")
        composeRule.onNodeWithText("Tap to Open APK").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_message_item_msg-apk-1").performClick()
        assertEquals("msg-apk-1", openedMessage?.id)
    }

    private class InMemoryMessageRepository : MessageRepository {
        val savedMessages = MutableStateFlow<List<P2PMessage>>(emptyList())
        val knownUsers = MutableStateFlow<List<User>>(emptyList())

        override fun observeConversation(conversationId: String): Flow<List<P2PMessage>> =
            savedMessages.map { list -> list.filter { it.conversationId == conversationId } }

        override fun observeAllMessages(): Flow<List<P2PMessage>> = savedMessages.asStateFlow()
        override fun observeAllUsers(): Flow<List<User>> = knownUsers.asStateFlow()
        override fun observeScannedQrHistory(): Flow<List<ScannedQrRecord>> = MutableStateFlow(emptyList())
        override fun observeQrScanStatistics(): Flow<QrScanStatistics> =
            MutableStateFlow(QrScanStatistics())

        override suspend fun saveMessage(message: P2PMessage) {
            savedMessages.update { current ->
                current.filterNot { it.id == message.id } + message
            }
        }

        override suspend fun upsertUser(user: User) {
            knownUsers.update { current -> current.filterNot { it.id == user.id } + user }
        }

        override suspend fun ensurePeerKnown(user: User) {
            knownUsers.update { current ->
                if (current.any { it.id == user.id }) current else current + user
            }
        }

        override suspend fun recordScannedQr(record: ScannedQrRecord) = Unit
        override suspend fun clearScannedQrHistory() = Unit
        override suspend fun deleteMessage(messageId: String) = Unit
    }

    private class RecordingWifiDirectRepository : WifiDirectRepository {
        override val discoveredPeers: StateFlow<List<WifiDirectPeer>> = MutableStateFlow(emptyList())
        override val linkInfo: StateFlow<WifiDirectLinkInfo> = MutableStateFlow(WifiDirectLinkInfo())
        override val connectionNotice: StateFlow<String?> = MutableStateFlow(null)
        override fun connectToPeer(deviceAddress: String) = Unit
        override fun cancelOrDisconnect() = Unit
        override fun refreshConnectionInfo() = Unit
        override fun registerResolvedPeerIp(ipAddress: String) = Unit
        override fun updateSocketServerState(isRunning: Boolean, port: Int) = Unit
        override fun clearNotice() = Unit
    }
}
