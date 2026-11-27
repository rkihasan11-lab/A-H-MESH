package com.ahmesh.app.presentation.screens.telemetry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.StabilityTimelinePoint
import com.ahmesh.app.domain.model.TelemetryEventType
import com.ahmesh.app.domain.model.TelemetryTimeRange
import com.ahmesh.app.domain.model.TrafficBucketPoint
import com.ahmesh.app.domain.model.TrafficChartMetric
import com.ahmesh.app.domain.model.TransportDistributionItem
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.NetworkTelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class NetworkTelemetryUiState(
    val currentStatus: P2PConnectionStatus = P2PConnectionStatus(),
    val selectedRange: TelemetryTimeRange = TelemetryTimeRange.LAST_HOUR,
    val trafficMetric: TrafficChartMetric = TrafficChartMetric.THROUGHPUT_KB,
    val totalSamplesCount: Int = 0,
    val averageStabilityPercent: Int = 0,
    val currentStabilityPercent: Int = 0,
    val peakDiscoveredPeers: Int = 0,
    val totalOutgoingBytes: Long = 0L,
    val totalIncomingBytes: Long = 0L,
    val totalMessagesAndTransfers: Int = 0,
    val textPayloadCount: Int = 0,
    val filePayloadCount: Int = 0,
    val apkPayloadCount: Int = 0,
    val failedTransfersCount: Int = 0,
    val stabilityPoints: List<StabilityTimelinePoint> = emptyList(),
    val trafficBuckets: List<TrafficBucketPoint> = emptyList(),
    val transportDistribution: List<TransportDistributionItem> = emptyList()
) {
    val totalTrafficBytes: Long
        get() = totalOutgoingBytes + totalIncomingBytes

    val deliverySuccessRatePercent: Int
        get() {
            val total = totalMessagesAndTransfers + failedTransfersCount
            if (total <= 0) return 100
            return ((totalMessagesAndTransfers * 100f) / total).roundToInt().coerceIn(0, 100)
        }

    companion object {
        fun buildFrom(
            samples: List<NetworkTelemetryRecord>,
            messages: List<P2PMessage>,
            currentStatus: P2PConnectionStatus,
            localNodeId: String,
            selectedRange: TelemetryTimeRange = TelemetryTimeRange.LAST_HOUR,
            trafficMetric: TrafficChartMetric = TrafficChartMetric.THROUGHPUT_KB,
            nowMs: Long = System.currentTimeMillis()
        ): NetworkTelemetryUiState {
            val maxTimestamp = maxOf(
                nowMs,
                samples.maxOfOrNull { it.timestamp } ?: nowMs,
                messages.maxOfOrNull { it.timestamp } ?: nowMs
            )

            val durationMs = selectedRange.durationMs
            val windowStartMs = if (durationMs != null) {
                maxTimestamp - durationMs
            } else {
                val earliest = listOfNotNull(
                    samples.minOfOrNull { it.timestamp },
                    messages.minOfOrNull { it.timestamp }
                ).minOrNull()
                if (earliest != null && earliest < maxTimestamp) {
                    earliest
                } else {
                    maxTimestamp - TelemetryTimeRange.LAST_HOUR.durationMs!!
                }
            }

            val filteredSamples = samples
                .filter { it.timestamp in windowStartMs..maxTimestamp }
                .sortedBy { it.timestamp }
            val filteredMessages = messages
                .filter { it.timestamp in windowStartMs..maxTimestamp }
                .sortedBy { it.timestamp }

            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Downsample stability points cleanly to max 14 plotted points while preserving first & last
            val sampledRecords = if (filteredSamples.size <= 14) {
                filteredSamples
            } else {
                val step = (filteredSamples.size - 1).toFloat() / 13f
                (0..13).map { idx ->
                    filteredSamples[(idx * step).roundToInt().coerceIn(0, filteredSamples.lastIndex)]
                }
            }

            val stabilityPoints = sampledRecords.map { record ->
                StabilityTimelinePoint(
                    label = timeFormatter.format(Date(record.timestamp)),
                    stabilityPercent = record.stabilityScore,
                    connectedPeers = record.connectedPeersCount,
                    discoveredPeers = record.discoveredPeersCount,
                    state = record.connectionState,
                    transportMode = record.transportMode,
                    timestamp = record.timestamp
                )
            }

            val currentStability = filteredSamples.lastOrNull()?.stabilityScore
                ?: NetworkTelemetryRecord.computeStabilityScore(currentStatus)
            val avgStability = if (filteredSamples.isNotEmpty()) {
                filteredSamples.map { it.stabilityScore }.average().roundToInt().coerceIn(0, 100)
            } else {
                currentStability
            }
            val peakPeers = maxOf(
                filteredSamples.maxOfOrNull { maxOf(it.discoveredPeersCount, it.connectedPeersCount) } ?: 0,
                maxOf(currentStatus.discoveredPeersCount, currentStatus.connectedPeersCount)
            )

            // Traffic Bucketing across selected time range
            val bucketCount = selectedRange.bucketCount
            val spanMs = (maxTimestamp - windowStartMs).coerceAtLeast(bucketCount * 1000L)
            val bucketWidthMs = (spanMs / bucketCount).coerceAtLeast(1L)

            val trafficBuckets = (0 until bucketCount).map { idx ->
                val bStart = windowStartMs + idx * bucketWidthMs
                val bEnd = if (idx == bucketCount - 1) maxTimestamp + 1L else bStart + bucketWidthMs

                val msgsInBucket = filteredMessages.filter { it.timestamp in bStart until bEnd }
                val outgoingFromMessages = msgsInBucket
                    .filter { it.senderId == localNodeId && it.status != MessageStatus.FAILED }
                    .sumOf { maxOf(it.payloadSize, it.ciphertext.toByteArray(Charsets.UTF_8).size.toLong()) }
                val incomingFromMessages = msgsInBucket
                    .filter { it.senderId != localNodeId && it.status != MessageStatus.FAILED }
                    .sumOf { maxOf(it.payloadSize, it.ciphertext.toByteArray(Charsets.UTF_8).size.toLong()) }

                val failedMsgs = msgsInBucket.count { it.status == MessageStatus.FAILED }
                val failedSamples = filteredSamples.count {
                    it.timestamp in bStart until bEnd && it.eventType == TelemetryEventType.TRANSFER_FAILED
                }

                TrafficBucketPoint(
                    label = timeFormatter.format(Date(bStart)),
                    outgoingBytes = outgoingFromMessages,
                    incomingBytes = incomingFromMessages,
                    transferCount = msgsInBucket.count { it.status != MessageStatus.FAILED },
                    failedCount = maxOf(failedMsgs, failedSamples),
                    bucketStartMs = bStart,
                    bucketEndMs = bEnd
                )
            }

            val totalOutgoing = trafficBuckets.sumOf { it.outgoingBytes }
            val totalIncoming = trafficBuckets.sumOf { it.incomingBytes }
            val successfulMsgs = filteredMessages.filter { it.status != MessageStatus.FAILED }
            val totalFailed = maxOf(
                filteredMessages.count { it.status == MessageStatus.FAILED },
                filteredSamples.count { it.eventType == TelemetryEventType.TRANSFER_FAILED }
            )

            val transportDist = if (filteredSamples.isEmpty()) {
                listOf(
                    TransportDistributionItem(
                        transportMode = currentStatus.transportMode,
                        sampleCount = 1,
                        sharePercent = 100
                    )
                )
            } else {
                val totalCount = filteredSamples.size
                P2PTransportMode.entries.mapNotNull { mode ->
                    val count = filteredSamples.count { it.transportMode == mode }
                    if (count == 0) null
                    else TransportDistributionItem(
                        transportMode = mode,
                        sampleCount = count,
                        sharePercent = ((count * 100f) / totalCount).roundToInt().coerceIn(0, 100)
                    )
                }.sortedByDescending { it.sampleCount }
            }

            return NetworkTelemetryUiState(
                currentStatus = currentStatus,
                selectedRange = selectedRange,
                trafficMetric = trafficMetric,
                totalSamplesCount = filteredSamples.size,
                averageStabilityPercent = avgStability,
                currentStabilityPercent = currentStability,
                peakDiscoveredPeers = peakPeers,
                totalOutgoingBytes = totalOutgoing,
                totalIncomingBytes = totalIncoming,
                totalMessagesAndTransfers = successfulMsgs.size,
                textPayloadCount = successfulMsgs.count { it.type == MessageType.TEXT },
                filePayloadCount = successfulMsgs.count {
                    it.type == MessageType.FILE || it.type == MessageType.IMAGE || it.type == MessageType.VIDEO
                },
                apkPayloadCount = successfulMsgs.count { it.type == MessageType.APK },
                failedTransfersCount = totalFailed,
                stabilityPoints = stabilityPoints,
                trafficBuckets = trafficBuckets,
                transportDistribution = transportDist
            )
        }
    }
}

