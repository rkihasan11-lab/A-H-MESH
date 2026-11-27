package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.SecuritySettings
import kotlinx.coroutines.flow.StateFlow

interface SecurityPreferencesRepository {
    val settings: StateFlow<SecuritySettings>
    suspend fun configurePin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
    suspend fun clearPinAndLock()
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setDeviceCredentialFallbackEnabled(enabled: Boolean)
    suspend fun setLockOnBackground(enabled: Boolean)
}
