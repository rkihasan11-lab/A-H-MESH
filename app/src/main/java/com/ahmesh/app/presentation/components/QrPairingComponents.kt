package com.ahmesh.app.presentation.components

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahmesh.app.R
import com.ahmesh.app.data.pairing.QrCodeHelper
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QrCodeGeneratorCard(
    identity: PairingPayload,
    qrPayloadString: String,
    isQrUnlocked: Boolean,
    authErrorMessage: String?,
    biometricCapability: BiometricCapability = BiometricCapability.AVAILABLE,
    onRequestUnlock: () -> Unit,
    onOpenBiometricSetup: () -> Unit = {},
    onLockQr: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onRotateSession: () -> Unit,
    onShowHandshakeQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val qrBitmap = remember(qrPayloadString, isQrUnlocked) {
        if (isQrUnlocked) {
            QrCodeHelper.generateQrBitmap(qrPayloadString, sizePx = 560)?.asImageBitmap()
        } else {
            null
        }
    }
    var aliasDraft by remember(identity.nodeId) { mutableStateOf(identity.displayName) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("qr_generator_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isQrUnlocked) Icons.Rounded.QrCode2 else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.pairing_generator_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            id = if (isQrUnlocked) {
                                R.string.pairing_generator_subtitle
                            } else {
                                R.string.pairing_qr_locked_subtitle
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isQrUnlocked) {
                    TextButton(
                        onClick = onLockQr,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_lock_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.pairing_lock_qr_cta))
                    }
                }
            }

            if (!isQrUnlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_qr_locked_card"),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.pairing_qr_locked_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_qr_locked_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (!authErrorMessage.isNullOrBlank()) {
                            Text(
                                text = authErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("pairing_qr_auth_error")
                            )
                        }
                        Button(
                            onClick = onRequestUnlock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pairing_unlock_qr_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.pairing_unlock_qr_cta))
                        }

                        if (biometricCapability != BiometricCapability.AVAILABLE) {
                            Text(
                                text = stringResource(
                                    id = if (biometricCapability == BiometricCapability.NOT_ENROLLED) {
                                        R.string.biometric_setup_status_not_enrolled_desc
                                    } else {
                                        R.string.biometric_setup_status_unavailable_desc
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("pairing_biometric_setup_hint")
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenBiometricSetup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pairing_enroll_verify_biometrics_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    id = if (biometricCapability == BiometricCapability.AVAILABLE) {
                                        R.string.biometric_setup_manage_button
                                    } else {
                                        R.string.biometric_setup_open_button
                                    }
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(232.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = stringResource(id = R.string.pairing_cd_qr_code),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("qr_code_image")
                        )
                    }
                }

                Button(
                    onClick = onShowHandshakeQr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("pairing_show_handshake_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.pairing_handshake_cta))
                }

                TactileExportQrButton(
                    onClick = {
                        shareQrCodeImage(
                            context = context,
                            scope = coroutineScope,
                            qrPayloadString = qrPayloadString,
                            nodeId = identity.nodeId
                        )
                    },
                    outlined = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_export_qr_image_button")
                )

                SaveQrToGalleryButton(
                    qrPayloadString = qrPayloadString,
                    nodeId = identity.nodeId,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = aliasDraft,
                    onValueChange = { updated ->
                        aliasDraft = updated.take(32)
                        onDisplayNameChange(aliasDraft)
                    },
                    label = { Text(stringResource(id = R.string.pairing_display_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_display_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IdentityMetricChip(
                        label = stringResource(id = R.string.pairing_node_id_label),
                        value = identity.nodeId,
                        modifier = Modifier.weight(1f)
                    )
                    IdentityMetricChip(
                        label = stringResource(id = R.string.pairing_key_fingerprint_label),
                        value = identity.shortKeyFingerprint(),
                        modifier = Modifier.weight(1.3f)
                    )
                }

                PublicKeyCopyCard(
                    publicKey = identity.publicKey,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(qrPayloadString))
                            Toast.makeText(
                                context,
                                context.getString(R.string.pairing_copied_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_copy_code_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.pairing_copy_payload),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onRotateSession,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_rotate_session_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.pairing_refresh_identity),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PublicKeyHandshakeDialog(
    identity: PairingPayload,
    qrPayloadString: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val largeQrBitmap = remember(qrPayloadString) {
        QrCodeHelper.generateQrBitmap(qrPayloadString, sizePx = 800)?.asImageBitmap()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("public_key_handshake_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.pairing_handshake_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.pairing_handshake_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Box(
                    modifier = Modifier
                        .size(264.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (largeQrBitmap != null) {
                        Image(
                            bitmap = largeQrBitmap,
                            contentDescription = stringResource(id = R.string.pairing_cd_qr_code),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IdentityMetricChip(
                        label = stringResource(id = R.string.pairing_node_id_label),
                        value = identity.nodeId,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IdentityMetricChip(
                        label = stringResource(id = R.string.pairing_key_fingerprint_label),
                        value = identity.shortKeyFingerprint(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(identity.publicKey))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pairing_public_key_copied_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.pairing_copy_public_key),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TactileExportQrButton(
                            onClick = {
                                shareQrCodeImage(
                                    context = context,
                                    scope = coroutineScope,
                                    qrPayloadString = qrPayloadString,
                                    nodeId = identity.nodeId
                                )
                            },
                            outlined = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pairing_dialog_export_qr_button")
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_handshake_close_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(id = R.string.pairing_handshake_close))
                    }
                }
            }
        }
    }
}

@Composable
fun PublicKeyTroubleshootingCard(
    identity: PairingPayload,
    isQrUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_troubleshooting_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("pairing_troubleshooting_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.pairing_troubleshooting_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_troubleshooting_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(200))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    if (isQrUnlocked) {
                        PublicKeyCopyCard(
                            publicKey = identity.publicKey,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.pairing_qr_locked_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.pairing_tip_1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_tip_2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_tip_3),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IdentityMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PublicKeyCopyCard(
    publicKey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    var copiedRecently by remember(publicKey) { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1_500L)
            copiedRecently = false
        }
    }

    Surface(
        modifier = modifier.testTag("pairing_public_key_card"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.pairing_public_key_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = publicKey,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("pairing_public_key_string")
                )
            }

            FilledTonalButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(publicKey))
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    copiedRecently = true
                    Toast.makeText(
                        context,
                        context.getString(R.string.pairing_public_key_copied_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pairing_copy_public_key_button"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (copiedRecently) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(id = R.string.pairing_cd_copy_public_key),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.pairing_copy_to_clipboard),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun TactileExportQrButton(
    onClick: () -> Unit,
    outlined: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    var tapCount by remember { mutableIntStateOf(0) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "export_qr_press_scale"
    )
    val pulseScale = remember { Animatable(1f) }
    val iconOffsetY = remember { Animatable(0f) }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            launch {
                pulseScale.snapTo(0.95f)
                pulseScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                iconOffsetY.snapTo(-4f)
                iconOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    val combinedScale = pressScale * pulseScale.value
    val buttonModifier = modifier
        .minimumInteractiveComponentSize()
        .graphicsLayer {
            scaleX = combinedScale
            scaleY = combinedScale
        }

    val triggerExport: () -> Unit = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tapCount++
        onClick()
    }

    if (outlined) {
        OutlinedButton(
            onClick = triggerExport,
            interactionSource = interactionSource,
            modifier = buttonModifier,
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { translationY = iconOffsetY.value }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(id = R.string.pairing_export_qr_image),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        FilledTonalButton(
            onClick = triggerExport,
            interactionSource = interactionSource,
            modifier = buttonModifier,
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { translationY = iconOffsetY.value }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.pairing_export_qr_image),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun shareQrCodeImage(
    context: Context,
    scope: CoroutineScope,
    qrPayloadString: String,
    nodeId: String
) {
    scope.launch {
        val launched = QrCodeHelper.sharePublicKeyQrImage(
            context = context,
            qrPayloadString = qrPayloadString,
            nodeId = nodeId,
            chooserTitle = context.getString(R.string.pairing_export_qr_chooser)
        )
        if (!launched) {
            Toast.makeText(
                context,
                context.getString(R.string.pairing_export_qr_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
internal fun SaveQrToGalleryButton(
    qrPayloadString: String,
    nodeId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    var isSaving by remember { mutableStateOf(false) }
    var savedRecently by remember(qrPayloadString) { mutableStateOf(false) }

    LaunchedEffect(savedRecently) {
        if (savedRecently) {
            delay(1_800L)
            savedRecently = false
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { targetUri ->
        if (targetUri == null) {
            isSaving = false
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val saved = QrCodeHelper.writeQrCodeToUri(
                context = context,
                targetUri = targetUri,
                qrPayloadString = qrPayloadString
            )
            isSaving = false
            if (saved) {
                savedRecently = true
                Toast.makeText(
                    context,
                    context.getString(R.string.pairing_save_gallery_success_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.pairing_save_gallery_error_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    OutlinedButton(
        onClick = {
            if (isSaving) return@OutlinedButton
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isSaving = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                coroutineScope.launch {
                    val savedUri = QrCodeHelper.saveQrCodeToGallery(
                        context = context,
                        qrPayloadString = qrPayloadString,
                        nodeId = nodeId
                    )
                    isSaving = false
                    if (savedUri != null) {
                        savedRecently = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.pairing_save_gallery_success_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.pairing_save_gallery_error_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                createDocumentLauncher.launch(QrCodeHelper.buildGalleryImageFileName(nodeId))
            }
        },
        enabled = !isSaving && qrPayloadString.isNotBlank(),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("pairing_save_to_gallery_button"),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = if (savedRecently) Icons.Rounded.Check else Icons.Rounded.Download,
                contentDescription = stringResource(id = R.string.pairing_cd_save_to_gallery),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                id = if (savedRecently) {
                    R.string.pairing_saved_to_gallery_label
                } else {
                    R.string.pairing_save_to_gallery
                }
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
