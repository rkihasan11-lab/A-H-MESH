package com.ahmesh.app.presentation.auth

import com.ahmesh.app.domain.repository.SecurityPreferencesRepository
import com.ahmesh.app.security.BiometricAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockSessionCoordinator @Inject constructor(
    private val securityRepository: SecurityPreferencesRepository,
    private val biometricAuthManager: BiometricAuthManager
) {
    private val _isLocked = MutableStateFlow(shouldRequireAuthentication())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    @Volatile
    private var isSystemAuthPromptActive: Boolean = false

    fun setSystemAuthPromptActive(active: Boolean) {
        isSystemAuthPromptActive = active
    }

    fun onAppEnteredBackground() {
        if (isSystemAuthPromptActive) return
        val settings = securityRepository.settings.value
        if (settings.lockOnBackground && shouldRequireAuthentication()) {
            _isLocked.value = true
        }
    }

    fun onAppReturnedToForeground() {
        if (!shouldRequireAuthentication()) {
            _isLocked.value = false
        }
    }

    fun unlock() {
        _isLocked.value = false
    }

    fun lockNow() {
        if (shouldRequireAuthentication()) {
            _isLocked.value = true
        }
    }

    private fun shouldRequireAuthentication(): Boolean {
        val settings = securityRepository.settings.value
        if (!settings.isAppLockEnabled) return false
        return settings.hasConfiguredPin || biometricAuthManager.isAuthenticationAvailable()
    }
}
