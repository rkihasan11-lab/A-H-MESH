package com.ahmesh.app.domain.model

data class SecuritySettings(
    val isAppLockEnabled: Boolean = true,
    val isBiometricEnabled: Boolean = true,
    val isDeviceCredentialFallbackEnabled: Boolean = true,
    val lockOnBackground: Boolean = true,
    val hasConfiguredPin: Boolean = false,
    val pinLength: Int = 4
)
