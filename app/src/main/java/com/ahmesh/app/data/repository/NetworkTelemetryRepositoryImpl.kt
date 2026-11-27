package com.ahmesh.app.data.repository

import com.ahmesh.app.data.local.dao.NetworkTelemetryDao
import com.ahmesh.app.data.local.entity.NetworkTelemetryEntity
import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import com.ahmesh.app.domain.model.TelemetryEventType
import com.ahmesh.app.domain.model.TransferStatus
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.NetworkTelemetryRepository
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTelemetryRepositoryImpl @Inject constructor(
    private val telemetryDao: NetworkTelemetryDao,
    private val networkStatusMonitor: NetworkStatusMonitor,
    private val wifiDirectRepository: WifiDirectRepository,
    private val transferRepository: P2PTransferRepository
) : NetworkTelemetryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recordedTransferKeys = HashSet<String>()

    init {
        startTelemetryCollectors()
    }

    override fun observeAllTelemetry(): Flow<List<NetworkTelemetryRecord>> {
        return telemetryDao.observeAllSamples().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun recordSample(record: NetworkTelemetryRecord) {
        telemetryDao.insertSample(NetworkTelemetryEntity.fromDomain(record))
    }

    override suspend fun recordLiveProbe() {
        networkStatusMonitor.refreshStatus()
        wifiDirectRepository.refreshConnectionInfo()
        val status = networkStatusMonitor.status.value
        val linkInfo = wifiDirectRepository.linkInfo.value
        val stability = NetworkTelemetryRecord.computeStabilityScore(
            status = status,
            isGroupFormed = linkInfo.isGroupFormed
        )
        recordSample(
            NetworkTelemetryRecord(
                timestamp = System.currentTimeMillis(),
                connectionState = status.state,
                transportMode = status.transportMode,
                stabilityScore = stability,
                discoveredPeersCount = status.discoveredPeersCount,
                connectedPeersCount = status.connectedPeersCount,
                bytesTransferred = 0L,
                direction = NetworkTelemetryRecord.DIRECTION_SNAPSHOT,
                eventType = TelemetryEventType.PERIODIC_PROBE
            )
        )
    }

    override suspend fun clearTelemetryHistory() {
        telemetryDao.clearAll()
    }

    private fun startTelemetryCollectors() {
        scope.launch {
            combine(
                networkStatusMonitor.status,
                wifiDirectRepository.linkInfo
            ) { status, linkInfo ->
                SnapshotKey(
                    status = status,
                    isGroupFormed = linkInfo.isGroupFormed
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val score = NetworkTelemetryRecord.computeStabilityScore(
                        status = snapshot.status,
                        isGroupFormed = snapshot.isGroupFormed
                    )
                    runCatching {
                        recordSample(
                            NetworkTelemetryRecord(
                                timestamp = System.currentTimeMillis(),
                                connectionState = snapshot.status.state,
                                transportMode = snapshot.status.transportMode,
                                stabilityScore = score,
                                discoveredPeersCount = snapshot.status.discoveredPeersCount,
                                connectedPeersCount = snapshot.status.connectedPeersCount,
                                bytesTransferred = 0L,
                                direction = NetworkTelemetryRecord.DIRECTION_SNAPSHOT,
                                eventType = TelemetryEventType.STATE_CHANGE
                            )
                        )
                    }
                }
        }

        scope.launch {
            transferRepository.activeTransfers.collect { transfers ->
                val status = networkStatusMonitor.status.value
                val linkInfo = wifiDirectRepository.linkInfo.value
                val baseScore = NetworkTelemetryRecord.computeStabilityScore(
                    status = status,
                    isGroupFormed = linkInfo.isGroupFormed
                )
                for (transfer in transfers) {
                    val isTerminal = transfer.status == TransferStatus.COMPLETED ||
                        transfer.status == TransferStatus.FAILED
                    if (!isTerminal) continue
                    val key = "${transfer.transferId}:${transfer.status.name}"
                    val shouldRecord = synchronized(recordedTransferKeys) {
                        if (recordedTransferKeys.size > 500) recordedTransferKeys.clear()
                        recordedTransferKeys.add(key)
                    }
                    if (!shouldRecord) continue

                    val eventType = if (transfer.status == TransferStatus.COMPLETED) {
                        TelemetryEventType.TRANSFER_COMPLETE
                    } else {
                        TelemetryEventType.TRANSFER_FAILED
                    }
                    val adjustedScore = if (transfer.status == TransferStatus.FAILED) {
                        (baseScore - 18).coerceAtLeast(10)
                    } else {
                        baseScore
                    }
                    runCatching {
                        recordSample(
                            NetworkTelemetryRecord(
                                timestamp = transfer.updatedAt,
                                connectionState = status.state,
                                transportMode = status.transportMode,
                                stabilityScore = adjustedScore,
                                discoveredPeersCount = status.discoveredPeersCount,
                                connectedPeersCount = status.connectedPeersCount,
                                bytesTransferred = transfer.bytesTransferred,
                                direction = transfer.direction.name,
                                eventType = eventType
                            )
                        )
                    }
                }
            }
        }
    }

    private data class SnapshotKey(
        val status: com.ahmesh.app.domain.model.P2PConnectionStatus,
        val isGroupFormed: Boolean
    )
}
