package com.ahmesh.app

import com.ahmesh.app.core.localization.AppLanguage
import com.ahmesh.app.core.localization.LanguageManager
import com.ahmesh.app.core.theme.AppThemeMode
import com.ahmesh.app.core.theme.ThemeManager
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.DEFAULT_P2P_SOCKET_PORT
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.PendingOutgoingFile
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.model.WifiDirectPeer
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import com.ahmesh.app.presentation.screens.main.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun themeManagerTogglesAndSetsDarkAndLightModes() {
        val manager = InMemoryThemeManager()
        assertEquals(AppThemeMode.DARK, manager.themeMode.value)
        assertTrue(manager.isDarkMode)

        manager.toggleTheme()
        assertEquals(AppThemeMode.LIGHT, manager.themeMode.value)
        assertFalse(manager.isDarkMode)

        manager.setDarkMode(true)
        assertEquals(AppThemeMode.DARK, manager.themeMode.value)
        assertTrue(manager.isDarkMode)

        manager.setDarkMode(false)
        assertEquals(AppThemeMode.LIGHT, manager.themeMode.value)
        assertFalse(manager.isDarkMode)
    }

    @Test
    fun mainViewModelPropagatesThemeChangesToUiState() = runTest {
        val themeManager = InMemoryThemeManager()
        val viewModel = MainViewModel(
            networkStatusMonitor = FakeNetworkStatusMonitor(),
            wifiDirectRepository = FakeWifiDirectRepository(),
            transferRepository = FakeP2PTransferRepository(),
            messageRepository = NoOpMessageRepository(),
            languageManager = FakeLanguageManager(),
            themeManager = themeManager
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertTrue(viewModel.uiState.value.isDarkMode)

        viewModel.setDarkMode(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDarkMode)
        assertEquals(AppThemeMode.LIGHT, themeManager.themeMode.value)

        viewModel.toggleTheme()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDarkMode)
        assertEquals(AppThemeMode.DARK, themeManager.themeMode.value)

        collectJob.cancel()
    }

    private class InMemoryThemeManager : ThemeManager {
        private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
        override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

        override fun setThemeMode(mode: AppThemeMode) {
            _themeMode.value = mode
        }
    }

    private class FakeLanguageManager : LanguageManager {
        private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
        override val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

        override fun setLanguage(language: AppLanguage) {
            _currentLanguage.value = language
        }
    }

    private class FakeNetworkStatusMonitor : NetworkStatusMonitor {
        private val _status = MutableStateFlow(P2PConnectionStatus())
        override val status: StateFlow<P2PConnectionStatus> = _status.asStateFlow()
        override fun refreshStatus() = Unit
        override fun startDiscovery() = Unit
        override fun stopDiscovery() = Unit
    }

    private class NoOpMessageRepository : MessageRepository {
        override fun observeConversation(conversationId: String): Flow<List<P2PMessage>> =
            MutableStateFlow(emptyList())

        override fun observeAllUsers(): Flow<List<User>> = MutableStateFlow(emptyList())
        override fun observeScannedQrHistory(): Flow<List<ScannedQrRecord>> = MutableStateFlow(emptyList())
        override fun observeQrScanStatistics(): Flow<QrScanStatistics> =
            MutableStateFlow(QrScanStatistics())

        override suspend fun saveMessage(message: P2PMessage) = Unit
        override suspend fun upsertUser(user: User) = Unit
        override suspend fun recordScannedQr(record: ScannedQrRecord) = Unit
        override suspend fun clearScannedQrHistory() = Unit
        override suspend fun deleteMessage(messageId: String) = Unit
    }

    private class FakeWifiDirectRepository : WifiDirectRepository {
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

    private class FakeP2PTransferRepository : P2PTransferRepository {
        override val activeTransfers: StateFlow<List<ActiveTransferState>> = MutableStateFlow(emptyList())
        override val isServerListening: StateFlow<Boolean> = MutableStateFlow(false)
        override val serverPort: StateFlow<Int> = MutableStateFlow(DEFAULT_P2P_SOCKET_PORT)
        override val fileSizeWarningThresholdBytes: StateFlow<Long> =
            MutableStateFlow(TransferSizeLimits.DEFAULT_WARNING_BYTES)
        override fun setFileSizeWarningThresholdBytes(bytes: Long) = Unit
        override fun ensureServerStarted() = Unit
        override fun stopServer() = Unit
        override suspend fun sendTextMessage(
            conversationId: String,
            recipientId: String,
            targetHost: String,
            targetPort: Int,
            text: String
        ): Result<P2PMessage> = Result.failure(UnsupportedOperationException())

        override suspend fun sendFileOrApk(
            conversationId: String,
            recipientId: String,
            targetHost: String,
            targetPort: Int,
            item: PendingOutgoingFile
        ): Result<P2PMessage> = Result.failure(UnsupportedOperationException())

        override fun clearCompletedTransfers() = Unit
    }
}
