package com.ahmesh.app.presentation.screens.telemetry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.LocalIsDarkTheme
import com.ahmesh.app.core.theme.StatusConnectedDark
import com.ahmesh.app.core.theme.StatusConnectedLight
import com.ahmesh.app.domain.model.LiveMetricSample
import com.ahmesh.app.domain.model.LiveStreamWindow
import com.ahmesh.app.domain.model.NetworkTelemetryRecord
import com.ahmesh.app.presentation.components.LiveMetricsLineChartCard
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LiveMetricsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RealTimeMetricsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onNavigateBack()
    }

    LiveMetricsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleStreaming = viewModel::toggleStreaming,
        onSelectWindow = viewModel::selectWindow,
        onToggleStability = viewModel::toggleStabilitySeries,
        onToggleOutgoing = viewModel::toggleOutgoingSeries,
        onToggleIncoming = viewModel::toggleIncomingSeries,
        onProbeNow = viewModel::probeNow,
        onClearStream = viewModel::clearStreamBuffer,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMetricsScreenContent(
    uiState: LiveMetricsUiState,
    onNavigateBack: () -> Unit,
    onToggleStreaming: () -> Unit,
    onSelectWindow: (LiveStreamWindow) -> Unit,
    onToggleStability: () -> Unit,
    onToggleOutgoing: () -> Unit,
    onToggleIncoming: () -> Unit,
    onProbeNow: () -> Unit,
    onClearStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val liveColor = if (isDark) StatusConnectedDark else StatusConnectedLight
    val streamBadgeColor = if (uiState.isStreaming) liveColor else MaterialTheme.colorScheme.outline

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("live_metrics_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("live_metrics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.live_metrics_back_cd)
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = stringResource(id = R.string.live_metrics_screen_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(id = R.string.live_metrics_screen_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            modifier = Modifier.testTag("live_metrics_stream_badge"),
                            shape = RoundedCornerShape(50),
                            color = streamBadgeColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, streamBadgeColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(streamBadgeColor)
                                )
                                Text(
                                    text = if (uiState.isStreaming) {
                                        stringResource(id = R.string.live_metrics_badge_live)
                                    } else {
                                        stringResource(id = R.string.live_metrics_badge_paused)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = streamBadgeColor
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleStreaming,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("live_metrics_toggle_stream_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isStreaming) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(id = R.string.live_metrics_toggle_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onProbeNow,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("live_metrics_probe_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = stringResource(id = R.string.telemetry_probe_cd),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (uiState.samples.isNotEmpty()) {
                        IconButton(
                            onClick = onClearStream,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_metrics_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(id = R.string.live_metrics_clear_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
            val isWide = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isWide) 980.dp else 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sliding Window Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveStreamWindow.entries.forEach { windowOption ->
                        val labelRes = when (windowOption) {
                            LiveStreamWindow.WINDOW_30S -> R.string.live_window_30s
                            LiveStreamWindow.WINDOW_60S -> R.string.live_window_60s
                            LiveStreamWindow.WINDOW_2M -> R.string.live_window_2m
                        }
                        FilterChip(
                            selected = uiState.window == windowOption,
                            onClick = { onSelectWindow(windowOption) },
                            label = { Text(stringResource(id = labelRes)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_window_${windowOption.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Live KPI Summary Row
                val latestStability = uiState.latestSample?.stabilityPercent
                    ?: NetworkTelemetryRecord.computeStabilityScore(uiState.currentStatus)
                val latestRateKb = uiState.latestSample?.totalTrafficKb ?: 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_metrics_kpi_row"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiveKpiTile(
                        label = stringResource(id = R.string.live_kpi_stability),
                        value = "$latestStability%",
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    LiveKpiTile(
                        label = stringResource(id = R.string.live_kpi_throughput),
                        value = formatLiveKb(latestRateKb),
                        accentColor = liveColor,
                        modifier = Modifier.weight(1f)
                    )
                    LiveKpiTile(
                        label = stringResource(id = R.string.live_kpi_links),
                        value = "${uiState.currentStatus.connectedPeersCount}/${uiState.currentStatus.discoveredPeersCount}",
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Main Real-Time Dual-Axis D3-Inspired Line Chart Card
                LiveMetricsLineChartCard(
                    samples = uiState.visibleSamples,
                    visibility = uiState.visibility,
                    isStreaming = uiState.isStreaming,
                    onToggleStability = onToggleStability,
                    onToggleOutgoing = onToggleOutgoing,
                    onToggleIncoming = onToggleIncoming
                )

                // Recent Ticks Stream Log
                RecentLiveTicksCard(samples = uiState.visibleSamples.takeLast(5).reversed())
            }
        }
    }
}

@Composable
private fun LiveKpiTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentLiveTicksCard(
    samples: List<LiveMetricSample>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_metrics_ticks_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(id = R.string.live_ticks_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (samples.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.live_ticks_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                samples.forEach { sample ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${sample.timeLabel} • ${sample.connectionState.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        id = R.string.live_ticks_row_detail,
                                        formatLiveKb(sample.outgoingKbPerTick),
                                        formatLiveKb(sample.incomingKbPerTick),
                                        sample.transportMode.name
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${sample.stabilityPercent}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLiveKb(kb: Float): String {
    return if (kb >= 100f) {
        "${kb.roundToInt()}K"
    } else {
        String.format(Locale.US, "%.1fK", kb)
    }
}
