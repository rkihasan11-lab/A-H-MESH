package com.ahmesh.app

import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricPrompt
import com.ahmesh.app.security.BiometricAuthManager
import com.ahmesh.app.security.BiometricAuthResult
import com.ahmesh.app.security.PromptErrorOutcome
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.SecuritySettings
import com.ahmesh.app.domain.repository.SecurityPreferencesRepository
import com.ahmesh.app.presentation.auth.AppLockSessionCoordinator
import com.ahmesh.app.presentation.auth.AuthPromptMode
import com.ahmesh.app.presentation.auth.AuthStatusNotice
import com.ahmesh.app.presentation.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricAuthManagerTest {

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
    fun classifyErrorOutcomeHandlesCancellation() {
        val codes = listOf(
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON
        )
        codes.forEach { code ->
            assertEquals(
                PromptErrorOutcome.CANCELED,
                BiometricAuthManager.classifyErrorOutcome(
                    errorCode = code,
                    allowDeviceCredentialFallback = true,
                    isDeviceCredentialFlow = false,
                    canUseDeviceCredential = true
                )
            )
        }
    }

    @Test
    fun classifyErrorOutcomeFallsBackToDeviceCredentialOnLockoutOrUnavailable() {
        val fallbackCodes = listOf(
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_UNAVAILABLE
        )
        fallbackCodes.forEach { code ->
            assertEquals(
                PromptErrorOutcome.FALLBACK_TO_DEVICE_CREDENTIAL,
                BiometricAuthManager.classifyErrorOutcome(
                    errorCode = code,
                    allowDeviceCredentialFallback = true,
                    isDeviceCredentialFlow = false,
                    canUseDeviceCredential = true
                )
            )
            assertEquals(
                PromptErrorOutcome.ERROR,
                BiometricAuthManager.classifyErrorOutcome(
                    errorCode = code,
                    allowDeviceCredentialFallback = false,
                    isDeviceCredentialFlow = false,
                    canUseDeviceCredential = true
                )
            )
            assertEquals(
                PromptErrorOutcome.ERROR,
                BiometricAuthManager.classifyErrorOutcome(
                    errorCode = code,
                    allowDeviceCredentialFallback = true,
                    isDeviceCredentialFlow = true,
                    canUseDeviceCredential = true
                )
            )
        }
    }

    @Test
    fun startupSequenceRequestsBiometricsAndHandlesCancellationAndSuccess() = runTest {
        val repo = FakeSecurityPreferencesRepository(
            SecuritySettings(
                isAppLockEnabled = true,
                isBiometricEnabled = true,
                isDeviceCredentialFallbackEnabled = true,
                hasConfiguredPin = true
            )
        )
        val manager = FakeBiometricAuthManager(
            capability = BiometricCapability.AVAILABLE,
            deviceCredentialAvailable = true
        )
        val coordinator = AppLockSessionCoordinator(repo, manager)
        val viewModel = AuthViewModel(repo, manager, coordinator)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAppStartup()

        assertEquals(AuthPromptMode.BIOMETRIC, viewModel.promptRequest.value?.mode)
        viewModel.consumePromptRequest()

        viewModel.onBiometricPromptResult(BiometricAuthResult.Canceled("Canceled", isNegativeButton = false))
        advanceUntilIdle()
        assertTrue(coordinator.isLocked.value)
        assertEquals(AuthStatusNotice.BIOMETRIC_CANCELED, viewModel.uiState.value.statusNotice)

        viewModel.onBiometricPromptResult(BiometricAuthResult.Succeeded)
        advanceUntilIdle()
        assertFalse(coordinator.isLocked.value)
        assertTrue(manager.cancelCalls >= 1)
        collectJob.cancel()
    }

    @Test
    fun startupSequenceFallsBackToDeviceCredentialWhenBiometricsUnavailable() = runTest {
        val repo = FakeSecurityPreferencesRepository(
            SecuritySettings(
                isAppLockEnabled = true,
                isBiometricEnabled = true,
                isDeviceCredentialFallbackEnabled = true,
                hasConfiguredPin = true
            )
        )
        val manager = FakeBiometricAuthManager(
            capability = BiometricCapability.UNAVAILABLE,
            deviceCredentialAvailable = true
        )
        val coordinator = AppLockSessionCoordinator(repo, manager)
        val viewModel = AuthViewModel(repo, manager, coordinator)

        viewModel.onAppStartup()

        assertEquals(AuthPromptMode.DEVICE_CREDENTIAL, viewModel.promptRequest.value?.mode)

        viewModel.cancelActiveAuthPrompt()
        assertNull(viewModel.promptRequest.value)
        assertEquals(1, manager.cancelCalls)
    }

    @Test
    fun startupLocksWhenBiometricsAvailableWithoutLocalPin() = runTest {
        val repo = FakeSecurityPreferencesRepository(
            SecuritySettings(
                isAppLockEnabled = true,
                isBiometricEnabled = true,
                isDeviceCredentialFallbackEnabled = true,
                hasConfiguredPin = false
            )
        )
        val manager = FakeBiometricAuthManager(
            capability = BiometricCapability.AVAILABLE,
            deviceCredentialAvailable = false
        )
        val coordinator = AppLockSessionCoordinator(repo, manager)
        val viewModel = AuthViewModel(repo, manager, coordinator)

        assertTrue(coordinator.isLocked.value)
        viewModel.onAppStartup()
        assertEquals(AuthPromptMode.BIOMETRIC, viewModel.promptRequest.value?.mode)
    }

    @Test
    fun startupDoesNotDeadlockWhenNoAuthMethodConfigured() = runTest {
        val repo = FakeSecurityPreferencesRepository(
            SecuritySettings(
                isAppLockEnabled = true,
                isBiometricEnabled = true,
                isDeviceCredentialFallbackEnabled = true,
                hasConfiguredPin = false
            )
        )
        val manager = FakeBiometricAuthManager(
            capability = BiometricCapability.UNAVAILABLE,
            deviceCredentialAvailable = false
        )
        val coordinator = AppLockSessionCoordinator(repo, manager)
        val viewModel = AuthViewModel(repo, manager, coordinator)

        assertFalse(coordinator.isLocked.value)
        viewModel.onAppStartup()
        assertNull(viewModel.promptRequest.value)
    }

    @Test
    fun buildEnrollmentIntentActionsPrioritizesBiometricEnrollOnApi30Plus() {
        assertEquals(
            listOf(
                Settings.ACTION_BIOMETRIC_ENROLL,
                Settings.ACTION_SECURITY_SETTINGS,
                Settings.ACTION_SETTINGS
            ),
            BiometricAuthManager.buildEnrollmentIntentActions(Build.VERSION_CODES.R)
        )
        assertEquals(
            listOf(
                Settings.ACTION_SECURITY_SETTINGS,
                Settings.ACTION_SETTINGS
            ),
            BiometricAuthManager.buildEnrollmentIntentActions(Build.VERSION_CODES.Q)
        )
    }

    @Test
    fun returningFromSystemSecuritySettingsRefreshesCapabilitiesWithoutLocking() = runTest {
        val repo = FakeSecurityPreferencesRepository(
            SecuritySettings(
                isAppLockEnabled = true,
                isBiometricEnabled = true,
                isDeviceCredentialFallbackEnabled = true,
                lockOnBackground = true,
                hasConfiguredPin = true
            )
        )
        val manager = FakeBiometricAuthManager(
            capability = BiometricCapability.NOT_ENROLLED,
            deviceCredentialAvailable = true
        )
        val coordinator = AppLockSessionCoordinator(repo, manager)
        val viewModel = AuthViewModel(repo, manager, coordinator)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Simulate user already unlocked and entering Security Settings to enroll biometrics
        viewModel.onBiometricPromptResult(BiometricAuthResult.Succeeded)
        advanceUntilIdle()
        assertFalse(coordinator.isLocked.value)

        viewModel.onSystemSecuritySettingsOpened()
        coordinator.onAppEnteredBackground()
        assertFalse(coordinator.isLocked.value)

        manager.capability = BiometricCapability.AVAILABLE
        viewModel.onSystemSecuritySettingsReturned()
        advanceUntilIdle()

        assertEquals(BiometricCapability.AVAILABLE, viewModel.uiState.value.biometricCapability)
        assertFalse(coordinator.isLocked.value)
        collectJob.cancel()
    }

    private class FakeBiometricAuthManager(
        var capability: BiometricCapability,
        private val deviceCredentialAvailable: Boolean
    ) : BiometricAuthManager() {
        var cancelCalls = 0

        override fun checkBiometricCapability(): BiometricCapability = capability

        override fun canAuthenticateWithDeviceCredential(): Boolean = deviceCredentialAvailable

        override fun cancelAuthentication() {
            cancelCalls++
        }
    }

    private class FakeSecurityPreferencesRepository(
        initial: SecuritySettings
    ) : SecurityPreferencesRepository {
        private val _settings = MutableStateFlow(initial)
        override val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

        override suspend fun configurePin(pin: String) = Unit
        override suspend fun verifyPin(pin: String): Boolean = false
        override suspend fun clearPinAndLock() = Unit
        override suspend fun setAppLockEnabled(enabled: Boolean) {
            _settings.value = _settings.value.copy(isAppLockEnabled = enabled)
        }
        override suspend fun setBiometricEnabled(enabled: Boolean) {
            _settings.value = _settings.value.copy(isBiometricEnabled = enabled)
        }
        override suspend fun setDeviceCredentialFallbackEnabled(enabled: Boolean) {
            _settings.value = _settings.value.copy(isDeviceCredentialFallbackEnabled = enabled)
        }
        override suspend fun setLockOnBackground(enabled: Boolean) {
            _settings.value = _settings.value.copy(lockOnBackground = enabled)
        }
    }
}
