package com.ahmesh.app.presentation.screens.pairing

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.data.security.AuditLogExportFileWriter
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.PairingCodec
import com.ahmesh.app.domain.model.PairingPayload
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.security.AuditExportFormat
import com.ahmesh.app.domain.security.AuditExportPayload
import com.ahmesh.app.presentation.auth.AuthViewModel
import com.ahmesh.app.presentation.components.AuditLogExportSection
import com.ahmesh.app.presentation.components.BiometricAuthenticationPrompt
import com.ahmesh.app.presentation.components.BiometricAuthEventsTableSection
import com.ahmesh.app.presentation.components.BiometricSetupModal
import com.ahmesh.app.presentation.components.PublicKeyHandshakeDialog
import com.ahmesh.app.presentation.components.PublicKeyTroubleshootingCard
import com.ahmesh.app.presentation.components.QrCodeGeneratorCard
import com.ahmesh.app.presentation.components.QrCodeScannerCard
import com.ahmesh.app.presentation.components.QrScanRetryDialog
import com.ahmesh.app.presentation.components.QrScanStatsTableSection
import com.ahmesh.app.presentation.components.QrScanSuccessRateWidget
import com.ahmesh.app.presentation.components.ScannedQrHistorySection
import com.ahmesh.app.security.BiometricAuthManager
import com.ahmesh.app.security.BiometricAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

enum class PairingMode {
    GENERATE,
    SCAN,
    HISTORY
}

