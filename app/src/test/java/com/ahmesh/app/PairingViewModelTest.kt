package com.ahmesh.app

import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.PairingCodec
import com.ahmesh.app.domain.model.PairingPayload
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.SecurityAuditRepository
import com.ahmesh.app.domain.security.AuditExportFormat
import com.ahmesh.app.domain.security.AuditExportPayload
import com.ahmesh.app.presentation.screens.pairing.PairingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

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
    fun biometricSuccessAndFailureAttemptsAreLoggedToAuditRepository() = runTest {
        val identityRepo = FakeLocalNodeIdentityRepository()
        val messageRepo = FakeMessageRepository()
        val auditRepo = FakeSecurityAuditRepository()
        val viewModel = PairingViewModel(identityRepo, messageRepo, auditRepo)

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertFalse(viewModel.uiState.value.isQrUnlocked)
        assertTrue(viewModel.uiState.value.biometricAuditLogs.isEmpty())

        // Failed biometric scan attempt
        viewModel.onQrBiometricError(
            message = "Verification was not recognized.",
            outcome = BiometricAuditOutcome.FAILED_ATTEMPT
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isQrUnlocked)
        assertEquals("Verification was not recognized.", viewModel.uiState.value.qrAuthErrorMessage)
        assertEquals(1, viewModel.uiState.value.biometricAuditLogs.size)
        assertEquals(
            BiometricAuditOutcome.FAILED_ATTEMPT,
            viewModel.uiState.value.biometricAuditLogs[0].outcome
        )
        assertFalse(viewModel.uiState.value.biometricAuditLogs[0].isSuccess)

        // Canceled attempt
        viewModel.onQrBiometricError(
            message = "Authentication canceled.",
            outcome = BiometricAuditOutcome.CANCELED
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.biometricAuditLogs.size)
        assertEquals(
            BiometricAuditOutcome.CANCELED,
            viewModel.uiState.value.biometricAuditLogs[0].outcome
        )

        // Successful biometric unlock
        viewModel.onQrBiometricUnlocked()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isQrUnlocked)
        assertNull(viewModel.uiState.value.qrAuthErrorMessage)
        assertEquals(3, viewModel.uiState.value.biometricAuditLogs.size)
        assertEquals(
            BiometricAuditOutcome.SUCCEEDED,
            viewModel.uiState.value.biometricAuditLogs[0].outcome
        )
        assertTrue(viewModel.uiState.value.biometricAuditLogs[0].isSuccess)

        // Locking QR keeps audit logs intact
        viewModel.lockQrCode()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isQrUnlocked)
        assertEquals(3, viewModel.uiState.value.biometricAuditLogs.size)

        // Export CSV and Text before clearing
        val csvExport = viewModel.buildAuditExport(AuditExportFormat.CSV)
        assertEquals(3, csvExport?.recordCount)
        assertTrue(csvExport?.fileName?.endsWith(".csv") == true)
        assertTrue(csvExport?.content?.contains("SUCCEEDED") == true)
        assertTrue(csvExport?.content?.contains("FAILED_ATTEMPT") == true)

        val textExport = viewModel.buildAuditExport(AuditExportFormat.TEXT)
        assertEquals(3, textExport?.recordCount)
        assertTrue(textExport?.fileName?.endsWith(".txt") == true)
        assertTrue(textExport?.content?.contains("AH Mesh - Biometric Security Audit Log") == true)

        // Clearing audit logs empties the list
        viewModel.clearBiometricAuditLogs()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.biometricAuditLogs.isEmpty())
        assertNull(viewModel.buildAuditExport(AuditExportFormat.CSV))

        collectJob.cancel()
    }

    @Test
    fun qrScanStatisticsTrackSuccessfulAndFailedScans() = runTest {
        val identityRepo = FakeLocalNodeIdentityRepository()
        val messageRepo = FakeMessageRepository()
        val auditRepo = FakeSecurityAuditRepository()
        val viewModel = PairingViewModel(identityRepo, messageRepo, auditRepo)

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Initial empty stats
        assertEquals(0, viewModel.uiState.value.qrScanStats.totalScans)
        assertEquals(0, viewModel.uiState.value.qrScanStats.successRatePercent)

        // 1 failed scan (invalid QR)
        viewModel.onQrCodeScanned("not-a-valid-qr-code")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.qrScanStats.totalScans)
        assertEquals(0, viewModel.uiState.value.qrScanStats.successCount)
        assertEquals(1, viewModel.uiState.value.qrScanStats.failureCount)
        assertEquals(0, viewModel.uiState.value.qrScanStats.successRatePercent)
        assertTrue(viewModel.uiState.value.scanHistory.isEmpty())

        // 1 scanner failure
        viewModel.onScannerFailed()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.qrScanStats.totalScans)
        assertEquals(2, viewModel.uiState.value.qrScanStats.failureCount)

        // 2 successful peer scans
        val peer1 = PairingCodec.encode(
            PairingPayload(
                nodeId = "ah-peer01",
                displayName = "Peer One",
                publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEpeerOneValidKeyPayload12345"
            )
        )
        val peer2 = PairingCodec.encode(
            PairingPayload(
                nodeId = "ah-peer02",
                displayName = "Peer Two",
                publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEpeerTwoValidKeyPayload67890"
            )
        )
        viewModel.onQrCodeScanned(peer1)
        viewModel.onQrCodeScanned(peer2)
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.qrScanStats.totalScans)
        assertEquals(2, viewModel.uiState.value.qrScanStats.successCount)
        assertEquals(2, viewModel.uiState.value.qrScanStats.failureCount)
        assertEquals(50, viewModel.uiState.value.qrScanStats.successRatePercent)
        assertEquals(2, viewModel.uiState.value.scanHistory.size)
        assertTrue(viewModel.uiState.value.scanHistory.all { it.isSuccess && it.scannedAt > 0L })

        viewModel.clearScanHistory()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.scanHistory.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun biometricEventTypesArePreservedForEventsTable() = runTest {
        val identityRepo = FakeLocalNodeIdentityRepository()
        val messageRepo = FakeMessageRepository()
        val auditRepo = FakeSecurityAuditRepository()
        val viewModel = PairingViewModel(identityRepo, messageRepo, auditRepo)

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onQrBiometricUnlocked(eventType = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK)
        viewModel.onQrBiometricError(
            message = "Device lock failed",
            outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
            eventType = PairingViewModel.EVENT_QR_DEVICE_UNLOCK
        )
        viewModel.onBiometricSetupAttempt(
            outcome = BiometricAuditOutcome.SUCCEEDED,
            detail = null
        )
        advanceUntilIdle()

        val logs = viewModel.uiState.value.biometricAuditLogs
        assertEquals(3, logs.size)
        assertEquals(PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY, logs[0].authMethod)
        assertTrue(logs[0].isSuccess)
        assertEquals(PairingViewModel.EVENT_QR_DEVICE_UNLOCK, logs[1].authMethod)
        assertFalse(logs[1].isSuccess)
        assertEquals(PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK, logs[2].authMethod)
        assertTrue(logs[2].isSuccess)

        collectJob.cancel()
    }

    @Test
    fun handshakeBiometricAttemptRecordsHandshakeAuditLog() = runTest {
        val identityRepo = FakeLocalNodeIdentityRepository()
        val messageRepo = FakeMessageRepository()
        val auditRepo = FakeSecurityAuditRepository()
        val viewModel = PairingViewModel(
            localIdentityRepository = identityRepo,
            messageRepository = messageRepo,
            securityAuditRepository = auditRepo
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onHandshakeBiometricAttempt(
            outcome = BiometricAuditOutcome.SUCCEEDED,
            detail = "Handshake QR Authorized"
        )
        advanceUntilIdle()

        val logs = viewModel.uiState.value.biometricAuditLogs
        assertEquals(1, logs.size)
        assertEquals(PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH, logs[0].authMethod)
        assertTrue(logs[0].isSuccess)
        assertEquals(BiometricAuditOutcome.SUCCEEDED, logs[0].outcome)
        assertEquals("Handshake QR Authorized", logs[0].detail)

        collectJob.cancel()
    }

    private class FakeLocalNodeIdentityRepository : LocalNodeIdentityRepository(
        PairingPayload(
            nodeId = "ah-test01",
            displayName = "Test-Node",
            publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestKeyPayload1234567890"
        )
    )

    private class FakeSecurityAuditRepository : SecurityAuditRepository {
        private var nextId = 1L
        private val logs = MutableStateFlow<List<BiometricAuditRecord>>(emptyList())

        override fun observePairingBiometricAuditLogs(): Flow<List<BiometricAuditRecord>> =
            logs.asStateFlow()

        override fun observeAllBiometricAuditLogs(): Flow<List<BiometricAuditRecord>> =
            logs.asStateFlow()

        override suspend fun getAllPairingBiometricAuditLogs(): List<BiometricAuditRecord> =
            logs.value

        override suspend fun recordPairingBiometricAttempt(record: BiometricAuditRecord) {
            val saved = record.copy(id = nextId++)
            logs.value = listOf(saved) + logs.value
        }

        override suspend fun clearPairingBiometricAuditLogs() {
            logs.value = emptyList()
        }
    }

    private class FakeMessageRepository : MessageRepository {
        private val users = MutableStateFlow<List<User>>(emptyList())
        private val history = MutableStateFlow<List<ScannedQrRecord>>(emptyList())

        override fun observeConversation(conversationId: String): Flow<List<P2PMessage>> =
            MutableStateFlow(emptyList())

        override fun observeAllUsers(): Flow<List<User>> = users.asStateFlow()

        override fun observeScannedQrHistory(): Flow<List<ScannedQrRecord>> =
            history.map { list -> list.filter { it.isSuccess } }

        override fun observeQrScanStatistics(): Flow<QrScanStatistics> =
            history.map { list ->
                val passed = list.count { it.isSuccess }
                val failed = list.size - passed
                QrScanStatistics(
                    totalScans = list.size,
                    successCount = passed,
                    failureCount = failed
                )
            }

        override suspend fun saveMessage(message: P2PMessage) = Unit

        override suspend fun upsertUser(user: User) {
            users.value = users.value.filterNot { it.id == user.id } + user
        }

        override suspend fun recordScannedQr(record: ScannedQrRecord) {
            history.value = listOf(record) + history.value
        }

        override suspend fun clearScannedQrHistory() {
            history.value = emptyList()
        }

        override suspend fun deleteMessage(messageId: String) = Unit
    }
}
