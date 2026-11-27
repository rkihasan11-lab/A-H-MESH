package com.ahmesh.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.SecuritySettings
import com.ahmesh.app.presentation.auth.AuthUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    authState: AuthUiState,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEnableAppLock: () -> Unit,
    onDisableAppLockWithoutPin: () -> Unit,
    onSetupPinAndEnable: (String) -> Unit,
    onEnableExistingPin: () -> Unit,
    onVerifyAndChangePin: (currentPin: String, newPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onResetWithNewPin: (String) -> Unit,
    onClearPinAndDisableLock: () -> Unit,
    onVerifyAndDisableLock: (currentPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleDeviceFallback: (Boolean) -> Unit,
    onToggleLockOnBackground: (Boolean) -> Unit,
    onOpenBiometricSetup: () -> Unit,
    onLockNow: () -> Unit,
    onOpenBiometricStats: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("settings_sheet")
    ) {
        SettingsSheetContent(
            authState = authState,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            onDismiss = onDismiss,
            onEnableAppLock = onEnableAppLock,
            onDisableAppLockWithoutPin = onDisableAppLockWithoutPin,
            onSetupPinAndEnable = onSetupPinAndEnable,
            onEnableExistingPin = onEnableExistingPin,
            onVerifyAndChangePin = onVerifyAndChangePin,
            onResetWithNewPin = onResetWithNewPin,
            onClearPinAndDisableLock = onClearPinAndDisableLock,
            onVerifyAndDisableLock = onVerifyAndDisableLock,
            onToggleBiometric = onToggleBiometric,
            onToggleDeviceFallback = onToggleDeviceFallback,
            onToggleLockOnBackground = onToggleLockOnBackground,
            onOpenBiometricSetup = onOpenBiometricSetup,
            onLockNow = onLockNow,
            onOpenBiometricStats = onOpenBiometricStats
        )
    }
}

