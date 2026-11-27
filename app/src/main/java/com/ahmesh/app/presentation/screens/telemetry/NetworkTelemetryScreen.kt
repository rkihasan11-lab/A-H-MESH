package com.ahmesh.app.presentation.screens.telemetry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmesh.app.R
import com.ahmesh.app.domain.model.TelemetryTimeRange
import com.ahmesh.app.domain.model.TrafficChartMetric
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.presentation.components.ConnectionStabilityAreaChartCard
import com.ahmesh.app.presentation.components.P2PTrafficPatternChartCard
import com.ahmesh.app.presentation.components.TransportStabilityBreakdownCard

@Composable
fun NetworkTelemetryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLiveMetrics: (() -> Unit)? = null,
    viewModel: NetworkTelemetryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onNavigateBack()
    }

    NetworkTelemetryScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSelectRange = viewModel::selectTimeRange,
        onSelectMetric = viewModel::selectTrafficMetric,
        onSampleNow = viewModel::sampleNow,
        onClearTelemetry = viewModel::clearTelemetry,
        onOpenLiveMetrics = onOpenLiveMetrics,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTelemetryScreenContent(
    uiState: NetworkTelemetryUiState,
    onNavigateBack: () -> Unit,
    onSelectRange: (TelemetryTimeRange) -> Unit,
    onSelectMetric: (TrafficChartMetric) -> Unit,
    onSampleNow: () -> Unit,
    onClearTelemetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLiveMetrics: (() -> Unit)? = null
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("network_telemetry_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("telemetry_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.telemetry_back_cd)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.telemetry_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(id = R.string.telemetry_screen_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (onOpenLiveMetrics != null) {
                        IconButton(
                            onClick = onOpenLiveMetrics,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("telemetry_open_live_metrics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timeline,
                                contentDescription = stringResource(id = R.string.live_metrics_open_cd),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onSampleNow,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("telemetry_probe_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = stringResource(id = R.string.telemetry_probe_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uiState.totalSamplesCount > 0) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("telemetry_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(id = R.string.telemetry_clear_cd),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTimeRange.entries.forEach { range ->
                        val labelRes = when (range) {
                            TelemetryTimeRange.LAST_15_MIN -> R.string.telemetry_range_15m
                            TelemetryTimeRange.LAST_HOUR -> R.string.telemetry_range_1h
                            TelemetryTimeRange.LAST_24_HOURS -> R.string.telemetry_range_24h
                            TelemetryTimeRange.ALL_TIME -> R.string.telemetry_range_all
                        }
                        FilterChip(
                            selected = uiState.selectedRange == range,
                            onClick = { onSelectRange(range) },
                            label = { Text(text = stringResource(id = labelRes)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("telemetry_range_${range.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                TelemetryKpiSummaryRow(uiState = uiState)

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        ConnectionStabilityAreaChartCard(
                            points = uiState.stabilityPoints,
                            modifier = Modifier.weight(1f)
                        )
                        P2PTrafficPatternChartCard(
                            uiState = uiState,
                            onMetricChange = onSelectMetric,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    ConnectionStabilityAreaChartCard(points = uiState.stabilityPoints)
                    P2PTrafficPatternChartCard(
                        uiState = uiState,
                        onMetricChange = onSelectMetric
                    )
                }

                TransportStabilityBreakdownCard(items = uiState.transportDistribution)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(id = R.string.telemetry_clear_dialog_title)) },
            text = { Text(stringResource(id = R.string.telemetry_clear_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearTelemetry()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(id = R.string.telemetry_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(id = R.string.transfer_size_warning_cancel))
                }
            }
        )
    }
}

@Composable
private fun TelemetryKpiSummaryRow(
    uiState: NetworkTelemetryUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_kpi_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryKpiTile(
            label = stringResource(id = R.string.telemetry_kpi_stability),
            value = "${uiState.averageStabilityPercent}%",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        TelemetryKpiTile(
            label = stringResource(id = R.string.telemetry_kpi_traffic),
            value = TransferSizeLimits.formatByteSize(uiState.totalTrafficBytes),
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        TelemetryKpiTile(
            label = stringResource(id = R.string.telemetry_kpi_peak_peers),
            value = uiState.peakDiscoveredPeers.toString(),
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryKpiTile(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
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
