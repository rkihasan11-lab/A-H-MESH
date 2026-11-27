package com.ahmesh.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.model.PairingCodec
import com.ahmesh.app.domain.model.PairingPayload
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.SecurityAuditRepository
import com.ahmesh.app.domain.security.AuditExportFormat
import com.ahmesh.app.domain.security.AuditExportPayload
import com.ahmesh.app.domain.security.AuditLogFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PairingErrorReason {
    INVALID_QR,
    INVALID_PUBLIC_KEY,
    SCAN_FAILED,
    SELF_QR
}

sealed interface PairingFeedback {
    data class Paired(val peer: User, val eventId: Long = System.nanoTime()) : PairingFeedback
    data class Error(
        val reason: PairingErrorReason,
        val showRetryDialog: Boolean = false
    ) : PairingFeedback
}

data class PairingUiState(
    val localIdentity: PairingPayload = PairingPayload(
        nodeId = "ah-local",
        displayName = "AH Node",
        publicKey = "local-key"
    ),
    val qrPayloadString: String = "",
    val isQrUnlocked: Boolean = false,
    val qrAuthErrorMessage: String? = null,
    val trustedPeers: List<User> = emptyList(),
    val trustedPeersCount: Int = 0,
    val scanHistory: List<ScannedQrRecord> = emptyList(),
    val qrScanStats: QrScanStatistics = QrScanStatistics(),
    val biometricAuditLogs: List<BiometricAuditRecord> = emptyList(),
    val manualCodeInput: String = "",
    val pairingFeedback: PairingFeedback? = null
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val localIdentityRepository: LocalNodeIdentityRepository,
    private val messageRepository: MessageRepository,
    private val securityAuditRepository: SecurityAuditRepository
) : ViewModel() {

    private val manualInput = MutableStateFlow("")
    private val feedback = MutableStateFlow<PairingFeedback?>(null)
    private val qrUnlocked = MutableStateFlow(false)
    private val qrAuthError = MutableStateFlow<String?>(null)

    private var lastScannedPayload: String? = null
    private var lastScannedAtMs: Long = 0L

    val uiState: StateFlow<PairingUiState> = combine(
        localIdentityRepository.localIdentity,
        messageRepository.observeAllUsers(),
        messageRepository.observeScannedQrHistory(),
        messageRepository.observeQrScanStatistics(),
        securityAuditRepository.observePairingBiometricAuditLogs(),
        manualInput,
        feedback,
        qrUnlocked,
        qrAuthError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val identity = values[0] as PairingPayload
        @Suppress("UNCHECKED_CAST")
        val users = values[1] as List<User>
        @Suppress("UNCHECKED_CAST")
        val history = values[2] as List<ScannedQrRecord>
        val scanStats = values[3] as QrScanStatistics
        @Suppress("UNCHECKED_CAST")
        val auditLogs = values[4] as List<BiometricAuditRecord>
        val manual = values[5] as String
        val currentFeedback = values[6] as PairingFeedback?
        val unlocked = values[7] as Boolean
        val authError = values[8] as String?

        val trusted = users.filter { it.isTrusted }.sortedByDescending { it.lastSeenAt }
        PairingUiState(
            localIdentity = identity,
            qrPayloadString = PairingCodec.encode(identity),
            isQrUnlocked = unlocked,
            qrAuthErrorMessage = authError,
            trustedPeers = trusted,
            trustedPeersCount = trusted.size,
            scanHistory = history,
            qrScanStats = scanStats,
            biometricAuditLogs = auditLogs,
            manualCodeInput = manual,
            pairingFeedback = currentFeedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = localIdentityRepository.localIdentity.value.let { identity ->
            PairingUiState(
                localIdentity = identity,
                qrPayloadString = PairingCodec.encode(identity)
            )
        }
    )

    fun onQrBiometricUnlocked(
        eventType: String = EVENT_BIOMETRIC_OR_DEVICE
    ) {
        qrUnlocked.value = true
        qrAuthError.value = null
        logBiometricAuditAttempt(
            outcome = BiometricAuditOutcome.SUCCEEDED,
            isSuccess = true,
            detail = null,
            authMethod = eventType
        )
    }

    fun onQrBiometricError(
        message: String,
        outcome: BiometricAuditOutcome = BiometricAuditOutcome.ERROR,
        eventType: String = EVENT_BIOMETRIC_OR_DEVICE
    ) {
        qrAuthError.value = message
        logBiometricAuditAttempt(
            outcome = outcome,
            isSuccess = false,
            detail = message,
            authMethod = eventType
        )
    }

    fun onBiometricSetupAttempt(
        outcome: BiometricAuditOutcome,
        detail: String? = null
    ) {
        logBiometricAuditAttempt(
            outcome = outcome,
            isSuccess = outcome == BiometricAuditOutcome.SUCCEEDED,
            detail = detail,
            authMethod = EVENT_BIOMETRIC_SETUP_VERIFY
        )
    }

    fun onHandshakeBiometricAttempt(
        outcome: BiometricAuditOutcome,
        detail: String? = null
    ) {
        logBiometricAuditAttempt(
            outcome = outcome,
            isSuccess = outcome == BiometricAuditOutcome.SUCCEEDED,
            detail = detail,
            authMethod = EVENT_HANDSHAKE_BIOMETRIC_AUTH
        )
    }

    fun lockQrCode() {
        qrUnlocked.value = false
    }

    fun clearBiometricAuditLogs() {
        viewModelScope.launch {
            securityAuditRepository.clearPairingBiometricAuditLogs()
        }
    }

    suspend fun buildAuditExport(format: AuditExportFormat): AuditExportPayload? {
        val allLogs = securityAuditRepository.getAllPairingBiometricAuditLogs()
        if (allLogs.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            val nowMs = System.currentTimeMillis()
            AuditExportPayload(
                format = format,
                fileName = AuditLogFormatter.buildDefaultFileName(format, nowMs),
                mimeType = format.mimeType,
                content = AuditLogFormatter.format(allLogs, format, nowMs),
                recordCount = allLogs.size
            )
        }
    }

    fun prepareAuditExport(
        format: AuditExportFormat,
        onEmpty: () -> Unit = {},
        onReady: (AuditExportPayload) -> Unit
    ) {
        viewModelScope.launch {
            val payload = buildAuditExport(format)
            if (payload == null) {
                onEmpty()
            } else {
                onReady(payload)
            }
        }
    }

    private fun logBiometricAuditAttempt(
        outcome: BiometricAuditOutcome,
        isSuccess: Boolean,
        detail: String?,
        authMethod: String = EVENT_BIOMETRIC_OR_DEVICE
    ) {
        val entry = BiometricAuditRecord(
            outcome = outcome,
            isSuccess = isSuccess,
            authMethod = authMethod,
            detail = detail,
            attemptedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            securityAuditRepository.recordPairingBiometricAttempt(entry)
        }
    }

    fun updateDisplayName(name: String) {
        localIdentityRepository.updateDisplayName(name)
    }

    fun rotateSession() {
        localIdentityRepository.rotateSessionTimestamp()
    }

    fun onManualCodeChange(value: String) {
        manualInput.value = value
        if (feedback.value is PairingFeedback.Error) {
            feedback.value = null
        }
    }

    fun submitManualCode() {
        processPairingCode(
            rawValue = manualInput.value,
            debounceSameCode = false,
            fromScanner = false
        )
    }

    fun onQrCodeScanned(rawValue: String) {
        processPairingCode(
            rawValue = rawValue,
            debounceSameCode = true,
            fromScanner = true
        )
    }

    fun onScannerFailed() {
        recordFailedQrScan(
            reason = PairingErrorReason.SCAN_FAILED,
            rawValue = "",
            decoded = null,
            timestamp = System.currentTimeMillis()
        )
        feedback.value = PairingFeedback.Error(
            reason = PairingErrorReason.SCAN_FAILED,
            showRetryDialog = true
        )
    }

    fun retryAfterScanError() {
        lastScannedPayload = null
        lastScannedAtMs = 0L
        feedback.value = null
    }

    fun dismissFeedback() {
        feedback.value = null
    }

    fun clearScanHistory() {
        viewModelScope.launch {
            messageRepository.clearScannedQrHistory()
        }
    }

    private fun processPairingCode(
        rawValue: String,
        debounceSameCode: Boolean,
        fromScanner: Boolean
    ) {
        val trimmed = rawValue.trim()
        val now = System.currentTimeMillis()
        if (trimmed.isEmpty()) {
            if (fromScanner) {
                recordFailedQrScan(
                    reason = PairingErrorReason.INVALID_QR,
                    rawValue = trimmed,
                    decoded = null,
                    timestamp = now
                )
            }
            feedback.value = PairingFeedback.Error(
                reason = PairingErrorReason.INVALID_QR,
                showRetryDialog = fromScanner
            )
            return
        }

        if (debounceSameCode && trimmed == lastScannedPayload && now - lastScannedAtMs < SCAN_DEBOUNCE_MS) {
            return
        }
        lastScannedPayload = trimmed
        lastScannedAtMs = now

        val decoded = PairingCodec.decode(trimmed)
        if (decoded == null) {
            if (fromScanner) {
                recordFailedQrScan(
                    reason = PairingErrorReason.INVALID_QR,
                    rawValue = trimmed,
                    decoded = null,
                    timestamp = now
                )
            }
            feedback.value = PairingFeedback.Error(
                reason = PairingErrorReason.INVALID_QR,
                showRetryDialog = fromScanner
            )
            return
        }

        if (!PairingCodec.isValidPublicKeyFormat(decoded.publicKey)) {
            if (fromScanner) {
                recordFailedQrScan(
                    reason = PairingErrorReason.INVALID_PUBLIC_KEY,
                    rawValue = trimmed,
                    decoded = decoded,
                    timestamp = now
                )
            }
            feedback.value = PairingFeedback.Error(
                reason = PairingErrorReason.INVALID_PUBLIC_KEY,
                showRetryDialog = true
            )
            return
        }

        val localNodeId = localIdentityRepository.localIdentity.value.nodeId
        if (decoded.nodeId == localNodeId) {
            if (fromScanner) {
                recordFailedQrScan(
                    reason = PairingErrorReason.SELF_QR,
                    rawValue = trimmed,
                    decoded = decoded,
                    timestamp = now
                )
            }
            feedback.value = PairingFeedback.Error(
                reason = PairingErrorReason.SELF_QR,
                showRetryDialog = fromScanner
            )
            return
        }

        val peerUser = User(
            id = decoded.nodeId,
            displayName = decoded.displayName,
            publicKey = decoded.publicKey,
            avatarColor = decoded.derivedAvatarColor(),
            lastSeenAt = now,
            isTrusted = true
        )
        val historyEntry = ScannedQrRecord(
            nodeId = decoded.nodeId,
            displayName = decoded.displayName,
            publicKeyFingerprint = decoded.shortKeyFingerprint(),
            rawPayload = trimmed,
            scannedAt = now,
            isSuccess = true,
            failureReason = null
        )

        viewModelScope.launch {
            messageRepository.upsertUser(peerUser)
            messageRepository.recordScannedQr(historyEntry)
            manualInput.value = ""
            feedback.value = PairingFeedback.Paired(peer = peerUser)
        }
    }

    private fun recordFailedQrScan(
        reason: PairingErrorReason,
        rawValue: String,
        decoded: PairingPayload?,
        timestamp: Long
    ) {
        val failedEntry = ScannedQrRecord(
            nodeId = decoded?.nodeId ?: "unknown",
            displayName = decoded?.displayName ?: "Unrecognized QR",
            publicKeyFingerprint = decoded?.shortKeyFingerprint() ?: "—",
            rawPayload = rawValue.take(256),
            scannedAt = timestamp,
            isSuccess = false,
            failureReason = reason.name
        )
        viewModelScope.launch {
            messageRepository.recordScannedQr(failedEntry)
        }
    }

    companion object {
        private const val SCAN_DEBOUNCE_MS = 1_500L
        const val EVENT_BIOMETRIC_OR_DEVICE = "BIOMETRIC_OR_DEVICE_CREDENTIAL"
        const val EVENT_QR_BIOMETRIC_UNLOCK = "QR_BIOMETRIC_UNLOCK"
        const val EVENT_QR_DEVICE_UNLOCK = "QR_DEVICE_UNLOCK"
        const val EVENT_BIOMETRIC_SETUP_VERIFY = "BIOMETRIC_SETUP_VERIFY"
        const val EVENT_HANDSHAKE_BIOMETRIC_AUTH = "HANDSHAKE_BIOMETRIC_AUTH"
    }
}
