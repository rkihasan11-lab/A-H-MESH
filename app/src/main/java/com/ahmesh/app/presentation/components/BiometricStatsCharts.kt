package com.ahmesh.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.presentation.screens.biometricstats.BiometricMethodBreakdown
import com.ahmesh.app.presentation.screens.biometricstats.BiometricStatsUiState
import com.ahmesh.app.presentation.screens.biometricstats.ChartMetricMode
import com.ahmesh.app.presentation.screens.pairing.PairingViewModel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private data class RechartsBarDatum(
    val label: String,
    val count: Int,
    val ratePercent: Int,
    val barColor: Color,
    val gradientBottomColor: Color
)

@Composable
fun BiometricSuccessFailureBarChartCard(
    uiState: BiometricStatsUiState,
    onMetricModeChange: (ChartMetricMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val successLabel = stringResource(id = R.string.biometric_stats_bar_success)
    val failureLabel = stringResource(id = R.string.biometric_stats_bar_failure)
    val primaryColor = MaterialTheme.colorScheme.primary
    val primarySoft = MaterialTheme.colorScheme.primaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val errorSoft = MaterialTheme.colorScheme.errorContainer

    val bars = remember(
        uiState.successCount,
        uiState.totalFailureCount,
        uiState.successRatePercent,
        uiState.failureRatePercent,
        successLabel,
        failureLabel,
        primaryColor,
        primarySoft,
        errorColor,
        errorSoft
    ) {
        listOf(
            RechartsBarDatum(
                label = successLabel,
                count = uiState.successCount,
                ratePercent = uiState.successRatePercent,
                barColor = primaryColor,
                gradientBottomColor = primarySoft
            ),
            RechartsBarDatum(
                label = failureLabel,
                count = uiState.totalFailureCount,
                ratePercent = uiState.failureRatePercent,
                barColor = errorColor,
                gradientBottomColor = errorSoft
            )
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("biometric_stats_chart_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(id = R.string.biometric_stats_chart_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.biometric_stats_chart_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recharts-style metric toggle: Percentage Rate vs Attempt Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.metricMode == ChartMetricMode.RATE_PERCENT,
                    onClick = { onMetricModeChange(ChartMetricMode.RATE_PERCENT) },
                    label = { Text(stringResource(id = R.string.biometric_stats_mode_rate)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("biometric_chart_mode_rate"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = uiState.metricMode == ChartMetricMode.ATTEMPT_COUNT,
                    onClick = { onMetricModeChange(ChartMetricMode.ATTEMPT_COUNT) },
                    label = { Text(stringResource(id = R.string.biometric_stats_mode_count)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("biometric_chart_mode_count"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            val chartCd = stringResource(id = R.string.biometric_stats_chart_cd)
            RechartsVerticalBarCanvas(
                bars = bars,
                metricMode = uiState.metricMode,
                totalAttempts = uiState.totalAttempts,
                selectedIndex = selectedIndex,
                onSelectBar = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .semantics { contentDescription = chartCd }
                    .testTag("biometric_stats_bar_chart")
            )

            // Interactive Recharts-style Legend + Tooltip Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                bars.forEachIndexed { idx, datum ->
                    val isSelected = selectedIndex == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedIndex = idx }
                            .minimumInteractiveComponentSize()
                            .testTag("biometric_chart_legend_$idx"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            datum.barColor.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                datum.barColor
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(datum.barColor)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = datum.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${datum.ratePercent}% (${datum.count})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.totalAttempts > 0) {
                val activeDatum = bars.getOrElse(selectedIndex) { bars.first() }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("biometric_stats_tooltip"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, activeDatum.barColor.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedIndex == 0) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.ErrorOutline
                                },
                                contentDescription = null,
                                tint = activeDatum.barColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${activeDatum.label}: " + stringResource(
                                    id = R.string.biometric_stats_tooltip_attempts,
                                    activeDatum.count,
                                    uiState.totalAttempts,
                                    activeDatum.ratePercent
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (selectedIndex == 1 && uiState.totalFailureCount > 0) {
                            Text(
                                text = stringResource(
                                    id = R.string.biometric_stats_tooltip_failure_detail,
                                    uiState.failedAttemptCount,
                                    uiState.canceledCount,
                                    uiState.errorCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BiometricOutcomeBreakdownChartCard(
    uiState: BiometricStatsUiState,
    modifier: Modifier = Modifier
) {
    val verifiedColor = MaterialTheme.colorScheme.primary
    val verifiedSoft = MaterialTheme.colorScheme.primaryContainer
    val failedColor = MaterialTheme.colorScheme.error
    val failedSoft = MaterialTheme.colorScheme.errorContainer
    val canceledColor = MaterialTheme.colorScheme.secondary
    val canceledSoft = MaterialTheme.colorScheme.secondaryContainer
    val errorColor = MaterialTheme.colorScheme.tertiary
    val errorSoft = MaterialTheme.colorScheme.tertiaryContainer

    val verifiedLabel = stringResource(id = R.string.biometric_stats_bar_verified)
    val failedLabel = stringResource(id = R.string.biometric_stats_bar_failed)
    val canceledLabel = stringResource(id = R.string.biometric_stats_bar_canceled)
    val errorLabel = stringResource(id = R.string.biometric_stats_bar_error)

    val bars = remember(
        uiState,
        verifiedColor,
        verifiedSoft,
        failedColor,
        failedSoft,
        canceledColor,
        canceledSoft,
        errorColor,
        errorSoft,
        verifiedLabel,
        failedLabel,
        canceledLabel,
        errorLabel
    ) {
        listOf(
            RechartsBarDatum(
                label = verifiedLabel,
                count = uiState.successCount,
                ratePercent = uiState.successRatePercent,
                barColor = verifiedColor,
                gradientBottomColor = verifiedSoft
            ),
            RechartsBarDatum(
                label = failedLabel,
                count = uiState.failedAttemptCount,
                ratePercent = uiState.failedAttemptRatePercent,
                barColor = failedColor,
                gradientBottomColor = failedSoft
            ),
            RechartsBarDatum(
                label = canceledLabel,
                count = uiState.canceledCount,
                ratePercent = uiState.canceledRatePercent,
                barColor = canceledColor,
                gradientBottomColor = canceledSoft
            ),
            RechartsBarDatum(
                label = errorLabel,
                count = uiState.errorCount,
                ratePercent = uiState.errorRatePercent,
                barColor = errorColor,
                gradientBottomColor = errorSoft
            )
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("biometric_outcome_breakdown_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(id = R.string.biometric_stats_outcome_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(id = R.string.biometric_stats_outcome_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RechartsVerticalBarCanvas(
                bars = bars,
                metricMode = uiState.metricMode,
                totalAttempts = uiState.totalAttempts,
                selectedIndex = selectedIndex,
                onSelectBar = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .testTag("biometric_outcome_breakdown_chart")
            )
        }
    }
}

@Composable
private fun RechartsVerticalBarCanvas(
    bars: List<RechartsBarDatum>,
    metricMode: ChartMetricMode,
    totalAttempts: Int,
    selectedIndex: Int,
    onSelectBar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val valueLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val xLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val axisLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    val maxCountAxis = remember(bars, metricMode) {
        if (metricMode == ChartMetricMode.RATE_PERCENT) {
            100
        } else {
            val highest = bars.maxOfOrNull { it.count } ?: 0
            max(4, ceil(highest / 4.0).toInt() * 4)
        }
    }

    val animatedFractions = bars.mapIndexed { idx, datum ->
        val rawFraction = if (totalAttempts == 0) {
            0f
        } else if (metricMode == ChartMetricMode.RATE_PERCENT) {
            datum.ratePercent / 100f
        } else {
            datum.count.toFloat() / maxCountAxis.toFloat()
        }
        animateFloatAsState(
            targetValue = rawFraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            label = "recharts_bar_fraction_$idx"
        ).value
    }

    val barBounds = remember(bars.size) { MutableList(bars.size) { Rect.Zero } }

    Canvas(
        modifier = modifier.pointerInput(bars.size) {
            detectTapGestures { tapOffset ->
                val hitIndex = barBounds.indexOfFirst { rect ->
                    tapOffset.x in rect.left..rect.right
                }
                if (hitIndex >= 0) {
                    onSelectBar(hitIndex)
                }
            }
        }
    ) {
        val leftAxisWidth = 42.dp.toPx()
        val topPadding = 24.dp.toPx()
        val bottomAxisHeight = 32.dp.toPx()
        val rightPadding = 12.dp.toPx()

        val plotLeft = leftAxisWidth
        val plotTop = topPadding
        val plotRight = (size.width - rightPadding).coerceAtLeast(plotLeft + 40f)
        val plotBottom = (size.height - bottomAxisHeight).coerceAtLeast(plotTop + 40f)
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        // Y-axis ticks & Recharts Cartesian dashed gridlines (4 intervals)
        val gridIntervals = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        for (step in 0..gridIntervals) {
            val ratio = step.toFloat() / gridIntervals.toFloat()
            val y = plotBottom - ratio * plotHeight
            val tickVal = ((maxCountAxis * step) / gridIntervals.toFloat()).roundToInt()
            val tickText = if (metricMode == ChartMetricMode.RATE_PERCENT) {
                "$tickVal%"
            } else {
                tickVal.toString()
            }

            if (step == 0) {
                drawLine(
                    color = axisLineColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.5.dp.toPx()
                )
            } else {
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            val measuredTick = textMeasurer.measure(tickText, style = axisLabelStyle)
            drawText(
                textLayoutResult = measuredTick,
                topLeft = Offset(
                    x = (plotLeft - measuredTick.size.width - 8.dp.toPx()).coerceAtLeast(0f),
                    y = y - measuredTick.size.height / 2f
                )
            )
        }

        // Left Y-axis vertical spine
        drawLine(
            color = axisLineColor,
            start = Offset(plotLeft, plotTop),
            end = Offset(plotLeft, plotBottom),
            strokeWidth = 1.2.dp.toPx()
        )

        val slotCount = bars.size.coerceAtLeast(1)
        val slotWidth = plotWidth / slotCount
        val maxBarWidth = if (slotCount <= 2) 64.dp.toPx() else 44.dp.toPx()
        val barWidth = (slotWidth * 0.52f).coerceIn(18.dp.toPx(), maxBarWidth)
        val barCornerRadius = 9.dp.toPx()

        bars.forEachIndexed { index, datum ->
            val centerX = plotLeft + slotWidth * (index + 0.5f)
            val barLeft = centerX - barWidth / 2f
            val barRight = centerX + barWidth / 2f
            barBounds[index] = Rect(
                left = plotLeft + slotWidth * index,
                top = plotTop,
                right = plotLeft + slotWidth * (index + 1),
                bottom = plotBottom
            )

            // Subtle column background track (Recharts active bar background cue)
            drawRoundRect(
                color = trackBgColor,
                topLeft = Offset(barLeft, plotTop),
                size = Size(barWidth, plotHeight),
                cornerRadius = CornerRadius(barCornerRadius, barCornerRadius)
            )

            val fraction = animatedFractions[index]
            val barHeight = (fraction * plotHeight).let { raw ->
                if (datum.count > 0 && raw < 6.dp.toPx()) 6.dp.toPx() else raw
            }

            if (barHeight > 0f) {
                val barTop = (plotBottom - barHeight).coerceAtLeast(plotTop)
                val barRect = RoundRect(
                    left = barLeft,
                    top = barTop,
                    right = barRight,
                    bottom = plotBottom,
                    topLeftCornerRadius = CornerRadius(barCornerRadius, barCornerRadius),
                    topRightCornerRadius = CornerRadius(barCornerRadius, barCornerRadius),
                    bottomLeftCornerRadius = CornerRadius.Zero,
                    bottomRightCornerRadius = CornerRadius.Zero
                )
                val barPath = Path().apply { addRoundRect(barRect) }

                drawPath(
                    path = barPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            datum.barColor,
                            datum.barColor.copy(alpha = 0.78f)
                        ),
                        startY = barTop,
                        endY = plotBottom
                    )
                )

                if (index == selectedIndex) {
                    drawPath(
                        path = barPath,
                        color = highlightColor,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }

            // Data label above each bar
            val valueText = if (metricMode == ChartMetricMode.RATE_PERCENT) {
                "${datum.ratePercent}%"
            } else {
                datum.count.toString()
            }
            val measuredValue = textMeasurer.measure(valueText, style = valueLabelStyle)
            val barActualTop = if (barHeight > 0f) (plotBottom - barHeight) else plotBottom
            drawText(
                textLayoutResult = measuredValue,
                topLeft = Offset(
                    x = centerX - measuredValue.size.width / 2f,
                    y = (barActualTop - measuredValue.size.height - 4.dp.toPx()).coerceAtLeast(0f)
                )
            )

            // X-axis category label below baseline
            val measuredX = textMeasurer.measure(datum.label, style = xLabelStyle)
            drawText(
                textLayoutResult = measuredX,
                topLeft = Offset(
                    x = (centerX - measuredX.size.width / 2f)
                        .coerceIn(0f, (size.width - measuredX.size.width).coerceAtLeast(0f)),
                    y = plotBottom + 8.dp.toPx()
                )
            )
        }
    }
}

@Composable
fun BiometricMethodBreakdownCard(
    breakdowns: List<BiometricMethodBreakdown>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("biometric_method_breakdown_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.biometric_stats_method_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.biometric_stats_method_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            breakdowns.forEachIndexed { idx, item ->
                BiometricMethodBreakdownRow(item = item)
                if (idx < breakdowns.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun BiometricMethodBreakdownRow(
    item: BiometricMethodBreakdown
) {
    val labelRes = when (item.authMethod) {
        PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK -> R.string.pairing_event_type_qr_biometric
        PairingViewModel.EVENT_QR_DEVICE_UNLOCK -> R.string.pairing_event_type_qr_device
        PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY -> R.string.pairing_event_type_setup_verify
        PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH -> R.string.pairing_event_type_handshake
        else -> R.string.pairing_event_type_biometric_or_device
    }

    val successRatio by animateFloatAsState(
        targetValue = (item.successRatePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "method_success_ratio_${item.authMethod}"
    )
    val failureRatio by animateFloatAsState(
        targetValue = (item.failureRatePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "method_failure_ratio_${item.authMethod}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biometric_method_row_${item.authMethod}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = labelRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.successRatePercent}% / ${item.failureRatePercent}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (successRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(successRatio.coerceAtLeast(0.01f))
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (failureRatio > 0f) {
                Box(
                    modifier = Modifier
                        .weight(failureRatio.coerceAtLeast(0.01f))
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }

        Text(
            text = stringResource(
                id = R.string.biometric_stats_method_counts,
                item.successCount,
                item.failureCount
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BiometricStatsEmptyStateCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("biometric_stats_empty_state"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = stringResource(id = R.string.biometric_stats_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(id = R.string.biometric_stats_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
