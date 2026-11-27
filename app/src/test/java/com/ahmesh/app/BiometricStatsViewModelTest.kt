package com.ahmesh.app

import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.repository.SecurityAuditRepository
import com.ahmesh.app.presentation.screens.biometricstats.BiometricStatsViewModel
import com.ahmesh.app.presentation.screens.biometricstats.ChartMetricMode
import com.ahmesh.app.presentation.screens.pairing.PairingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricStatsViewModelTest {

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
    fun aggregatesSuccessAndFailureRatesAndMethodBreakdownsFromRoomLogs() = runTest {
        val repo = FakeAuditRepository(
            initialLogs = listOf(
                BiometricAuditRecord(
                    id = 1L,
                    outcome = BiometricAuditOutcome.SUCCEEDED,
                    isSuccess = true,
                    authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK,
                    attemptedAt = 1000L
                ),
                BiometricAuditRecord(
                    id = 2L,
                    outcome = BiometricAuditOutcome.SUCCEEDED,
                    isSuccess = true,
                    authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK,
                    attemptedAt = 2000L
                ),
                BiometricAuditRecord(
                    id = 3L,
                    outcome = BiometricAuditOutcome.SUCCEEDED,
                    isSuccess = true,
                    authMethod = PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH,
                    attemptedAt = 3000L
                ),
                BiometricAuditRecord(
                    id = 4L,
                    outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
                    isSuccess = false,
                    authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK,
                    attemptedAt = 4000L
                ),
                BiometricAuditRecord(
                    id = 5L,
                    outcome = BiometricAuditOutcome.CANCELED,
                    isSuccess = false,
                    authMethod = PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY,
                    attemptedAt = 5000L
                )
            )
        )

        val viewModel = BiometricStatsViewModel(repo)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.totalAttempts)
        assertEquals(3, state.successCount)
        assertEquals(2, state.totalFailureCount)
        assertEquals(1, state.failedAttemptCount)
        assertEquals(1, state.canceledCount)
        assertEquals(0, state.errorCount)
        assertEquals(60, state.successRatePercent)
        assertEquals(40, state.failureRatePercent)
        assertEquals(ChartMetricMode.RATE_PERCENT, state.metricMode)

        assertEquals(3, state.methodBreakdowns.size)
        val qrBreakdown = state.methodBreakdowns.first {
            it.authMethod == PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK
        }
        assertEquals(3, qrBreakdown.totalAttempts)
        assertEquals(2, qrBreakdown.successCount)
        assertEquals(1, qrBreakdown.failureCount)
        assertEquals(67, qrBreakdown.successRatePercent)
        assertEquals(33, qrBreakdown.failureRatePercent)

        viewModel.setMetricMode(ChartMetricMode.ATTEMPT_COUNT)
        advanceUntilIdle()
        assertEquals(ChartMetricMode.ATTEMPT_COUNT, viewModel.uiState.value.metricMode)

        viewModel.clearAuditLogs()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.totalAttempts)
        assertEquals(0, viewModel.uiState.value.successRatePercent)
        assertEquals(0, viewModel.uiState.value.failureRatePercent)
        assertTrue(viewModel.uiState.value.methodBreakdowns.isEmpty())

        collectJob.cancel()
    }

    private class FakeAuditRepository(
        initialLogs: List<BiometricAuditRecord> = emptyList()
    ) : SecurityAuditRepository {
        private val logs = MutableStateFlow(initialLogs)

        override fun observePairingBiometricAuditLogs(): Flow<List<BiometricAuditRecord>> =
            logs.asStateFlow()

        override fun observeAllBiometricAuditLogs(): Flow<List<BiometricAuditRecord>> =
            logs.asStateFlow()

        override suspend fun getAllPairingBiometricAuditLogs(): List<BiometricAuditRecord> =
            logs.value

        override suspend fun recordPairingBiometricAttempt(record: BiometricAuditRecord) {
            logs.value = listOf(record) + logs.value
        }

        override suspend fun clearPairingBiometricAuditLogs() {
            logs.value = emptyList()
        }
    }
}
