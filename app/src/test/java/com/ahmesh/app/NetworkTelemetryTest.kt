package com.ahmesh.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.data.local.AppDatabase
import com.ahmesh.app.data.local.entity.NetworkTelemetryEntity
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.TelemetryEventType
import com.ahmesh.app.domain.model.TelemetryTimeRange
import com.ahmesh.app.domain.model.TrafficChartMetric
import com.ahmesh.app.presentation.components.NetworkStatusCard
import com.ahmesh.app.presentation.screens.telemetry.NetworkTelemetryScreenContent
import com.ahmesh.app.presentation.screens.telemetry.NetworkTelemetryUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkTelemetryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun computeStabilityScoreReflectsLinkHealth() {
        val connectedFormed = NetworkTelemetryRecord.computeStabilityScore(
            status = P2PConnectionStatus(
                state = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                connectedPeersCount = 2
            ),
            isGroupFormed = true
        )
        val discovering = NetworkTelemetryRecord.computeStabilityScore(
            status = P2PConnectionStatus(
                state = P2PConnectionState.DISCOVERING,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                discoveredPeersCount = 3
            )
        )
        val offline = NetworkTelemetryRecord.computeStabilityScore(
            status = P2PConnectionStatus(
                state = P2PConnectionState.OFFLINE,
                transportMode = P2PTransportMode.STANDBY
            )
        )

        assertTrue(connectedFormed >= 95)
        assertEquals(83, discovering)
        assertEquals(12, offline)
    }

    @Test
    fun uiStateBuildFromAggregatesTimeWindowsTrafficAndTransports() {
        val now = 1_760_000_000_000L
        val samples = listOf(
            // Older than 1 hour (should only appear in LAST_24_HOURS / ALL_TIME)
            NetworkTelemetryRecord(
                id = 1L,
                timestamp = now - 2L * 60L * 60L * 1000L,
                connectionState = P2PConnectionState.OFFLINE,
                transportMode = P2PTransportMode.STANDBY,
                stabilityScore = 12,
                discoveredPeersCount = 0,
                connectedPeersCount = 0,
                eventType = TelemetryEventType.STATE_CHANGE
            ),
            NetworkTelemetryRecord(
                id = 2L,
                timestamp = now - 20L * 60L * 1000L,
                connectionState = P2PConnectionState.DISCOVERING,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                stabilityScore = 80,
                discoveredPeersCount = 4,
                connectedPeersCount = 0,
                eventType = TelemetryEventType.STATE_CHANGE
            ),
            NetworkTelemetryRecord(
                id = 3L,
                timestamp = now - 5L * 60L * 1000L,
                connectionState = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                stabilityScore = 100,
                discoveredPeersCount = 4,
                connectedPeersCount = 2,
                eventType = TelemetryEventType.TRANSFER_COMPLETE
            )
        )

        val localNodeId = "local-node"
        val messages = listOf(
            P2PMessage(
                id = "m1",
                conversationId = "peer-1",
                senderId = localNodeId,
                recipientId = "peer-1",
                ciphertext = "hello mesh",
                timestamp = now - 10L * 60L * 1000L,
                status = MessageStatus.SENT,
                type = MessageType.TEXT,
                payloadSize = 1024L
            ),
            P2PMessage(
                id = "m2",
                conversationId = "peer-1",
                senderId = "peer-1",
                recipientId = localNodeId,
                ciphertext = "app.apk",
                timestamp = now - 4L * 60L * 1000L,
                status = MessageStatus.DELIVERED,
                type = MessageType.APK,
                payloadSize = 4096L
            ),
            P2PMessage(
                id = "m3",
                conversationId = "peer-1",
                senderId = localNodeId,
                recipientId = "peer-1",
                ciphertext = "broken.bin",
                timestamp = now - 3L * 60L * 1000L,
                status = MessageStatus.FAILED,
                type = MessageType.FILE,
                payloadSize = 2048L
            )
        )

        val currentStatus = P2PConnectionStatus(
            state = P2PConnectionState.CONNECTED,
            transportMode = P2PTransportMode.WIFI_DIRECT,
            connectedPeersCount = 2,
            discoveredPeersCount = 4
        )

        val hourState = NetworkTelemetryUiState.buildFrom(
            samples = samples,
            messages = messages,
            currentStatus = currentStatus,
            localNodeId = localNodeId,
            selectedRange = TelemetryTimeRange.LAST_HOUR,
            trafficMetric = TrafficChartMetric.THROUGHPUT_KB,
            nowMs = now
        )

        assertEquals(2, hourState.totalSamplesCount)
        assertEquals(90, hourState.averageStabilityPercent)
        assertEquals(4, hourState.peakDiscoveredPeers)
        assertEquals(1024L, hourState.totalOutgoingBytes)
        assertEquals(4096L, hourState.totalIncomingBytes)
        assertEquals(5120L, hourState.totalTrafficBytes)
        assertEquals(2, hourState.totalMessagesAndTransfers)
        assertEquals(1, hourState.textPayloadCount)
        assertEquals(1, hourState.apkPayloadCount)
        assertEquals(1, hourState.failedTransfersCount)
        assertEquals(6, hourState.trafficBuckets.size)
        assertEquals(P2PTransportMode.WIFI_DIRECT, hourState.transportDistribution.first().transportMode)
        assertEquals(100, hourState.transportDistribution.first().sharePercent)

        val allTimeState = NetworkTelemetryUiState.buildFrom(
            samples = samples,
            messages = messages,
            currentStatus = currentStatus,
            localNodeId = localNodeId,
            selectedRange = TelemetryTimeRange.ALL_TIME,
            trafficMetric = TrafficChartMetric.TRANSFER_COUNT,
            nowMs = now
        )
        assertEquals(3, allTimeState.totalSamplesCount)
        assertEquals(64, allTimeState.averageStabilityPercent)
        assertEquals(8, allTimeState.trafficBuckets.size)
    }

    @Test
    fun roomDatabaseStoresAndQueriesTelemetrySamples() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.networkTelemetryDao()
            val sample = NetworkTelemetryRecord(
                timestamp = 1_760_000_123_000L,
                connectionState = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                stabilityScore = 97,
                discoveredPeersCount = 3,
                connectedPeersCount = 1,
                bytesTransferred = 8192L,
                direction = NetworkTelemetryRecord.DIRECTION_OUTGOING,
                eventType = TelemetryEventType.TRANSFER_COMPLETE
            )
            dao.insertSample(NetworkTelemetryEntity.fromDomain(sample))
            val loaded = dao.observeAllSamples().first().map { it.toDomain() }
            assertEquals(1, loaded.size)
            assertEquals(97, loaded.first().stabilityScore)
            assertEquals(8192L, loaded.first().bytesTransferred)

            dao.clearAll()
            assertTrue(dao.observeAllSamples().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun telemetryScreenDisplaysChartsAndInteractiveControls() {
        var selectedRange = TelemetryTimeRange.LAST_HOUR
        var selectedMetric = TrafficChartMetric.THROUGHPUT_KB
        var probeClicked = false

        val now = 1_760_000_000_000L
        val samples = listOf(
            NetworkTelemetryRecord(
                id = 1L,
                timestamp = now - 15 * 60_000L,
                connectionState = P2PConnectionState.READY,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                stabilityScore = 82,
                discoveredPeersCount = 1,
                connectedPeersCount = 0
            ),
            NetworkTelemetryRecord(
                id = 2L,
                timestamp = now - 5 * 60_000L,
                connectionState = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                stabilityScore = 98,
                discoveredPeersCount = 3,
                connectedPeersCount = 2
            )
        )
        val uiState = NetworkTelemetryUiState.buildFrom(
            samples = samples,
            messages = emptyList(),
            currentStatus = P2PConnectionStatus(
                state = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                connectedPeersCount = 2,
                discoveredPeersCount = 3
            ),
            localNodeId = "local",
            selectedRange = selectedRange,
            trafficMetric = selectedMetric,
            nowMs = now
        )

        composeRule.setContent {
            AHMeshTheme {
                NetworkTelemetryScreenContent(
                    uiState = uiState,
                    onNavigateBack = {},
                    onSelectRange = { selectedRange = it },
                    onSelectMetric = { selectedMetric = it },
                    onSampleNow = { probeClicked = true },
                    onClearTelemetry = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("network_telemetry_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_kpi_row").assertIsDisplayed()

        composeRule.onNodeWithTag("telemetry_range_all_time").performScrollTo().performClick()
        assertEquals(TelemetryTimeRange.ALL_TIME, selectedRange)

        composeRule.onNodeWithTag("telemetry_probe_button").performClick()
        assertTrue(probeClicked)

        composeRule.onNodeWithTag("telemetry_stability_area_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_stability_tooltip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_traffic_bar_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_traffic_tooltip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_transport_card").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("telemetry_metric_count_chip").performScrollTo().performClick()
        assertEquals(TrafficChartMetric.TRANSFER_COUNT, selectedMetric)
    }

    @Test
    fun telemetryScreenDisplaysEmptyStabilityNoticeWhenNoSamples() {
        composeRule.setContent {
            AHMeshTheme {
                NetworkTelemetryScreenContent(
                    uiState = NetworkTelemetryUiState(),
                    onNavigateBack = {},
                    onSelectRange = {},
                    onSelectMetric = {},
                    onSampleNow = {},
                    onClearTelemetry = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("telemetry_stability_empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun networkStatusCardTriggersOpenTelemetryCallback() {
        var telemetryClicked = false
        composeRule.setContent {
            AHMeshTheme {
                NetworkStatusCard(
                    status = P2PConnectionStatus(
                        state = P2PConnectionState.CONNECTED,
                        transportMode = P2PTransportMode.WIFI_DIRECT,
                        isWifiEnabled = true,
                        isWifiDirectSupported = true,
                        hasNearbyPermission = true,
                        connectedPeersCount = 1
                    ),
                    savedPeerCount = 1,
                    onPrimaryAction = {},
                    onRefresh = {},
                    onOpenTelemetry = { telemetryClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("network_telemetry_button").assertIsDisplayed()
        composeRule.onNodeWithText("Traffic & Stability").assertIsDisplayed()
        composeRule.onNodeWithTag("network_telemetry_button").performClick()
        assertTrue(telemetryClicked)
    }
}
