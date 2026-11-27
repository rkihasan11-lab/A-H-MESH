package com.ahmesh.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.presentation.auth.AuthUiState

enum class PinEditorMode {
    NONE,
    SETUP_NEW,
    CHANGE_EXISTING,
    RESET_PIN,
    DISABLE_LOCK
}

@Composable
internal fun SecurityDashboardSummary(
    authState: AuthUiState,
    modifier: Modifier = Modifier
) {
    val hwStatusText = when (authState.biometricCapability) {
        BiometricCapability.AVAILABLE -> stringResource(id = R.string.security_hw_available)
        BiometricCapability.NOT_ENROLLED -> stringResource(id = R.string.security_hw_not_enrolled)
        BiometricCapability.UNAVAILABLE -> stringResource(id = R.string.security_hw_unavailable)
    }

    val pinStatusText = if (authState.settings.hasConfiguredPin) {
        stringResource(id = R.string.security_dashboard_pin_set, authState.settings.pinLength)
    } else {
        stringResource(id = R.string.security_dashboard_pin_unset)
    }

    val fallbackText = if (authState.canUseDeviceCredential) {
        stringResource(id = R.string.security_dashboard_fallback_ready)
    } else {
        stringResource(id = R.string.security_dashboard_fallback_unavailable)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_dashboard_summary"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardStatusItem(
                    icon = Icons.Rounded.Fingerprint,
                    label = stringResource(id = R.string.security_hw_label),
                    value = hwStatusText
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardStatusItem(
                        icon = Icons.Rounded.Key,
                        label = stringResource(id = R.string.security_dashboard_pin_label),
                        value = pinStatusText,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardStatusItem(
                        icon = Icons.Rounded.PhonelinkLock,
                        label = stringResource(id = R.string.security_dashboard_fallback_label),
                        value = fallbackText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatusItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SecuritySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.55f
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
internal fun SettingsPinEditorCard(
    editorMode: PinEditorMode,
    currentPinInput: String,
    newPinInput: String,
    confirmPinInput: String,
    formErrorResId: Int?,
    onCurrentPinChange: (String) -> Unit,
    onNewPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onClearPin: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_pin_editor"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val editorTitleRes = when (editorMode) {
                PinEditorMode.SETUP_NEW -> R.string.security_pin_setup_title
                PinEditorMode.CHANGE_EXISTING -> R.string.security_pin_change_title
                PinEditorMode.RESET_PIN -> R.string.security_pin_reset_title
                PinEditorMode.DISABLE_LOCK -> R.string.security_pin_verify_disable_title
                PinEditorMode.NONE -> R.string.security_pin_setup_title
            }
            Text(
                text = stringResource(id = editorTitleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (editorMode == PinEditorMode.RESET_PIN) {
                Text(
                    text = stringResource(id = R.string.security_pin_reset_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (editorMode == PinEditorMode.CHANGE_EXISTING ||
                editorMode == PinEditorMode.DISABLE_LOCK
            ) {
                OutlinedTextField(
                    value = currentPinInput,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            onCurrentPinChange(value)
                        }
                    },
                    label = { Text(stringResource(id = R.string.security_pin_current_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("security_pin_current_input")
                )
            }

            if (editorMode == PinEditorMode.SETUP_NEW ||
                editorMode == PinEditorMode.CHANGE_EXISTING ||
                editorMode == PinEditorMode.RESET_PIN
            ) {
                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            onNewPinChange(value)
                        }
                    },
                    label = { Text(stringResource(id = R.string.security_pin_new_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("security_pin_input")
                )

                OutlinedTextField(
                    value = confirmPinInput,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            onConfirmPinChange(value)
                        }
                    },
                    label = { Text(stringResource(id = R.string.security_pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("security_pin_confirm_input")
                )
            }

            formErrorResId?.let { errorId ->
                Text(
                    text = stringResource(id = errorId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editorMode == PinEditorMode.RESET_PIN) {
                    TextButton(
                        onClick = onClearPin,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("security_clear_pin_button"),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(id = R.string.security_pin_clear_button))
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(id = R.string.security_cancel))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("security_pin_save_button")
                    ) {
                        val btnLabel = when (editorMode) {
                            PinEditorMode.SETUP_NEW -> R.string.security_pin_save
                            PinEditorMode.CHANGE_EXISTING -> R.string.security_pin_update
                            PinEditorMode.RESET_PIN -> R.string.security_pin_reset_save
                            PinEditorMode.DISABLE_LOCK -> R.string.security_pin_disable_confirm
                            PinEditorMode.NONE -> R.string.security_pin_save
                        }
                        Text(stringResource(id = btnLabel))
                    }
                }
            }
        }
    }
}