@HiltViewModel
class NetworkTelemetryViewModel @Inject constructor(
    private val telemetryRepository: NetworkTelemetryRepository,
    messageRepository: MessageRepository,
    networkStatusMonitor: NetworkStatusMonitor,
    localIdentityRepository: LocalNodeIdentityRepository
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TelemetryTimeRange.LAST_HOUR)
    private val trafficMetric = MutableStateFlow(TrafficChartMetric.THROUGHPUT_KB)

    val uiState: StateFlow<NetworkTelemetryUiState> = combine(
        telemetryRepository.observeAllTelemetry(),
        messageRepository.observeAllMessages(),
        networkStatusMonitor.status,
        selectedRange,
        trafficMetric
    ) { samples, messages, status, range, metric ->
        NetworkTelemetryUiState.buildFrom(
            samples = samples,
            messages = messages,
            currentStatus = status,
            localNodeId = localIdentityRepository.localIdentity.value.nodeId,
            selectedRange = range,
            trafficMetric = metric
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkTelemetryUiState(currentStatus = networkStatusMonitor.status.value)
    )

    fun selectTimeRange(range: TelemetryTimeRange) {
        selectedRange.value = range
    }

    fun selectTrafficMetric(metric: TrafficChartMetric) {
        trafficMetric.value = metric
    }

    fun sampleNow() {
        viewModelScope.launch {
            telemetryRepository.recordLiveProbe()
        }
    }

    fun clearTelemetry() {
        viewModelScope.launch {
            telemetryRepository.clearTelemetryHistory()
        }
    }
}