@Composable
fun PairingScreen(
    biometricAuthManager: BiometricAuthManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
    initialMode: PairingMode = PairingMode.GENERATE,
    onNavigateToBiometricStats: () -> Unit = {},
    onOpenPeerTransfer: (peerId: String, peerName: String) -> Unit = { _, _ -> },
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val coroutineScope = rememberCoroutineScope()
    var pendingSavePayload by remember { mutableStateOf<AuditExportPayload?>(null) }
    var showBiometricSetupModal by rememberSaveable { mutableStateOf(false) }
    var triggerHandshakeBiometricPrompt by rememberSaveable { mutableStateOf(false) }
    var pendingHandshakeAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var biometricCapability by remember { mutableStateOf(biometricAuthManager.checkBiometricCapability()) }

    val handleSaveUri: (android.net.Uri?) -> Unit = { uri ->
        val payload = pendingSavePayload
        pendingSavePayload = null
        if (uri != null && payload != null) {
            coroutineScope.launch {
                val written = withContext(Dispatchers.IO) {
                    AuditLogExportFileWriter.writeToUri(context, uri, payload.content)
                }
                val toastText = if (written) {
                    context.getString(R.string.pairing_audit_export_saved_toast, payload.recordCount)
                } else {
                    context.getString(R.string.pairing_audit_export_error_toast)
                }
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AuditExportFormat.CSV.mimeType),
        onResult = handleSaveUri
    )
    val saveTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AuditExportFormat.TEXT.mimeType),
        onResult = handleSaveUri
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    LaunchedEffect(uiState.pairingFeedback) {
        if (uiState.pairingFeedback is PairingFeedback.Paired) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            withContext(Dispatchers.Default) {
                runCatching {
                    val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
                    try {
                        generator.startTone(ToneGenerator.TONE_PROP_ACK, 140)
                    } finally {
                        generator.release()
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                biometricCapability = biometricAuthManager.checkBiometricCapability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                biometricAuthManager.cancelAuthentication()
                viewModel.lockQrCode()
            }
        }
    }

    val requestQrBiometricUnlock = {
        val hostActivity = activity
        val eventType = if (biometricCapability == BiometricCapability.AVAILABLE) {
            PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK
        } else {
            PairingViewModel.EVENT_QR_DEVICE_UNLOCK
        }
        if (hostActivity == null) {
            viewModel.onQrBiometricError(
                message = context.getString(R.string.pairing_biometric_unavailable),
                outcome = BiometricAuditOutcome.ERROR,
                eventType = eventType
            )
        } else {
            biometricAuthManager.authenticate(
                activity = hostActivity,
                allowDeviceCredentialFallback = true,
                useSystemDeviceCredentialInPrompt = true,
                title = context.getString(R.string.pairing_biometric_prompt_title),
                subtitle = context.getString(R.string.pairing_biometric_prompt_subtitle)
            ) { result ->
                when (result) {
                    is BiometricAuthResult.Succeeded -> viewModel.onQrBiometricUnlocked(
                        eventType = eventType
                    )
                    is BiometricAuthResult.Canceled -> viewModel.onQrBiometricError(
                        message = context.getString(R.string.pairing_biometric_auth_canceled),
                        outcome = BiometricAuditOutcome.CANCELED,
                        eventType = eventType
                    )
                    is BiometricAuthResult.FailedAttempt -> viewModel.onQrBiometricError(
                        message = context.getString(R.string.pairing_biometric_auth_failed),
                        outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
                        eventType = eventType
                    )
                    is BiometricAuthResult.Error -> {
                        viewModel.onQrBiometricError(
                            message = result.message ?: context.getString(R.string.pairing_biometric_unavailable),
                            outcome = BiometricAuditOutcome.ERROR,
                            eventType = eventType
                        )
                        if (result.errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                            result.errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                        ) {
                            showBiometricSetupModal = true
                        }
                    }
                }
            }
        }
    }

    PairingScreenContent(
        uiState = uiState,
        biometricCapability = biometricCapability,
        initialMode = initialMode,
        onNavigateBack = onNavigateBack,
        onNavigateToBiometricStats = onNavigateToBiometricStats,
        onOpenPeerTransfer = onOpenPeerTransfer,
        onRequestQrUnlock = requestQrBiometricUnlock,
        onOpenBiometricSetup = { showBiometricSetupModal = true },
        onLockQr = viewModel::lockQrCode,
        onDisplayNameChange = viewModel::updateDisplayName,
        onRotateSession = viewModel::rotateSession,
        onManualCodeChange = viewModel::onManualCodeChange,
        onManualSubmit = viewModel::submitManualCode,
        onQrCodeDetected = viewModel::onQrCodeScanned,
        onScanFailed = viewModel::onScannerFailed,
        onRetryScan = viewModel::retryAfterScanError,
        onClearScanHistory = viewModel::clearScanHistory,
        onClearBiometricAuditLogs = viewModel::clearBiometricAuditLogs,
        onSaveAuditLog = { format ->
            viewModel.prepareAuditExport(
                format = format,
                onEmpty = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.pairing_audit_export_empty_hint),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onReady = { payload ->
                    pendingSavePayload = payload
                    when (payload.format) {
                        AuditExportFormat.CSV -> saveCsvLauncher.launch(payload.fileName)
                        AuditExportFormat.TEXT -> saveTextLauncher.launch(payload.fileName)
                    }
                }
            )
        },
        onShareAuditLog = { format ->
            viewModel.prepareAuditExport(
                format = format,
                onEmpty = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.pairing_audit_export_empty_hint),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onReady = { payload ->
                    coroutineScope.launch {
                        val shareUri = withContext(Dispatchers.IO) {
                            AuditLogExportFileWriter.createShareUri(context, payload)
                        }
                        if (shareUri == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.pairing_audit_export_error_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val chooser = AuditLogExportFileWriter.buildShareChooserIntent(
                                context = context,
                                uri = shareUri,
                                payload = payload,
                                chooserTitle = context.getString(R.string.pairing_audit_export_chooser)
                            )
                            runCatching { context.startActivity(chooser) }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pairing_audit_export_error_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            )
        },
        onDismissFeedback = viewModel::dismissFeedback,
        onRequestHandshakeAuth = { action ->
            pendingHandshakeAction = action
            triggerHandshakeBiometricPrompt = true
        },
        modifier = modifier
    )

    BiometricAuthenticationPrompt(
        trigger = triggerHandshakeBiometricPrompt,
        biometricAuthManager = biometricAuthManager,
        title = stringResource(id = R.string.pairing_handshake_biometric_title),
        subtitle = stringResource(id = R.string.pairing_handshake_biometric_subtitle),
        onSuccess = {
            viewModel.onHandshakeBiometricAttempt(BiometricAuditOutcome.SUCCEEDED)
            pendingHandshakeAction?.invoke()
            pendingHandshakeAction = null
            triggerHandshakeBiometricPrompt = false
        },
        onError = { msg ->
            viewModel.onHandshakeBiometricAttempt(
                outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
                detail = msg
            )
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            pendingHandshakeAction = null
            triggerHandshakeBiometricPrompt = false
        },
        onCanceled = {
            viewModel.onHandshakeBiometricAttempt(BiometricAuditOutcome.CANCELED)
            pendingHandshakeAction = null
            triggerHandshakeBiometricPrompt = false
        },
        onDismiss = {
            triggerHandshakeBiometricPrompt = false
        }
    )

    if (showBiometricSetupModal) {
        BiometricSetupModal(
            biometricAuthManager = biometricAuthManager,
            onDismiss = {
                biometricCapability = biometricAuthManager.checkBiometricCapability()
                showBiometricSetupModal = false
            },
            onCapabilitiesRefreshed = {
                biometricCapability = biometricAuthManager.checkBiometricCapability()
                authViewModel?.refreshCapabilities()
            },
            onSystemSettingsOpened = { authViewModel?.onSystemSecuritySettingsOpened() },
            onSystemSettingsReturned = {
                authViewModel?.onSystemSecuritySettingsReturned()
                biometricCapability = biometricAuthManager.checkBiometricCapability()
            },
            onBiometricVerified = {
                biometricCapability = biometricAuthManager.checkBiometricCapability()
                authViewModel?.refreshCapabilities()
            },
            onVerificationAttempt = viewModel::onBiometricSetupAttempt
        )
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreenContent(
    uiState: PairingUiState,
    biometricCapability: BiometricCapability,
    onNavigateBack: () -> Unit,
    onRequestQrUnlock: () -> Unit,
    onOpenBiometricSetup: () -> Unit,
    onLockQr: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onRotateSession: () -> Unit,
    onManualCodeChange: (String) -> Unit,
    onManualSubmit: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onScanFailed: () -> Unit,
    onRetryScan: () -> Unit,
    onClearScanHistory: () -> Unit,
    onClearBiometricAuditLogs: () -> Unit,
    onSaveAuditLog: (AuditExportFormat) -> Unit,
    onShareAuditLog: (AuditExportFormat) -> Unit,
    onDismissFeedback: () -> Unit,
    initialMode: PairingMode = PairingMode.GENERATE,
    onNavigateToBiometricStats: () -> Unit = {},
    onOpenPeerTransfer: (peerId: String, peerName: String) -> Unit = { _, _ -> },
    onRequestHandshakeAuth: (onSuccess: () -> Unit) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var showHandshakeDialog by rememberSaveable { mutableStateOf(false) }

    val errorFeedback = uiState.pairingFeedback as? PairingFeedback.Error
    val hasScanError = errorFeedback != null

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("pairing_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_pairing_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(id = R.string.pairing_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToBiometricStats,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_biometric_stats_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = stringResource(id = R.string.pairing_biometric_stats_topbar_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenBiometricSetup,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_biometric_setup_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = stringResource(id = R.string.biometric_setup_open_cd),
                            tint = if (biometricCapability == BiometricCapability.AVAILABLE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Surface(
                        modifier = Modifier.padding(end = 12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.pairing_peers_badge,
                                uiState.trustedPeersCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideLayout = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isWideLayout) 1000.dp else 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = uiState.pairingFeedback != null) {
                    uiState.pairingFeedback?.let { result ->
                        PairingFeedbackBanner(
                            feedback = result,
                            onOpenPeerTransfer = onOpenPeerTransfer,
                            onDismiss = onDismissFeedback
                        )
                    }
                }

                if (isWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        QrCodeGeneratorCard(
                            identity = uiState.localIdentity,
                            qrPayloadString = uiState.qrPayloadString,
                            isQrUnlocked = uiState.isQrUnlocked,
                            authErrorMessage = uiState.qrAuthErrorMessage,
                            biometricCapability = biometricCapability,
                            onRequestUnlock = onRequestQrUnlock,
                            onOpenBiometricSetup = onOpenBiometricSetup,
                            onLockQr = onLockQr,
                            onDisplayNameChange = onDisplayNameChange,
                            onRotateSession = onRotateSession,
                            onShowHandshakeQr = {
                                if (uiState.isQrUnlocked) {
                                    onRequestHandshakeAuth {
                                        showHandshakeDialog = true
                                    }
                                } else {
                                    onRequestQrUnlock()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QrCodeScannerCard(
                            manualCodeInput = uiState.manualCodeInput,
                            hasScanError = hasScanError,
                            onManualCodeChange = onManualCodeChange,
                            onManualSubmit = onManualSubmit,
                            onQrCodeDetected = onQrCodeDetected,
                            onScanFailed = onScanFailed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedMode == PairingMode.GENERATE,
                            onClick = { selectedMode = PairingMode.GENERATE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            modifier = Modifier.testTag("pairing_tab_generate"),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.pairing_tab_my_qr),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SegmentedButton(
                            selected = selectedMode == PairingMode.SCAN,
                            onClick = { selectedMode = PairingMode.SCAN },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            modifier = Modifier.testTag("pairing_tab_scan"),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.pairing_tab_scan_qr),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SegmentedButton(
                            selected = selectedMode == PairingMode.HISTORY,
                            onClick = { selectedMode = PairingMode.HISTORY },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            modifier = Modifier.testTag("pairing_tab_history"),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.pairing_tab_history),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Crossfade(
                        targetState = selectedMode,
                        animationSpec = tween(durationMillis = 200),
                        label = "pairing_mode_crossfade"
                    ) { mode ->
                        when (mode) {
                            PairingMode.GENERATE -> {
                                QrCodeGeneratorCard(
                                    identity = uiState.localIdentity,
                                    qrPayloadString = uiState.qrPayloadString,
                                    isQrUnlocked = uiState.isQrUnlocked,
                                    authErrorMessage = uiState.qrAuthErrorMessage,
                                    biometricCapability = biometricCapability,
                                    onRequestUnlock = onRequestQrUnlock,
                                    onOpenBiometricSetup = onOpenBiometricSetup,
                                    onLockQr = onLockQr,
                                    onDisplayNameChange = onDisplayNameChange,
                                    onRotateSession = onRotateSession,
                                    onShowHandshakeQr = {
                                        if (uiState.isQrUnlocked) {
                                            onRequestHandshakeAuth {
                                                showHandshakeDialog = true
                                            }
                                        } else {
                                            onRequestQrUnlock()
                                        }
                                    }
                                )
                            }
                            PairingMode.SCAN -> {
                                QrCodeScannerCard(
                                    manualCodeInput = uiState.manualCodeInput,
                                    hasScanError = hasScanError,
                                    onManualCodeChange = onManualCodeChange,
                                    onManualSubmit = onManualSubmit,
                                    onQrCodeDetected = onQrCodeDetected,
                                    onScanFailed = onScanFailed
                                )
                            }
                            PairingMode.HISTORY -> {
                                ScannedQrHistorySection(
                                    history = uiState.scanHistory,
                                    onClearHistory = onClearScanHistory
                                )
                            }
                        }
                    }
                }

                PublicKeyTroubleshootingCard(
                    identity = uiState.localIdentity,
                    isQrUnlocked = uiState.isQrUnlocked
                )

                TrustedPeersSection(
                    peers = uiState.trustedPeers,
                    onPeerClick = { peer -> onOpenPeerTransfer(peer.id, peer.displayName) }
                )

                QrScanStatsTableSection(stats = uiState.qrScanStats)

                if (isWideLayout) {
                    ScannedQrHistorySection(
                        history = uiState.scanHistory,
                        onClearHistory = onClearScanHistory
                    )
                }

                BiometricAuditLogSection(
                    auditLogs = uiState.biometricAuditLogs,
                    qrScanStats = uiState.qrScanStats,
                    onClearLogs = onClearBiometricAuditLogs
                )

                BiometricAuthEventsTableSection(
                    auditLogs = uiState.biometricAuditLogs,
                    onOpenAnalytics = onNavigateToBiometricStats
                )

                AuditLogExportSection(
                    auditLogCount = uiState.biometricAuditLogs.size,
                    onSaveAuditLog = onSaveAuditLog,
                    onShareAuditLog = onShareAuditLog
                )
            }
        }
    }

    if (showHandshakeDialog && uiState.isQrUnlocked) {
        PublicKeyHandshakeDialog(
            identity = uiState.localIdentity,
            qrPayloadString = uiState.qrPayloadString,
            onDismiss = { showHandshakeDialog = false }
        )
    }

    if (errorFeedback != null && errorFeedback.showRetryDialog) {
        QrScanRetryDialog(
            message = stringResource(id = errorFeedback.reason.toMessageRes()),
            onRetry = {
                selectedMode = PairingMode.SCAN
                onRetryScan()
            },
            onDismiss = onDismissFeedback
        )
    }
}

private fun PairingErrorReason.toMessageRes(): Int = when (this) {
    PairingErrorReason.INVALID_QR -> R.string.pairing_error_invalid_qr
    PairingErrorReason.INVALID_PUBLIC_KEY -> R.string.pairing_error_invalid_public_key
    PairingErrorReason.SCAN_FAILED -> R.string.pairing_error_scan_failed
    PairingErrorReason.SELF_QR -> R.string.pairing_error_self_qr
}

@Composable
private fun PairingFeedbackBanner(
    feedback: PairingFeedback,
    onOpenPeerTransfer: (peerId: String, peerName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isSuccess = feedback is PairingFeedback.Paired
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_feedback_banner"),
        shape = RoundedCornerShape(18.dp),
        color = if (isSuccess) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    when (feedback) {
                        is PairingFeedback.Paired -> {
                            Text(
                                text = stringResource(id = R.string.pairing_success_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.pairing_success_subtitle,
                                    feedback.peer.displayName,
                                    feedback.peer.id
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        is PairingFeedback.Error -> {
                            Text(
                                text = stringResource(id = feedback.reason.toMessageRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(id = R.string.pairing_dismiss_feedback))
                }
            }

            if (feedback is PairingFeedback.Paired) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { onOpenPeerTransfer(feedback.peer.id, feedback.peer.displayName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("pairing_feedback_open_transfer_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(id = R.string.pairing_open_peer_transfer))
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(
                            id = R.string.pairing_cd_open_peer_transfer,
                            feedback.peer.displayName
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedPeersSection(
    peers: List<User>,
    onPeerClick: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_trusted_peers_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(id = R.string.pairing_trusted_peers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (peers.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.pairing_trusted_peers_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                peers.forEach { peer ->
                    Surface(
                        onClick = { onPeerClick(peer) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_trusted_peer_${peer.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(peer.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.displayName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = peer.id,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = peer.displayName,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = stringResource(
                                    id = R.string.pairing_cd_open_peer_transfer,
                                    peer.displayName
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricAuditLogSection(
    auditLogs: List<BiometricAuditRecord>,
    qrScanStats: QrScanStatistics,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    val successCount = remember(auditLogs) { auditLogs.count { it.isSuccess } }
    val failureCount = remember(auditLogs) { auditLogs.count { !it.isSuccess } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_biometric_audit_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.pairing_audit_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (auditLogs.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    id = R.string.pairing_audit_summary_counts,
                                    successCount,
                                    failureCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (auditLogs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pairing_biometric_audit_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(id = R.string.pairing_audit_cd_clear),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.pairing_audit_clear))
                    }
                }
            }

            QrScanSuccessRateWidget(stats = qrScanStats)

            if (auditLogs.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.pairing_audit_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                auditLogs.forEach { entry ->
                    val outcomeLabelRes = when (entry.outcome) {
                        BiometricAuditOutcome.SUCCEEDED -> R.string.pairing_audit_outcome_succeeded
                        BiometricAuditOutcome.FAILED_ATTEMPT -> R.string.pairing_audit_outcome_failed
                        BiometricAuditOutcome.CANCELED -> R.string.pairing_audit_outcome_canceled
                        BiometricAuditOutcome.ERROR -> R.string.pairing_audit_outcome_error
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pairing_biometric_audit_item_${entry.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (entry.isSuccess) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (entry.isSuccess) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (entry.isSuccess) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (entry.isSuccess) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = outcomeLabelRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (entry.isSuccess) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Text(
                                        text = dateFormatter.format(Date(entry.attemptedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!entry.detail.isNullOrBlank()) {
                                    Text(
                                        text = entry.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairingScreenPreview() {
    val sample = PairingPayload(
        nodeId = "ah-7f92a1c4b0",
        displayName = "Pixel-Mesh-7F92",
        publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsample"
    )
    AHMeshTheme {
        PairingScreenContent(
            uiState = PairingUiState(
                localIdentity = sample,
                qrPayloadString = PairingCodec.encode(sample),
                isQrUnlocked = true,
                trustedPeersCount = 2
            ),
            biometricCapability = BiometricCapability.AVAILABLE,
            onNavigateBack = {},
            onRequestQrUnlock = {},
            onOpenBiometricSetup = {},
            onLockQr = {},
            onDisplayNameChange = {},
            onRotateSession = {},
            onManualCodeChange = {},
            onManualSubmit = {},
            onQrCodeDetected = {},
            onScanFailed = {},
            onRetryScan = {},
            onClearScanHistory = {},
            onClearBiometricAuditLogs = {},
            onSaveAuditLog = {},
            onShareAuditLog = {},
            onDismissFeedback = {}
        )
    }
}
