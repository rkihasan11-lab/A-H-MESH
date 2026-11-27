package com.ahmesh.app.presentation.screens.biometricstats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.presentation.components.BiometricMethodBreakdownCard
import com.ahmesh.app.presentation.components.BiometricOutcomeBreakdownChartCard
import com.ahmesh.app.presentation.components.BiometricStatsEmptyStateCard
import com.ahmesh.app.presentation.components.BiometricSuccessFailureBarChartCard
import com.ahmesh.app.presentation.screens.pairing.PairingViewModel

@Composable
fun BiometricStatsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BiometricStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onNavigateBack()
    }

    BiometricStatsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onMetricModeChange = viewModel::setMetricMode,
        onClearAuditLogs = viewModel::clearAuditLogs,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricStatsScreenContent(
    uiState: BiometricStatsUiState,
    onNavigateBack: () -> Unit,
    onMetricModeChange: (ChartMetricMode) -> Unit,
    onClearAuditLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("biometric_stats_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("biometric_stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.biometric_stats_back_cd)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.biometric_stats_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(id = R.string.biometric_stats_screen_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (uiState.totalAttempts > 0) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("biometric_stats_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(id = R.string.biometric_stats_clear_cd),
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
                BiometricKpiSummaryRow(uiState = uiState)

                if (uiState.totalAttempts == 0) {
                    BiometricStatsEmptyStateCard()
                }

                if (isWide && uiState.totalAttempts > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        BiometricSuccessFailureBarChartCard(
                            uiState = uiState,
                            onMetricModeChange = onMetricModeChange,
                            modifier = Modifier.weight(1f)
                        )
                        BiometricOutcomeBreakdownChartCard(
                            uiState = uiState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    BiometricSuccessFailureBarChartCard(
                        uiState = uiState,
                        onMetricModeChange = onMetricModeChange
                    )

                    if (uiState.totalAttempts > 0) {
                        BiometricOutcomeBreakdownChartCard(uiState = uiState)
                    }
                }

                if (uiState.methodBreakdowns.isNotEmpty()) {
                    BiometricMethodBreakdownCard(breakdowns = uiState.methodBreakdowns)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(id = R.string.biometric_stats_clear_dialog_title)) },
            text = { Text(stringResource(id = R.string.biometric_stats_clear_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAuditLogs()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("biometric_stats_clear_confirm_button")
                ) {
                    Text(stringResource(id = R.string.biometric_stats_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(id = R.string.security_cancel))
                }
            }
        )
    }
}

@Composable
private fun BiometricKpiSummaryRow(
    uiState: BiometricStatsUiState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biometric_stats_kpi_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BiometricKpiCard(
            label = stringResource(id = R.string.biometric_stats_kpi_total),
            value = uiState.totalAttempts.toString(),
            accentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .testTag("biometric_kpi_total")
        )
        BiometricKpiCard(
            label = stringResource(id = R.string.biometric_stats_kpi_success_rate),
            value = if (uiState.totalAttempts > 0) "${uiState.successRatePercent}%" else "0%",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .testTag("biometric_kpi_success_rate")
        )
        BiometricKpiCard(
            label = stringResource(id = R.string.biometric_stats_kpi_failure_rate),
            value = if (uiState.totalAttempts > 0) "${uiState.failureRatePercent}%" else "0%",
            accentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .weight(1f)
                .testTag("biometric_kpi_failure_rate")
        )
    }
}

@Composable
private fun BiometricKpiCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
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
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BiometricStatsScreenPreview() {
    val sampleLogs = listOf(
        BiometricAuditRecord(
            id = 1L,
            outcome = BiometricAuditOutcome.SUCCEEDED,
            isSuccess = true,
            authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK
        ),
        BiometricAuditRecord(
            id = 2L,
            outcome = BiometricAuditOutcome.SUCCEEDED,
            isSuccess = true,
            authMethod = PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH
        ),
        BiometricAuditRecord(
            id = 3L,
            outcome = BiometricAuditOutcome.SUCCEEDED,
            isSuccess = true,
            authMethod = PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY
        ),
        BiometricAuditRecord(
            id = 4L,
            outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
            isSuccess = false,
            authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK
        ),
        BiometricAuditRecord(
            id = 5L,
            outcome = BiometricAuditOutcome.CANCELED,
            isSuccess = false,
            authMethod = PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH
        )
    )
    AHMeshTheme {
        BiometricStatsScreenContent(
            uiState = BiometricStatsUiState.fromAuditLogs(sampleLogs),
            onNavigateBack = {},
            onMetricModeChange = {},
            onClearAuditLogs = {}
        )
    }
}
