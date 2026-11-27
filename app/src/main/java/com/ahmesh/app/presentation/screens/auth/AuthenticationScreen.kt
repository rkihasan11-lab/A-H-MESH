package com.ahmesh.app.presentation.screens.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.SecuritySettings
import com.ahmesh.app.presentation.auth.AuthLockScreen
import com.ahmesh.app.presentation.auth.AuthPromptMode
import com.ahmesh.app.presentation.auth.AuthUiState
import com.ahmesh.app.presentation.auth.AuthViewModel
import com.ahmesh.app.security.BiometricAuthManager

@Composable
fun AuthenticationScreen(
    biometricAuthManager: BiometricAuthManager,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val promptRequest by authViewModel.promptRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()

    LaunchedEffect(authState.isLocked) {
        if (!authState.isLocked) {
            authViewModel.cancelActiveAuthPrompt()
            onAuthenticated()
        } else {
            authViewModel.refreshCapabilitiesAndMaybePrompt()
        }
    }

    LaunchedEffect(promptRequest, activity) {
        val request = promptRequest ?: return@LaunchedEffect
        val hostActivity = activity ?: return@LaunchedEffect
        authViewModel.consumePromptRequest()
        when (request.mode) {
            AuthPromptMode.BIOMETRIC -> {
                biometricAuthManager.authenticate(
                    activity = hostActivity,
                    allowDeviceCredentialFallback = authState.settings.isDeviceCredentialFallbackEnabled,
                    useSystemDeviceCredentialInPrompt = !authState.settings.hasConfiguredPin,
                    onResult = authViewModel::onBiometricPromptResult
                )
            }
            AuthPromptMode.DEVICE_CREDENTIAL -> {
                biometricAuthManager.authenticateWithDeviceCredential(
                    activity = hostActivity,
                    onResult = authViewModel::onBiometricPromptResult
                )
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                biometricAuthManager.cancelAuthentication()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("authentication_screen")
    ) {
        AuthLockScreen(
            uiState = authState,
            onDigitClick = authViewModel::onPinDigit,
            onBackspaceClick = authViewModel::onPinBackspace,
            onUseBiometricsClick = authViewModel::requestBiometricPrompt,
            onUseDeviceCredentialClick = authViewModel::requestDeviceCredentialPrompt
        )
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun AuthenticationScreenPreview() {
    AHMeshTheme {
        AuthLockScreen(
            uiState = AuthUiState(
                isLocked = true,
                biometricCapability = BiometricCapability.AVAILABLE,
                canUseDeviceCredential = true,
                settings = SecuritySettings(
                    isAppLockEnabled = true,
                    isBiometricEnabled = true,
                    isDeviceCredentialFallbackEnabled = true,
                    hasConfiguredPin = true
                )
            ),
            onDigitClick = {},
            onBackspaceClick = {},
            onUseBiometricsClick = {},
            onUseDeviceCredentialClick = {}
        )
    }
}
