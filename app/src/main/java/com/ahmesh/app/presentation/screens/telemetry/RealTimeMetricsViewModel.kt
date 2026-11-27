package com.ahmesh.app.presentation.screens.telemetry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.domain.model.LiveMetricSample
import com.ahmesh.app.domain.model.LiveSeriesVisibility
import com.ahmesh.app.domain.model.LiveStreamWindow
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.TransferDirection
import com.ahmesh.app.domain.model.TransferStatus
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.NetworkTelemetryRepository
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

data class LiveMetricsUiState(
    val isStreaming: Boolean = true,
    val window: LiveStreamWindow = LiveStreamWindow.WINDOW_60S,
    val visibility: LiveSeriesVisibility = LiveSeriesVisibility(),
    val currentStatus: P2PConnectionStatus = P2PConnectionStatus(),
    val samples: List<LiveMetricSample> = emptyList(),
    val isServerListening: Boolean = false,
    val activeTransfersCount: Int = 0
) {
    val visibleSamples: List<LiveMetricSample>
        get() = samples.takeLast(window.maxPoints)

    val latestSample: LiveMetricSample?
        get() = visibleSamples.lastOrNull()

    val averageStabilityPercent: Int
        get() {
            val list = visibleSamples
            if (list.isEmpty()) {
                return NetworkTelemetryRecord.computeStabilityScore(currentStatus)
            }
            return list.map { it.stabilityPercent }.average().roundToInt().coerceIn(0, 100)
        }

    val peakTrafficKb: Float
        get() = visibleSamples.maxOfOrNull { max(it.outgoingKbPerTick, it.incomingKbPerTick) } ?: 0f

    val totalWindowTrafficKb: Float
        get() = visibleSamples.sumOf { (it.outgoingKbPerTick + it.incomingKbPerTick).toDouble() }.toFloat()
}

