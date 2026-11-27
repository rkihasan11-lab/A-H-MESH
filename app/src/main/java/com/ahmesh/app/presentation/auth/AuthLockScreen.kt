package com.ahmesh.app.presentation.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.SecuritySettings
import kotlin.math.roundToInt

@Composable
fun AuthLockScreen(
    uiState: AuthUiState,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onUseBiometricsClick: () -> Unit,
    onUseDeviceCredentialClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(uiState.pinShakeTrigger) {
        if (uiState.pinShakeTrigger > 0) {
            val offsets = listOf(-18f, 18f, -12f, 12f, -6f, 6f, 0f)
            offsets.forEach { target ->
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 45))
            }
        }
    }

    val canUseBiometrics = uiState.settings.isBiometricEnabled &&
        uiState.biometricCapability == BiometricCapability.AVAILABLE
    val canUseDeviceLock = uiState.settings.isDeviceCredentialFallbackEnabled &&
        uiState.canUseDeviceCredential

    val statusText = when (uiState.statusNotice) {
        AuthStatusNotice.BIOMETRIC_UNAVAILABLE -> stringResource(id = R.string.auth_error_biometric_unavailable)
        AuthStatusNotice.BIOMETRIC_FAILED -> stringResource(id = R.string.auth_error_biometric_failed)
        AuthStatusNotice.BIOMETRIC_CANCELED -> stringResource(id = R.string.auth_error_biometric_canceled)
        AuthStatusNotice.WRONG_PIN -> stringResource(id = R.string.auth_error_wrong_pin)
        AuthStatusNotice.COOLDOWN -> stringResource(
            id = R.string.auth_error_cooldown,
            uiState.lockoutRemainingSeconds
        )
        AuthStatusNotice.NONE -> if (canUseBiometrics) {
            stringResource(id = R.string.auth_lock_subtitle_biometric)
        } else {
            stringResource(id = R.string.auth_lock_subtitle_pin_only)
        }
    }

    val isErrorBanner = uiState.statusNotice == AuthStatusNotice.WRONG_PIN ||
        uiState.statusNotice == AuthStatusNotice.COOLDOWN ||
        uiState.statusNotice == AuthStatusNotice.BIOMETRIC_FAILED

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_lock_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                        contentDescription = stringResource(id = R.string.cd_ah_mesh_logo),
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.auth_lock_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.brand_tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_status_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isErrorBanner) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isErrorBanner) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isErrorBanner) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                if (uiState.settings.hasConfiguredPin) {
                    PinDotRow(
                        pinLength = uiState.settings.pinLength.coerceIn(4, 6),
                        filledCount = uiState.pinInput.length,
                        isError = uiState.statusNotice == AuthStatusNotice.WRONG_PIN,
                        modifier = Modifier
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                            .testTag("auth_pin_dots")
                    )

                    PinKeypad(
                        enabled = uiState.lockoutRemainingSeconds == 0,
                        showBiometricKey = canUseBiometrics,
                        onDigitClick = onDigitClick,
                        onBackspaceClick = onBackspaceClick,
                        onBiometricKeyClick = onUseBiometricsClick
                    )
                }

                if (canUseBiometrics || canUseDeviceLock) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (canUseBiometrics) {
                            FilledTonalButton(
                                onClick = onUseBiometricsClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("auth_biometric_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.auth_action_try_biometrics),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (canUseDeviceLock) {
                            OutlinedButton(
                                onClick = onUseDeviceCredentialClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("auth_device_credential_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhonelinkLock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.auth_action_device_lock),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDotRow(
    pinLength: Int,
    filledCount: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pinLength) { index ->
            val filled = index < filledCount
            val dotColor by animateColorAsState(
                targetValue = when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "pin_dot_color"
            )
            val dotScale by animateFloatAsState(
                targetValue = if (filled) 1.18f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "pin_dot_scale"
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(
                        width = 1.5.dp,
                        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    showBiometricKey: Boolean,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricKeyClick: () -> Unit
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit ->
                    KeypadDigitButton(
                        digit = digit,
                        enabled = enabled,
                        onClick = { onDigitClick(digit) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBiometricKey) {
                Surface(
                    onClick = onBiometricKeyClick,
                    enabled = enabled,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("auth_keypad_biometric"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = stringResource(id = R.string.auth_cd_biometric_key),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }

            KeypadDigitButton(
                digit = '0',
                enabled = enabled,
                onClick = { onDigitClick('0') }
            )

            Surface(
                onClick = onBackspaceClick,
                enabled = enabled,
                modifier = Modifier
                    .size(72.dp)
                    .testTag("auth_pin_backspace"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = stringResource(id = R.string.auth_cd_backspace),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .testTag("auth_pin_key_$digit"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthLockScreenPreview() {
    AHMeshTheme {
        AuthLockScreen(
            uiState = AuthUiState(
                isLocked = true,
                biometricCapability = BiometricCapability.AVAILABLE,
                canUseDeviceCredential = true,
                settings = SecuritySettings(isAppLockEnabled = true, hasConfiguredPin = true),
                pinInput = "12"
            ),
            onDigitClick = {},
            onBackspaceClick = {},
            onUseBiometricsClick = {},
            onUseDeviceCredentialClick = {}
        )
    }
}
