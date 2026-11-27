package com.ahmesh.app.presentation.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.ahmesh.app.security.BiometricAuthManager
import com.ahmesh.app.security.BiometricAuthResult

/**
 * Composable function that triggers and manages biometric authentication using the
 * BiometricPrompt API to secure sensitive operations (e.g. public key cryptographic handshake,
 * sensitive exports, and key rotation).
 */
@Composable
fun BiometricAuthenticationPrompt(
    trigger: Boolean,
    biometricAuthManager: BiometricAuthManager,
    title: String,
    subtitle: String,
    negativeButtonText: String = "Use PIN / Cancel",
    onSuccess: () -> Unit,
    onError: (message: String) -> Unit = {},
    onCanceled: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnCanceled by rememberUpdatedState(onCanceled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val activity = context.findFragmentActivity()

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect

        val hostActivity = activity
        if (hostActivity == null) {
            currentOnError("Host activity not found for biometric authentication")
            currentOnDismiss()
            return@LaunchedEffect
        }

        biometricAuthManager.authenticate(
            activity = hostActivity,
            allowDeviceCredentialFallback = true,
            useSystemDeviceCredentialInPrompt = true,
            title = title,
            subtitle = subtitle,
            negativeButtonText = negativeButtonText
        ) { result ->
            when (result) {
                is BiometricAuthResult.Succeeded -> {
                    currentOnSuccess()
                    currentOnDismiss()
                }
                is BiometricAuthResult.Canceled -> {
                    currentOnCanceled()
                    currentOnDismiss()
                }
                is BiometricAuthResult.FailedAttempt -> {
                    currentOnError("Biometric authentication verification failed")
                }
                is BiometricAuthResult.Error -> {
                    currentOnError(result.message ?: "Biometric authentication error")
                    currentOnDismiss()
                }
            }
        }
    }

    DisposableEffect(trigger) {
        onDispose {
            if (trigger) {
                biometricAuthManager.cancelAuthentication()
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