@HiltViewModel
class RealTimeMetricsViewModel @Inject constructor(
    private val networkStatusMonitor: NetworkStatusMonitor,
    private val wifiDirectRepository: WifiDirectRepository,
    private val transferRepository: P2PTransferRepository,
    private val messageRepository: MessageRepository,
    private val localIdentityRepository: LocalNodeIdentityRepository,
    private val telemetryRepository: NetworkTelemetryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LiveMetricsUiState(currentStatus = networkStatusMonitor.status.value)
    )
    val uiState: StateFlow<LiveMetricsUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var lastObservedOutgoingMsgBytes: Long? = null
    private var lastObservedIncomingMsgBytes: Long? = null
    private val lastTransferBytesById = HashMap<String, Long>()

    init {
        viewModelScope.launch {
            seedInitialHistory()
            while (isActive) {
                if (_uiState.value.isStreaming) {
                    sampleTick(forceRefresh = false)
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private suspend fun seedInitialHistory() {
        val localNodeId = localIdentityRepository.localIdentity.value.nodeId
        val existingMessages = runCatching { messageRepository.observeAllMessages().first() }
            .getOrDefault(emptyList())
        val totalOut = existingMessages
            .filter { it.senderId == localNodeId && it.status != MessageStatus.FAILED }
            .sumOf { maxOf(it.payloadSize, it.ciphertext.toByteArray(Charsets.UTF_8).size.toLong()) }
        val totalIn = existingMessages
            .filter { it.senderId != localNodeId && it.status != MessageStatus.FAILED }
            .sumOf { maxOf(it.payloadSize, it.ciphertext.toByteArray(Charsets.UTF_8).size.toLong()) }
        lastObservedOutgoingMsgBytes = totalOut
        lastObservedIncomingMsgBytes = totalIn

        transferRepository.activeTransfers.value.forEach { tr ->
            lastTransferBytesById[tr.transferId] = tr.bytesTransferred
        }

        val historical = runCatching { telemetryRepository.observeAllTelemetry().first() }
            .getOrDefault(emptyList())
            .sortedBy { it.timestamp }
            .takeLast(LiveStreamWindow.WINDOW_2M.maxPoints)

        if (historical.isNotEmpty()) {
            val seeded = historical.map { rec ->
                val kb = rec.bytesTransferred / 1024f
                LiveMetricSample(
                    timestamp = rec.timestamp,
                    timeLabel = timeFormat.format(Date(rec.timestamp)),
                    stabilityPercent = rec.stabilityScore.coerceIn(0, 100),
                    outgoingKbPerTick = if (rec.direction == NetworkTelemetryRecord.DIRECTION_OUTGOING) kb else 0f,
                    incomingKbPerTick = if (rec.direction == NetworkTelemetryRecord.DIRECTION_INCOMING) kb else 0f,
                    connectedPeers = rec.connectedPeersCount,
                    discoveredPeers = rec.discoveredPeersCount,
                    activeTransfersCount = 0,
                    connectionState = rec.connectionState,
                    transportMode = rec.transportMode
                )
            }
            _uiState.update { state ->
                state.copy(
                    samples = seeded,
                    currentStatus = networkStatusMonitor.status.value,
                    isServerListening = transferRepository.isServerListening.value
                )
            }
        }
    }

    private suspend fun sampleTick(forceRefresh: Boolean) {
        if (forceRefresh) {
            networkStatusMonitor.refreshStatus()
            wifiDirectRepository.refreshConnectionInfo()
        }
        val now = System.currentTimeMillis()
        val status = networkStatusMonitor.status.value
        val linkInfo = wifiDirectRepository.linkInfo.value
        val transfers = transferRepository.activeTransfers.value
        val localNodeId = localIdentityRepository.localIdentity.value.nodeId

        // Real cumulative message bytes delta since previous tick
        val allMessages = runCatching { messageRepository.observeAllMessages().first() }
            .getOrDefault(emptyList())
        val cumulativeOutMsg = allMessages
            .filter { it.senderId == localNodeId && it.status != MessageStatus.FAILED }
            .sumOf { maxOf(it.payloadSize, it.ciphertext.toByteArray(Charsets.UTF_8).size.toLong()) }
        val cumulativeInMsg = allMessages
            .filter { it.senderId != localNodeId && it.status != MessageStatus.FAILED }
            .sumOf { maxOf(it.payloadSize, it.ciphertext.toByteArray(Charsets.UTF_8).size.toLong()) }

        val deltaOutMsg = (cumulativeOutMsg - (lastObservedOutgoingMsgBytes ?: cumulativeOutMsg))
            .coerceAtLeast(0L)
        val deltaInMsg = (cumulativeInMsg - (lastObservedIncomingMsgBytes ?: cumulativeInMsg))
            .coerceAtLeast(0L)
        lastObservedOutgoingMsgBytes = cumulativeOutMsg
        lastObservedIncomingMsgBytes = cumulativeInMsg

        // Real live socket transfer stream progress delta
        var deltaOutTransfer = 0L
        var deltaInTransfer = 0L
        transfers.forEach { tr ->
            val prev = lastTransferBytesById[tr.transferId] ?: 0L
            val delta = (tr.bytesTransferred - prev).coerceAtLeast(0L)
            lastTransferBytesById[tr.transferId] = tr.bytesTransferred
            if (tr.direction == TransferDirection.OUTGOING) {
                deltaOutTransfer += delta
            } else {
                deltaInTransfer += delta
            }
        }

        // Avoid double-counting message payload size vs active socket bytes by taking the higher real signal
        val effectiveOutBytes = maxOf(deltaOutTransfer, deltaOutMsg)
        val effectiveInBytes = maxOf(deltaInTransfer, deltaInMsg)

        val inProgressCount = transfers.count {
            it.status == TransferStatus.TRANSFERRING || it.status == TransferStatus.QUEUED
        }
        val recentFailCount = transfers.count {
            it.status == TransferStatus.FAILED && (now - it.updatedAt) <= 10_000L
        }
        val baseStability = NetworkTelemetryRecord.computeStabilityScore(
            status = status,
            isGroupFormed = linkInfo.isGroupFormed
        )
        val stabilityScore = (baseStability - recentFailCount * 12).coerceIn(5, 100)

        val point = LiveMetricSample(
            timestamp = now,
            timeLabel = timeFormat.format(Date(now)),
            stabilityPercent = stabilityScore,
            outgoingKbPerTick = effectiveOutBytes / 1024f,
            incomingKbPerTick = effectiveInBytes / 1024f,
            connectedPeers = status.connectedPeersCount,
            discoveredPeers = status.discoveredPeersCount,
            activeTransfersCount = inProgressCount,
            connectionState = status.state,
            transportMode = status.transportMode
        )

        _uiState.update { prev ->
            val updated = (prev.samples + point).takeLast(MAX_BUFFER_SAMPLES)
            prev.copy(
                currentStatus = status,
                samples = updated,
                isServerListening = transferRepository.isServerListening.value,
                activeTransfersCount = inProgressCount
            )
        }
    }

    fun toggleStreaming() {
        _uiState.update { it.copy(isStreaming = !it.isStreaming) }
    }

    fun selectWindow(window: LiveStreamWindow) {
        _uiState.update { it.copy(window = window) }
    }

    fun toggleStabilitySeries() {
        _uiState.update {
            it.copy(visibility = it.visibility.copy(showStability = !it.visibility.showStability))
        }
    }

    fun toggleOutgoingSeries() {
        _uiState.update {
            it.copy(visibility = it.visibility.copy(showOutgoingTraffic = !it.visibility.showOutgoingTraffic))
        }
    }

    fun toggleIncomingSeries() {
        _uiState.update {
            it.copy(visibility = it.visibility.copy(showIncomingTraffic = !it.visibility.showIncomingTraffic))
        }
    }

    fun probeNow() {
        viewModelScope.launch {
            telemetryRepository.recordLiveProbe()
            sampleTick(forceRefresh = true)
        }
    }

    fun clearStreamBuffer() {
        _uiState.update { it.copy(samples = emptyList()) }
    }

    companion object {
        const val TICK_INTERVAL_MS = 1500L
        const val MAX_BUFFER_SAMPLES = 90
    }
}
