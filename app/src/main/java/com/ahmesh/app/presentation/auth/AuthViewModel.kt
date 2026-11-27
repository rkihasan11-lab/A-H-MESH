package com.ahmesh.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.security.BiometricAuthManager
import com.ahmesh.app.security.BiometricAuthResult
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.SecuritySettings
import com.ahmesh.app.domain.repository.SecurityPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthPromptMode {
    BIOMETRIC,
    DEVICE_CREDENTIAL
}

data class AuthPromptRequest(
    val requestId: Long,
    val mode: AuthPromptMode
)

enum class AuthStatusNotice {
    NONE,
    BIOMETRIC_UNAVAILABLE,
    BIOMETRIC_FAILED,
    BIOMETRIC_CANCELED,
    WRONG_PIN,
    COOLDOWN
}

data class AuthUiState(
    val isLocked: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.UNAVAILABLE,
    val canUseDeviceCredential: Boolean = false,
    val settings: SecuritySettings = SecuritySettings(),
    val pinInput: String = "",
    val statusNotice: AuthStatusNotice = AuthStatusNotice.NONE,
    val failedAttempts: Int = 0,
    val lockoutRemainingSeconds: Int = 0,
    val pinShakeTrigger: Int = 0
)

private data class TransientAuthState(
    val biometricCapability: BiometricCapability = BiometricCapability.UNAVAILABLE,
    val canUseDeviceCredential: Boolean = false,
    val pinInput: String = "",
    val statusNotice: AuthStatusNotice = AuthStatusNotice.NONE,
    val failedAttempts: Int = 0,
    val lockoutRemainingSeconds: Int = 0,
    val pinShakeTrigger: Int = 0
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val securityRepository: SecurityPreferencesRepository,
    private val biometricAuthManager: BiometricAuthManager,
    private val appLockCoordinator: AppLockSessionCoordinator
) : ViewModel() {

    private val transientState = MutableStateFlow(
        TransientAuthState(
            biometricCapability = biometricAuthManager.checkBiometricCapability(),
            canUseDeviceCredential = biometricAuthManager.canAuthenticateWithDeviceCredential()
        )
    )

    private val _promptRequest = MutableStateFlow<AuthPromptRequest?>(null)
    val promptRequest: StateFlow<AuthPromptRequest?> = _promptRequest.asStateFlow()

    private var promptCounter = 0L
    private var autoPromptedForCurrentLock = false
    private var cooldownJob: Job? = null

    val uiState: StateFlow<AuthUiState> = combine(
        appLockCoordinator.isLocked,
        securityRepository.settings,
        transientState
    ) { isLocked, settings, transient ->
        if (!isLocked) {
            autoPromptedForCurrentLock = false
        }
        val canAuth = settings.hasConfiguredPin ||
            transient.biometricCapability == BiometricCapability.AVAILABLE ||
            transient.canUseDeviceCredential
        AuthUiState(
            isLocked = isLocked && settings.isAppLockEnabled && canAuth,
            biometricCapability = transient.biometricCapability,
            canUseDeviceCredential = transient.canUseDeviceCredential,
            settings = settings,
            pinInput = transient.pinInput,
            statusNotice = transient.statusNotice,
            failedAttempts = transient.failedAttempts,
            lockoutRemainingSeconds = transient.lockoutRemainingSeconds,
            pinShakeTrigger = transient.pinShakeTrigger
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthUiState(
            isLocked = appLockCoordinator.isLocked.value &&
                securityRepository.settings.value.isAppLockEnabled &&
                (securityRepository.settings.value.hasConfiguredPin ||
                    transientState.value.biometricCapability == BiometricCapability.AVAILABLE ||
                    transientState.value.canUseDeviceCredential),
            biometricCapability = transientState.value.biometricCapability,
            canUseDeviceCredential = transientState.value.canUseDeviceCredential,
            settings = securityRepository.settings.value
        )
    )

    fun onAppStartup() {
        refreshCapabilitiesAndMaybePrompt()
    }

    fun refreshCapabilitiesAndMaybePrompt() {
        val capability = biometricAuthManager.checkBiometricCapability()
        val deviceCred = biometricAuthManager.canAuthenticateWithDeviceCredential()
        transientState.value = transientState.value.copy(
            biometricCapability = capability,
            canUseDeviceCredential = deviceCred
        )

        val settings = securityRepository.settings.value
        val hasAnyAuthMethod = settings.hasConfiguredPin ||
            capability == BiometricCapability.AVAILABLE ||
            deviceCred
        if (appLockCoordinator.isLocked.value && !hasAnyAuthMethod) {
            appLockCoordinator.unlock()
            return
        }

        if (appLockCoordinator.isLocked.value && settings.isAppLockEnabled && !autoPromptedForCurrentLock) {
            autoPromptedForCurrentLock = true
            if (settings.isBiometricEnabled) {
                when {
                    capability == BiometricCapability.AVAILABLE -> requestBiometricPrompt()
                    settings.isDeviceCredentialFallbackEnabled && deviceCred -> {
                        requestDeviceCredentialPrompt()
                    }
                    else -> {
                        transientState.value = transientState.value.copy(
                            statusNotice = AuthStatusNotice.BIOMETRIC_UNAVAILABLE
                        )
                    }
                }
            } else if (settings.isDeviceCredentialFallbackEnabled && deviceCred && !settings.hasConfiguredPin) {
                requestDeviceCredentialPrompt()
            }
        }
    }

    fun requestBiometricPrompt() {
        val capability = biometricAuthManager.checkBiometricCapability()
        val deviceCred = biometricAuthManager.canAuthenticateWithDeviceCredential()
        val settings = securityRepository.settings.value
        transientState.value = transientState.value.copy(
            biometricCapability = capability,
            canUseDeviceCredential = deviceCred
        )
        if (capability != BiometricCapability.AVAILABLE) {
            if (settings.isDeviceCredentialFallbackEnabled && deviceCred) {
                requestDeviceCredentialPrompt()
            } else {
                transientState.value = transientState.value.copy(
                    statusNotice = AuthStatusNotice.BIOMETRIC_UNAVAILABLE
                )
            }
            return
        }
        appLockCoordinator.setSystemAuthPromptActive(true)
        promptCounter++
        _promptRequest.value = AuthPromptRequest(promptCounter, AuthPromptMode.BIOMETRIC)
    }

    fun requestDeviceCredentialPrompt() {
        if (!biometricAuthManager.canAuthenticateWithDeviceCredential()) {
            transientState.value = transientState.value.copy(
                canUseDeviceCredential = false,
                statusNotice = AuthStatusNotice.BIOMETRIC_UNAVAILABLE
            )
            return
        }
        appLockCoordinator.setSystemAuthPromptActive(true)
        promptCounter++
        _promptRequest.value = AuthPromptRequest(promptCounter, AuthPromptMode.DEVICE_CREDENTIAL)
    }

    fun cancelActiveAuthPrompt() {
        biometricAuthManager.cancelAuthentication()
        appLockCoordinator.setSystemAuthPromptActive(false)
        _promptRequest.value = null
    }

    fun consumePromptRequest() {
        _promptRequest.value = null
    }

    fun onBiometricPromptResult(result: BiometricAuthResult) {
        when (result) {
            BiometricAuthResult.Succeeded -> {
                appLockCoordinator.setSystemAuthPromptActive(false)
                unlockSuccessfully()
            }
            BiometricAuthResult.FailedAttempt -> {
                transientState.value = transientState.value.copy(
                    statusNotice = AuthStatusNotice.BIOMETRIC_FAILED
                )
            }
            is BiometricAuthResult.Canceled -> {
                appLockCoordinator.setSystemAuthPromptActive(false)
                transientState.value = transientState.value.copy(
                    statusNotice = AuthStatusNotice.BIOMETRIC_CANCELED
                )
            }
            is BiometricAuthResult.Error -> {
                appLockCoordinator.setSystemAuthPromptActive(false)
                transientState.value = transientState.value.copy(
                    statusNotice = AuthStatusNotice.BIOMETRIC_FAILED
                )
            }
        }
    }

    fun onPinDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = transientState.value
        if (current.lockoutRemainingSeconds > 0) return

        val targetLength = securityRepository.settings.value.pinLength.coerceIn(4, 6)
        if (current.pinInput.length >= targetLength) return

        val updatedPin = current.pinInput + digit
        transientState.value = current.copy(
            pinInput = updatedPin,
            statusNotice = if (current.statusNotice == AuthStatusNotice.WRONG_PIN) {
                AuthStatusNotice.NONE
            } else {
                current.statusNotice
            }
        )

        if (updatedPin.length == targetLength) {
            verifySubmittedPin(updatedPin)
        }
    }

    fun onPinBackspace() {
        val current = transientState.value
        if (current.pinInput.isNotEmpty() && current.lockoutRemainingSeconds == 0) {
            transientState.value = current.copy(pinInput = current.pinInput.dropLast(1))
        }
    }

    private fun verifySubmittedPin(candidate: String) {
        viewModelScope.launch {
            val matched = securityRepository.verifyPin(candidate)
            if (matched) {
                unlockSuccessfully()
            } else {
                val nextAttempts = transientState.value.failedAttempts + 1
                val cooldownSec = when {
                    nextAttempts >= 8 -> 30
                    nextAttempts >= 5 -> 15
                    else -> 0
                }
                transientState.value = transientState.value.copy(
                    pinInput = "",
                    failedAttempts = nextAttempts,
                    statusNotice = if (cooldownSec > 0) AuthStatusNotice.COOLDOWN else AuthStatusNotice.WRONG_PIN,
                    lockoutRemainingSeconds = cooldownSec,
                    pinShakeTrigger = transientState.value.pinShakeTrigger + 1
                )
                if (cooldownSec > 0) {
                    startCooldownTicker(cooldownSec)
                }
            }
        }
    }

    private fun startCooldownTicker(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                transientState.value = transientState.value.copy(
                    lockoutRemainingSeconds = remaining,
                    statusNotice = if (remaining > 0) AuthStatusNotice.COOLDOWN else AuthStatusNotice.NONE
                )
            }
        }
    }

    private fun unlockSuccessfully() {
        cooldownJob?.cancel()
        biometricAuthManager.cancelAuthentication()
        appLockCoordinator.setSystemAuthPromptActive(false)
        transientState.value = transientState.value.copy(
            pinInput = "",
            statusNotice = AuthStatusNotice.NONE,
            failedAttempts = 0,
            lockoutRemainingSeconds = 0
        )
        appLockCoordinator.unlock()
    }

    fun enableAppLockWithNewPin(pin: String) {
        viewModelScope.launch {
            securityRepository.configurePin(pin)
            securityRepository.setAppLockEnabled(true)
            val biometricReady =
                biometricAuthManager.checkBiometricCapability() == BiometricCapability.AVAILABLE
            securityRepository.setBiometricEnabled(biometricReady)
            autoPromptedForCurrentLock = true
            appLockCoordinator.unlock()
        }
    }

    fun enableAppLock() {
        viewModelScope.launch {
            securityRepository.setAppLockEnabled(true)
            autoPromptedForCurrentLock = true
            appLockCoordinator.unlock()
        }
    }

    fun disableAppLockWithoutPin() {
        viewModelScope.launch {
            securityRepository.setAppLockEnabled(false)
            appLockCoordinator.unlock()
        }
    }

    fun enableExistingPinAppLock() {
        viewModelScope.launch {
            if (securityRepository.settings.value.hasConfiguredPin) {
                securityRepository.setAppLockEnabled(true)
                autoPromptedForCurrentLock = true
                appLockCoordinator.unlock()
            }
        }
    }

    fun verifyAndUpdatePin(currentPin: String, newPin: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = securityRepository.verifyPin(currentPin)
            if (valid) {
                securityRepository.configurePin(newPin)
            }
            onComplete(valid)
        }
    }

    fun verifyAndDisableAppLock(currentPin: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = securityRepository.verifyPin(currentPin)
            if (valid) {
                securityRepository.setAppLockEnabled(false)
                appLockCoordinator.unlock()
            }
            onComplete(valid)
        }
    }

    fun resetWithNewPin(newPin: String) {
        viewModelScope.launch {
            securityRepository.configurePin(newPin)
            securityRepository.setAppLockEnabled(true)
            autoPromptedForCurrentLock = true
            appLockCoordinator.unlock()
        }
    }

    fun clearPinAndDisableLock() {
        viewModelScope.launch {
            securityRepository.clearPinAndLock()
            appLockCoordinator.unlock()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setBiometricEnabled(enabled)
        }
    }

    fun setDeviceCredentialFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setDeviceCredentialFallbackEnabled(enabled)
        }
    }

    fun setLockOnBackground(enabled: Boolean) {
        viewModelScope.launch {
            securityRepository.setLockOnBackground(enabled)
        }
    }

    fun lockAppNow() {
        autoPromptedForCurrentLock = false
        transientState.value = transientState.value.copy(
            pinInput = "",
            statusNotice = AuthStatusNotice.NONE
        )
        appLockCoordinator.lockNow()
        refreshCapabilitiesAndMaybePrompt()
    }

    fun refreshCapabilities() {
        val capability = biometricAuthManager.checkBiometricCapability()
        val deviceCred = biometricAuthManager.canAuthenticateWithDeviceCredential()
        transientState.value = transientState.value.copy(
            biometricCapability = capability,
            canUseDeviceCredential = deviceCred
        )
        val settings = securityRepository.settings.value
        val hasAnyAuthMethod = settings.hasConfiguredPin ||
            capability == BiometricCapability.AVAILABLE ||
            deviceCred
        if (appLockCoordinator.isLocked.value && !hasAnyAuthMethod) {
            appLockCoordinator.unlock()
        }
    }

    fun onSystemSecuritySettingsOpened() {
        appLockCoordinator.setSystemAuthPromptActive(true)
    }

    fun onSystemSecuritySettingsReturned() {
        appLockCoordinator.setSystemAuthPromptActive(false)
        refreshCapabilities()
    }
}
