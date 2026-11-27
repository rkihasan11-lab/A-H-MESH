package com.ahmesh.app.presentation.components

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.security.BiometricAuthManager
import com.ahmesh.app.security.BiometricAuthResult

enum class BiometricSetupFeedbackKind {
    SUCCESS,
    INFO,
    ERROR
}

data class BiometricSetupFeedback(
    val kind: BiometricSetupFeedbackKind,
    val message: String
)

@Composable
fun BiometricSetupModal(
    biometricAuthManager: BiometricAuthManager,
    onDismiss: () -> Unit,
    onCapabilitiesRefreshed: () -> Unit = {},
    onSystemSettingsOpened: () -> Unit = {},
    onSystemSettingsReturned: () -> Unit = {},
    onBiometricVerified: () -> Unit = {},
    onVerificationAttempt: ((BiometricAuditOutcome, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    var capability by remember { mutableStateOf(biometricAuthManager.checkBiometricCapability()) }
    var canUseDeviceCredential by remember {
        mutableStateOf(biometricAuthManager.canAuthenticateWithDeviceCredential())
    }
    var verificationFeedback by remember { mutableStateOf<BiometricSetupFeedback?>(null) }

    fun refreshDeviceSecurityState() {
        capability = biometricAuthManager.checkBiometricCapability()
        canUseDeviceCredential = biometricAuthManager.canAuthenticateWithDeviceCredential()
        onCapabilitiesRefreshed()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onSystemSettingsReturned()
        refreshDeviceSecurityState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onSystemSettingsReturned()
                refreshDeviceSecurityState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launchSettings: (useEnrollmentFirst: Boolean) -> Unit = { useEnrollmentFirst ->
        val candidates = if (useEnrollmentFirst) {
            biometricAuthManager.createBiometricEnrollmentIntents()
        } else {
            biometricAuthManager.createSecuritySettingsIntents()
        }
        onSystemSettingsOpened()
        val launched = biometricAuthManager.launchSettingsIntents(candidates) { intent ->
            settingsLauncher.launch(intent)
        }
        if (!launched) {
            onSystemSettingsReturned()
            verificationFeedback = BiometricSetupFeedback(
                kind = BiometricSetupFeedbackKind.ERROR,
                message = context.getString(R.string.biometric_setup_result_settings_error)
            )
        }
    }

    val verifyBiometrics: () -> Unit = {
        val hostActivity = activity
        if (hostActivity == null) {
            verificationFeedback = BiometricSetupFeedback(
                kind = BiometricSetupFeedbackKind.ERROR,
                message = context.getString(R.string.auth_error_biometric_unavailable)
            )
        } else {
            biometricAuthManager.authenticateWithBiometrics(
                activity = hostActivity,
                allowDeviceCredentialFallback = false,
                title = context.getString(R.string.biometric_setup_verify_prompt_title),
                subtitle = context.getString(R.string.biometric_setup_verify_prompt_subtitle),
                negativeButtonText = context.getString(R.string.security_cancel)
            ) { result ->
                refreshDeviceSecurityState()
                when (result) {
                    BiometricAuthResult.Succeeded -> {
                        val msg = context.getString(R.string.biometric_setup_result_verified)
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.SUCCESS,
                            message = msg
                        )
                        onVerificationAttempt?.invoke(BiometricAuditOutcome.SUCCEEDED, null)
                        onBiometricVerified()
                    }
                    BiometricAuthResult.FailedAttempt -> {
                        val msg = context.getString(R.string.biometric_setup_result_failed)
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.ERROR,
                            message = msg
                        )
                        onVerificationAttempt?.invoke(BiometricAuditOutcome.FAILED_ATTEMPT, msg)
                    }
                    is BiometricAuthResult.Canceled -> {
                        val msg = context.getString(R.string.biometric_setup_result_canceled)
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.INFO,
                            message = msg
                        )
                        onVerificationAttempt?.invoke(BiometricAuditOutcome.CANCELED, msg)
                    }
                    is BiometricAuthResult.Error -> {
                        val msg = result.message
                            ?: context.getString(R.string.auth_error_biometric_unavailable)
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.ERROR,
                            message = msg
                        )
                        onVerificationAttempt?.invoke(BiometricAuditOutcome.ERROR, msg)
                    }
                }
            }
        }
    }

    val verifyDeviceCredential: () -> Unit = {
        val hostActivity = activity
        if (hostActivity == null) {
            verificationFeedback = BiometricSetupFeedback(
                kind = BiometricSetupFeedbackKind.ERROR,
                message = context.getString(R.string.auth_error_biometric_unavailable)
            )
        } else {
            biometricAuthManager.authenticateWithDeviceCredential(
                activity = hostActivity
            ) { result ->
                refreshDeviceSecurityState()
                when (result) {
                    BiometricAuthResult.Succeeded -> {
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.SUCCESS,
                            message = context.getString(R.string.biometric_setup_result_device_verified)
                        )
                    }
                    BiometricAuthResult.FailedAttempt -> {
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.ERROR,
                            message = context.getString(R.string.biometric_setup_result_failed)
                        )
                    }
                    is BiometricAuthResult.Canceled -> {
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.INFO,
                            message = context.getString(R.string.biometric_setup_result_canceled)
                        )
                    }
                    is BiometricAuthResult.Error -> {
                        verificationFeedback = BiometricSetupFeedback(
                            kind = BiometricSetupFeedbackKind.ERROR,
                            message = result.message
                                ?: context.getString(R.string.auth_error_biometric_unavailable)
                        )
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BiometricSetupModalContent(
            biometricCapability = capability,
            canUseDeviceCredential = canUseDeviceCredential,
            verificationFeedback = verificationFeedback,
            onVerifyBiometrics = verifyBiometrics,
            onVerifyDeviceCredential = verifyDeviceCredential,
            onOpenBiometricEnrollment = { launchSettings(true) },
            onOpenSecuritySettings = { launchSettings(false) },
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun BiometricSetupModalContent(
    biometricCapability: BiometricCapability,
    canUseDeviceCredential: Boolean,
    verificationFeedback: BiometricSetupFeedback?,
    onVerifyBiometrics: () -> Unit,
    onVerifyDeviceCredential: () -> Unit,
    onOpenBiometricEnrollment: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("biometric_setup_modal"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(id = R.string.biometric_setup_modal_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.biometric_setup_modal_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("biometric_modal_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(id = R.string.biometric_setup_close_cd)
                    )
                }
            }

            BiometricCapabilityStatusCard(
                biometricCapability = biometricCapability,
                canUseDeviceCredential = canUseDeviceCredential
            )

            AnimatedVisibility(visible = verificationFeedback != null) {
                verificationFeedback?.let { feedback ->
                    VerificationFeedbackCard(feedback = feedback)
                }
            }

            if (biometricCapability != BiometricCapability.AVAILABLE) {
                BiometricEnrollmentGuideCard()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (biometricCapability) {
                    BiometricCapability.AVAILABLE -> {
                        Button(
                            onClick = onVerifyBiometrics,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("biometric_modal_verify_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.biometric_setup_cta_verify))
                        }
                        OutlinedButton(
                            onClick = onOpenBiometricEnrollment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("biometric_modal_manage_settings_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.biometric_setup_cta_manage_settings),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    BiometricCapability.NOT_ENROLLED -> {
                        Button(
                            onClick = onOpenBiometricEnrollment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("biometric_modal_enroll_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.biometric_setup_cta_enroll))
                        }
                        OutlinedButton(
                            onClick = onOpenSecuritySettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("biometric_modal_security_settings_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.biometric_setup_cta_security_settings),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (canUseDeviceCredential) {
                            FilledTonalButton(
                                onClick = onVerifyDeviceCredential,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("biometric_modal_verify_device_lock_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhonelinkLock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.biometric_setup_cta_verify_device_lock))
                            }
                        }
                    }

                    BiometricCapability.UNAVAILABLE -> {
                        Button(
                            onClick = onOpenSecuritySettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("biometric_modal_security_settings_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.biometric_setup_cta_security_settings))
                        }
                        if (canUseDeviceCredential) {
                            FilledTonalButton(
                                onClick = onVerifyDeviceCredential,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("biometric_modal_verify_device_lock_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhonelinkLock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.biometric_setup_cta_verify_device_lock))
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .minimumInteractiveComponentSize()
                        .testTag("biometric_modal_done_button")
                ) {
                    Text(stringResource(id = R.string.biometric_setup_done))
                }
            }
        }
    }
}

@Composable
private fun BiometricCapabilityStatusCard(
    biometricCapability: BiometricCapability,
    canUseDeviceCredential: Boolean
) {
    val isReady = biometricCapability == BiometricCapability.AVAILABLE
    val isNotEnrolled = biometricCapability == BiometricCapability.NOT_ENROLLED

    val containerColor = when {
        isReady -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        isNotEnrolled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isReady -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        isNotEnrolled -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline
    }
    val icon = when {
        isReady -> Icons.Rounded.VerifiedUser
        isNotEnrolled -> Icons.Rounded.ErrorOutline
        else -> Icons.Rounded.Shield
    }
    val iconTint = when {
        isReady -> MaterialTheme.colorScheme.primary
        isNotEnrolled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleRes = when (biometricCapability) {
        BiometricCapability.AVAILABLE -> R.string.biometric_setup_status_enrolled_title
        BiometricCapability.NOT_ENROLLED -> R.string.biometric_setup_status_not_enrolled_title
        BiometricCapability.UNAVAILABLE -> R.string.biometric_setup_status_unavailable_title
    }
    val descRes = when (biometricCapability) {
        BiometricCapability.AVAILABLE -> R.string.biometric_setup_status_enrolled_desc
        BiometricCapability.NOT_ENROLLED -> R.string.biometric_setup_status_not_enrolled_desc
        BiometricCapability.UNAVAILABLE -> R.string.biometric_setup_status_unavailable_desc
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biometric_modal_status_card"),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(id = titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(id = descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhonelinkLock,
                        contentDescription = null,
                        tint = if (canUseDeviceCredential) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${stringResource(id = R.string.biometric_setup_device_lock_label)}: " +
                            stringResource(
                                id = if (canUseDeviceCredential) {
                                    R.string.biometric_setup_device_lock_active
                                } else {
                                    R.string.biometric_setup_device_lock_missing
                                }
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun BiometricEnrollmentGuideCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biometric_modal_guide_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(id = R.string.biometric_setup_steps_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.biometric_setup_step_1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(id = R.string.biometric_setup_step_2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(id = R.string.biometric_setup_step_3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerificationFeedbackCard(
    feedback: BiometricSetupFeedback
) {
    val containerColor = when (feedback.kind) {
        BiometricSetupFeedbackKind.SUCCESS ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        BiometricSetupFeedbackKind.ERROR ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        BiometricSetupFeedbackKind.INFO ->
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = when (feedback.kind) {
        BiometricSetupFeedbackKind.SUCCESS -> MaterialTheme.colorScheme.primary
        BiometricSetupFeedbackKind.ERROR -> MaterialTheme.colorScheme.error
        BiometricSetupFeedbackKind.INFO -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (feedback.kind) {
        BiometricSetupFeedbackKind.SUCCESS -> Icons.Rounded.CheckCircle
        BiometricSetupFeedbackKind.ERROR -> Icons.Rounded.ErrorOutline
        BiometricSetupFeedbackKind.INFO -> Icons.Rounded.Shield
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biometric_modal_verification_banner"),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = feedback.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun BiometricSetupModalNotEnrolledPreview() {
    AHMeshTheme {
        BiometricSetupModalContent(
            biometricCapability = BiometricCapability.NOT_ENROLLED,
            canUseDeviceCredential = true,
            verificationFeedback = null,
            onVerifyBiometrics = {},
            onVerifyDeviceCredential = {},
            onOpenBiometricEnrollment = {},
            onOpenSecuritySettings = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BiometricSetupModalEnrolledPreview() {
    AHMeshTheme {
        BiometricSetupModalContent(
            biometricCapability = BiometricCapability.AVAILABLE,
            canUseDeviceCredential = true,
            verificationFeedback = BiometricSetupFeedback(
                kind = BiometricSetupFeedbackKind.SUCCESS,
                message = "Biometric credential verified."
            ),
            onVerifyBiometrics = {},
            onVerifyDeviceCredential = {},
            onOpenBiometricEnrollment = {},
            onOpenSecuritySettings = {},
            onDismiss = {}
        )
    }
}
