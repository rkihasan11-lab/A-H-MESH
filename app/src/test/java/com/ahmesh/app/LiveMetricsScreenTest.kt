package com.ahmesh.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.LiveMetricSample
import com.ahmesh.app.domain.model.LiveSeriesVisibility
import com.ahmesh.app.domain.model.LiveStreamWindow
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.presentation.components.NetworkStatusCard
import com.ahmesh.app.presentation.screens.telemetry.LiveMetricsScreenContent
import com.ahmesh.app.presentation.screens.telemetry.LiveMetricsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveMetricsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveMetricsUiStateComputesVisibleWindowAverageAndPeaks() {
        val samples = (1..25).map { idx ->
            LiveMetricSample(
                timestamp = 1_760_000_000_000L + idx * 1500L,
                timeLabel = "12:00:%02d".format(idx),
                stabilityPercent = if (idx <= 5) 50 else 95,
                outgoingKbPerTick = idx * 2f,
                incomingKbPerTick = idx * 1f,
                connectedPeers = 2,
                discoveredPeers = 3,
                activeTransfersCount = 1,
                connectionState = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT
            )
        }
        val state = LiveMetricsUiState(
            window = LiveStreamWindow.WINDOW_30S,
            samples = samples
        )

        // WINDOW_30S retains last 20 samples (indices 6..25 -> all have stability 95)
        assertEquals(20, state.visibleSamples.size)
        assertEquals(95, state.averageStabilityPercent)
        assertEquals(50f, state.peakTrafficKb, 0.01f)
    }

    @Test
    fun liveMetricsScreenRendersDualAxisChartKpisAndControls() {
        var isStreaming = true
        var selectedWindow = LiveStreamWindow.WINDOW_60S
        var visibility = LiveSeriesVisibility()
        var probeTriggered = false

        val samples = listOf(
            LiveMetricSample(
                timestamp = 1_760_000_001_000L,
                timeLabel = "12:00:01",
                stabilityPercent = 82,
                outgoingKbPerTick = 14.5f,
                incomingKbPerTick = 4.2f,
                connectedPeers = 1,
                discoveredPeers = 2,
                activeTransfersCount = 1,
                connectionState = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT
            ),
            LiveMetricSample(
                timestamp = 1_760_000_002_500L,
                timeLabel = "12:00:02",
                stabilityPercent = 96,
                outgoingKbPerTick = 32.0f,
                incomingKbPerTick = 12.8f,
                connectedPeers = 2,
                discoveredPeers = 3,
                activeTransfersCount = 2,
                connectionState = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT
            )
        )

        val uiState = LiveMetricsUiState(
            isStreaming = isStreaming,
            window = selectedWindow,
            visibility = visibility,
            currentStatus = P2PConnectionStatus(
                state = P2PConnectionState.CONNECTED,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                connectedPeersCount = 2,
                discoveredPeersCount = 3
            ),
            samples = samples
        )

        composeRule.setContent {
            AHMeshTheme {
                LiveMetricsScreenContent(
                    uiState = uiState,
                    onNavigateBack = {},
                    onToggleStreaming = { isStreaming = !isStreaming },
                    onSelectWindow = { selectedWindow = it },
                    onToggleStability = { visibility = visibility.copy(showStability = !visibility.showStability) },
                    onToggleOutgoing = { visibility = visibility.copy(showOutgoingTraffic = !visibility.showOutgoingTraffic) },
                    onToggleIncoming = { visibility = visibility.copy(showIncomingTraffic = !visibility.showIncomingTraffic) },
                    onProbeNow = { probeTriggered = true },
                    onClearStream = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_metrics_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("live_metrics_stream_badge").assertIsDisplayed()
        composeRule.onNodeWithText("LIVE").assertIsDisplayed()
        composeRule.onNodeWithTag("live_metrics_kpi_row").assertIsDisplayed()
        composeRule.onNodeWithTag("live_window_window_30s").performScrollTo().performClick()
        assertEquals(LiveStreamWindow.WINDOW_30S, selectedWindow)

        composeRule.onNodeWithTag("live_metrics_toggle_stream_button").performClick()
        assertFalse(isStreaming)

        composeRule.onNodeWithTag("live_metrics_probe_button").performClick()
        assertTrue(probeTriggered)

        composeRule.onNodeWithTag("live_metrics_line_chart_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_metrics_line_canvas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_metrics_hud_tooltip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_series_outgoing_chip").performScrollTo().performClick()
        assertFalse(visibility.showOutgoingTraffic)

        composeRule.onNodeWithTag("live_metrics_ticks_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun liveMetricsScreenDisplaysEmptyStateBeforeFirstSample() {
        composeRule.setContent {
            AHMeshTheme {
                LiveMetricsScreenContent(
                    uiState = LiveMetricsUiState(isStreaming = false, samples = emptyList()),
                    onNavigateBack = {},
                    onToggleStreaming = {},
                    onSelectWindow = {},
                    onToggleStability = {},
                    onToggleOutgoing = {},
                    onToggleIncoming = {},
                    onProbeNow = {},
                    onClearStream = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("PAUSED").assertIsDisplayed()
        composeRule.onNodeWithTag("live_metrics_chart_empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun networkStatusCardTriggersOpenLiveMetricsCallback() {
        var liveMetricsClicked = false
        composeRule.setContent {
            AHMeshTheme {
                NetworkStatusCard(
                    status = P2PConnectionStatus(
                        state = P2PConnectionState.READY,
                        transportMode = P2PTransportMode.WIFI_DIRECT,
                        isWifiEnabled = true,
                        isWifiDirectSupported = true,
                        hasNearbyPermission = true
                    ),
                    savedPeerCount = 1,
                    onPrimaryAction = {},
                    onRefresh = {},
                    onOpenTelemetry = {},
                    onOpenLiveMetrics = { liveMetricsClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("network_live_metrics_button").assertIsDisplayed()
        composeRule.onNodeWithText("Live Line Chart").assertIsDisplayed()
        composeRule.onNodeWithTag("network_live_metrics_button").performClick()
        assertTrue(liveMetricsClicked)
    }
}