@Composable
internal fun SettingsSheetContent(
    authState: AuthUiState,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEnableAppLock: () -> Unit,
    onDisableAppLockWithoutPin: () -> Unit,
    onSetupPinAndEnable: (String) -> Unit,
    onEnableExistingPin: () -> Unit,
    onVerifyAndChangePin: (currentPin: String, newPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onResetWithNewPin: (String) -> Unit,
    onClearPinAndDisableLock: () -> Unit,
    onVerifyAndDisableLock: (currentPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleDeviceFallback: (Boolean) -> Unit,
    onToggleLockOnBackground: (Boolean) -> Unit,
    onOpenBiometricSetup: () -> Unit,
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBiometricStats: () -> Unit = {}
) {
    var editorMode by remember { mutableStateOf(PinEditorMode.NONE) }
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var formErrorResId by remember { mutableStateOf<Int?>(null) }

    fun resetEditor() {
        editorMode = PinEditorMode.NONE
        currentPinInput = ""
        newPinInput = ""
        confirmPinInput = ""
        formErrorResId = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.security_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.security_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.testTag("security_posture_badge"),
                shape = RoundedCornerShape(50),
                color = if (authState.settings.isAppLockEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (authState.settings.isAppLockEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (authState.settings.isAppLockEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = stringResource(
                            id = if (authState.settings.isAppLockEnabled) {
                                R.string.security_badge_protected
                            } else {
                                R.string.security_badge_unprotected
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (authState.settings.isAppLockEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        SecurityDashboardSummary(authState = authState)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_appearance_card"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.settings_appearance_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                SecuritySwitchRow(
                    title = stringResource(id = R.string.settings_toggle_dark_mode),
                    subtitle = stringResource(
                        id = if (isDarkMode) {
                            R.string.settings_toggle_dark_mode_on_desc
                        } else {
                            R.string.settings_toggle_dark_mode_off_desc
                        }
                    ),
                    checked = isDarkMode,
                    enabled = true,
                    testTag = "settings_dark_mode_switch",
                    onCheckedChange = onToggleDarkMode
                )
            }
        }

        if (authState.biometricCapability != BiometricCapability.AVAILABLE) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_biometric_enroll_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.biometric_setup_settings_banner_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(id = R.string.biometric_setup_settings_banner_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenBiometricSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("security_biometric_setup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.biometric_setup_open_button))
                    }
                }
            }
        } else {
            FilledTonalButton(
                onClick = onOpenBiometricSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("security_biometric_setup_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.biometric_setup_manage_button))
            }
        }

        OutlinedButton(
            onClick = onOpenBiometricStats,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("settings_biometric_analytics_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.settings_biometric_analytics_cta))
        }

        val hasDeviceAuth = authState.biometricCapability == BiometricCapability.AVAILABLE ||
            authState.canUseDeviceCredential
        val isProtectionActive = authState.settings.isAppLockEnabled &&
            (authState.settings.hasConfiguredPin || hasDeviceAuth)

        SecuritySwitchRow(
            title = stringResource(id = R.string.security_toggle_app_lock),
            subtitle = stringResource(id = R.string.security_toggle_app_lock_desc),
            checked = isProtectionActive,
            enabled = true,
            testTag = "security_app_lock_switch",
            onCheckedChange = { shouldEnable ->
                formErrorResId = null
                if (shouldEnable) {
                    when {
                        authState.settings.hasConfiguredPin -> {
                            onEnableExistingPin()
                            resetEditor()
                        }
                        hasDeviceAuth -> {
                            onEnableAppLock()
                            resetEditor()
                        }
                        else -> {
                            editorMode = PinEditorMode.SETUP_NEW
                        }
                    }
                } else {
                    if (authState.settings.hasConfiguredPin) {
                        editorMode = PinEditorMode.DISABLE_LOCK
                    } else {
                        onDisableAppLockWithoutPin()
                        resetEditor()
                    }
                }
            }
        )

        SecuritySwitchRow(
            title = stringResource(id = R.string.security_toggle_biometric),
            subtitle = stringResource(id = R.string.security_toggle_biometric_desc),
            checked = authState.settings.isBiometricEnabled,
            enabled = authState.settings.isAppLockEnabled &&
                authState.biometricCapability == BiometricCapability.AVAILABLE,
            testTag = "security_biometric_switch",
            onCheckedChange = onToggleBiometric
        )

        SecuritySwitchRow(
            title = stringResource(id = R.string.security_toggle_device_fallback),
            subtitle = stringResource(id = R.string.security_toggle_device_fallback_desc),
            checked = authState.settings.isDeviceCredentialFallbackEnabled,
            enabled = authState.settings.isAppLockEnabled && authState.canUseDeviceCredential,
            testTag = "security_device_fallback_switch",
            onCheckedChange = onToggleDeviceFallback
        )

        SecuritySwitchRow(
            title = stringResource(id = R.string.security_toggle_lock_bg),
            subtitle = stringResource(id = R.string.security_toggle_lock_bg_desc),
            checked = authState.settings.lockOnBackground,
            enabled = authState.settings.isAppLockEnabled,
            testTag = "security_lock_bg_switch",
            onCheckedChange = onToggleLockOnBackground
        )

        AnimatedVisibility(visible = editorMode != PinEditorMode.NONE) {
            SettingsPinEditorCard(
                editorMode = editorMode,
                currentPinInput = currentPinInput,
                newPinInput = newPinInput,
                confirmPinInput = confirmPinInput,
                formErrorResId = formErrorResId,
                onCurrentPinChange = {
                    currentPinInput = it
                    formErrorResId = null
                },
                onNewPinChange = {
                    newPinInput = it
                    formErrorResId = null
                },
                onConfirmPinChange = {
                    confirmPinInput = it
                    formErrorResId = null
                },
                onClearPin = {
                    onClearPinAndDisableLock()
                    resetEditor()
                },
                onCancel = { resetEditor() },
                onSubmit = {
                    when (editorMode) {
                        PinEditorMode.SETUP_NEW -> {
                            when {
                                newPinInput.length !in 4..6 ->
                                    formErrorResId = R.string.security_pin_error_length
                                newPinInput != confirmPinInput ->
                                    formErrorResId = R.string.security_pin_error_mismatch
                                else -> {
                                    onSetupPinAndEnable(newPinInput)
                                    resetEditor()
                                }
                            }
                        }
                        PinEditorMode.RESET_PIN -> {
                            when {
                                newPinInput.length !in 4..6 ->
                                    formErrorResId = R.string.security_pin_error_length
                                newPinInput != confirmPinInput ->
                                    formErrorResId = R.string.security_pin_error_mismatch
                                else -> {
                                    onResetWithNewPin(newPinInput)
                                    resetEditor()
                                }
                            }
                        }
                        PinEditorMode.CHANGE_EXISTING -> {
                            when {
                                newPinInput.length !in 4..6 ->
                                    formErrorResId = R.string.security_pin_error_length
                                newPinInput != confirmPinInput ->
                                    formErrorResId = R.string.security_pin_error_mismatch
                                else -> {
                                    onVerifyAndChangePin(currentPinInput, newPinInput) { ok ->
                                        if (ok) {
                                            resetEditor()
                                        } else {
                                            formErrorResId = R.string.security_pin_error_wrong_current
                                        }
                                    }
                                }
                            }
                        }
                        PinEditorMode.DISABLE_LOCK -> {
                            onVerifyAndDisableLock(currentPinInput) { ok ->
                                if (ok) {
                                    resetEditor()
                                } else {
                                    formErrorResId = R.string.security_pin_error_wrong_current
                                }
                            }
                        }
                        PinEditorMode.NONE -> Unit
                    }
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    formErrorResId = null
                    editorMode = if (authState.settings.hasConfiguredPin) {
                        PinEditorMode.CHANGE_EXISTING
                    } else {
                        PinEditorMode.SETUP_NEW
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("security_change_pin_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        id = if (authState.settings.hasConfiguredPin) {
                            R.string.security_btn_change_pin
                        } else {
                            R.string.security_btn_set_pin
                        }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (authState.settings.hasConfiguredPin) {
                OutlinedButton(
                    onClick = {
                        formErrorResId = null
                        editorMode = PinEditorMode.RESET_PIN
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("security_reset_pin_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockReset,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.security_btn_reset_pin),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isProtectionActive) {
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onLockNow()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("security_lock_now_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.security_btn_lock_now))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsSheetPreview() {
    AHMeshTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsSheetContent(
                authState = AuthUiState(
                    isLocked = false,
                    biometricCapability = BiometricCapability.AVAILABLE,
                    canUseDeviceCredential = true,
                    settings = SecuritySettings(
                        isAppLockEnabled = true,
                        isBiometricEnabled = true,
                        isDeviceCredentialFallbackEnabled = true,
                        lockOnBackground = true,
                        hasConfiguredPin = true,
                        pinLength = 4
                    )
                ),
                isDarkMode = true,
                onToggleDarkMode = {},
                onDismiss = {},
                onEnableAppLock = {},
                onDisableAppLockWithoutPin = {},
                onSetupPinAndEnable = {},
                onEnableExistingPin = {},
                onVerifyAndChangePin = { _, _, _ -> },
                onResetWithNewPin = {},
                onClearPinAndDisableLock = {},
                onVerifyAndDisableLock = { _, _ -> },
                onToggleBiometric = {},
                onToggleDeviceFallback = {},
                onToggleLockOnBackground = {},
                onOpenBiometricSetup = {},
                onLockNow = {}
            )
        }
    }
}
