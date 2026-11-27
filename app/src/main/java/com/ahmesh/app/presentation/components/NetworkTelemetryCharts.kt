package com.ahmesh.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.ahmesh.app.core.theme.LocalIsDarkTheme
import com.ahmesh.app.core.theme.StatusConnectedDark
import com.ahmesh.app.core.theme.StatusConnectedLight
import com.ahmesh.app.core.theme.StatusDiscoveringDark
import com.ahmesh.app.core.theme.StatusDiscoveringLight
import com.ahmesh.app.core.theme.StatusWarningDark
import com.ahmesh.app.core.theme.StatusWarningLight
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.StabilityTimelinePoint
import com.ahmesh.app.domain.model.TrafficBucketPoint
import com.ahmesh.app.domain.model.TrafficChartMetric
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.domain.model.TransportDistributionItem
import com.ahmesh.app.presentation.screens.telemetry.NetworkTelemetryUiState
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ConnectionStabilityAreaChartCard(
    points: List<StabilityTimelinePoint>,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val connectedColor = if (isDark) StatusConnectedDark else StatusConnectedLight
    val discoveringColor = if (isDark) StatusDiscoveringDark else StatusDiscoveringLight
    val warningColor = if (isDark) StatusWarningDark else StatusWarningLight
    val errorColor = MaterialTheme.colorScheme.error

    var selectedIndex by remember(points.size) {
        mutableIntStateOf((points.lastIndex).coerceAtLeast(0))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_stability_card"),
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
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.telemetry_stability_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.telemetry_stability_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (points.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telemetry_stability_empty"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = stringResource(id = R.string.telemetry_stability_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val safeIndex = selectedIndex.coerceIn(0, points.lastIndex)
                val chartCd = stringResource(id = R.string.telemetry_stability_chart_cd)
                val textMeasurer = rememberTextMeasurer()
                val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                val axisStyle = MaterialTheme.typography.labelSmall.copy(
                    color = axisColor,
                    fontFamily = FontFamily.Monospace
                )

                val animProgress by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                    label = "stability_area_reveal"
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .semantics { contentDescription = chartCd }
                        .testTag("telemetry_stability_area_chart")
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPad = 42.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                val relX = (offset.x - leftPad).coerceIn(0f, plotW)
                                val idx = if (points.size <= 1) {
                                    0
                                } else {
                                    ((relX / plotW) * (points.size - 1))
                                        .roundToInt()
                                        .coerceIn(0, points.lastIndex)
                                }
                                selectedIndex = idx
                            }
                        }
                ) {
                    val leftPad = 42.dp.toPx()
                    val rightPad = 14.dp.toPx()
                    val topPad = 14.dp.toPx()
                    val bottomPad = 28.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    val gridPercents = listOf(0, 25, 50, 75, 100)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                    gridPercents.forEach { tick ->
                        val ratio = tick / 100f
                        val y = topPad + plotH * (1f - ratio)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + plotW, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (tick == 0) null else dashEffect
                        )
                        val labelLayout = textMeasurer.measure("$tick%", axisStyle)
                        drawText(
                            textLayoutResult = labelLayout,
                            topLeft = Offset(
                                x = (leftPad - labelLayout.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                                y = y - labelLayout.size.height / 2f
                            )
                        )
                    }

                    val coords = points.mapIndexed { idx, pt ->
                        val x = if (points.size == 1) {
                            leftPad + plotW / 2f
                        } else {
                            leftPad + (idx.toFloat() / (points.size - 1)) * plotW
                        }
                        val normalized = (pt.stabilityPercent / 100f).coerceIn(0f, 1f) * animProgress
                        val y = topPad + plotH * (1f - normalized)
                        Offset(x, y)
                    }

                    if (coords.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 0 until coords.lastIndex) {
                                val p0 = coords[i]
                                val p1 = coords[i + 1]
                                val midX = (p0.x + p1.x) / 2f
                                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                            }
                        }

                        val areaPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().x, topPad + plotH)
                            lineTo(coords.first().x, topPad + plotH)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.34f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = topPad,
                                endY = topPad + plotH
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Active point vertical crosshair
                    val selectedCoord = coords[safeIndex]
                    drawLine(
                        color = primaryColor.copy(alpha = 0.45f),
                        start = Offset(selectedCoord.x, topPad),
                        end = Offset(selectedCoord.x, topPad + plotH),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    coords.forEachIndexed { idx, pt ->
                        val stateColor = when (points[idx].state) {
                            P2PConnectionState.CONNECTED -> connectedColor
                            P2PConnectionState.DISCOVERING -> discoveringColor
                            P2PConnectionState.READY -> primaryColor
                            P2PConnectionState.PERMISSION_REQUIRED -> warningColor
                            P2PConnectionState.OFFLINE -> errorColor
                        }
                        val isSel = idx == safeIndex
                        if (isSel) {
                            drawCircle(
                                color = stateColor.copy(alpha = 0.28f),
                                radius = 9.dp.toPx(),
                                center = pt
                            )
                        }
                        drawCircle(
                            color = stateColor,
                            radius = if (isSel) 5.dp.toPx() else 3.5.dp.toPx(),
                            center = pt
                        )
                    }

                    // X-axis sparse labels (first, middle, last)
                    val labelIndices = linkedSetOf(0, coords.lastIndex / 2, coords.lastIndex)
                    labelIndices.forEach { idx ->
                        val textLayout = textMeasurer.measure(points[idx].label, axisStyle)
                        val x = (coords[idx].x - textLayout.size.width / 2f)
                            .coerceIn(leftPad, leftPad + plotW - textLayout.size.width)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(x, topPad + plotH + 6.dp.toPx())
                        )
                    }
                }

                val activePoint = points[safeIndex]
                val activeColor = when (activePoint.state) {
                    P2PConnectionState.CONNECTED -> connectedColor
                    P2PConnectionState.DISCOVERING -> discoveringColor
                    P2PConnectionState.READY -> primaryColor
                    P2PConnectionState.PERMISSION_REQUIRED -> warningColor
                    P2PConnectionState.OFFLINE -> errorColor
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telemetry_stability_tooltip"),
                    shape = RoundedCornerShape(14.dp),
                    color = activeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${activePoint.label} • ${activePoint.state.name} (${activePoint.transportMode.name})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = activeColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.telemetry_stability_tooltip_detail,
                                    activePoint.stabilityPercent,
                                    activePoint.connectedPeers,
                                    activePoint.discoveredPeers
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun P2PTrafficPatternChartCard(
    uiState: NetworkTelemetryUiState,
    onMetricChange: (TrafficChartMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    val outgoingColor = MaterialTheme.colorScheme.primary
    val secondaryBarColor = if (uiState.trafficMetric == TrafficChartMetric.THROUGHPUT_KB) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }
    val buckets = uiState.trafficBuckets

    var selectedBucketIndex by remember(buckets.size) {
        mutableIntStateOf((buckets.lastIndex).coerceAtLeast(0))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_traffic_card"),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.telemetry_traffic_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.telemetry_traffic_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.trafficMetric == TrafficChartMetric.THROUGHPUT_KB,
                    onClick = { onMetricChange(TrafficChartMetric.THROUGHPUT_KB) },
                    label = { Text(stringResource(id = R.string.telemetry_metric_volume)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("telemetry_metric_volume_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = uiState.trafficMetric == TrafficChartMetric.TRANSFER_COUNT,
                    onClick = { onMetricChange(TrafficChartMetric.TRANSFER_COUNT) },
                    label = { Text(stringResource(id = R.string.telemetry_metric_transfers)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("telemetry_metric_count_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            if (buckets.isNotEmpty()) {
                val safeIndex = selectedBucketIndex.coerceIn(0, buckets.lastIndex)
                val chartCd = stringResource(id = R.string.telemetry_traffic_chart_cd)

                TrafficGroupedBarCanvas(
                    buckets = buckets,
                    metric = uiState.trafficMetric,
                    outgoingColor = outgoingColor,
                    incomingColor = secondaryBarColor,
                    selectedIndex = safeIndex,
                    onSelectBucket = { selectedBucketIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .semantics { contentDescription = chartCd }
                        .testTag("telemetry_traffic_bar_chart")
                )

                // Legend + Payload Mix pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primaryLegend = if (uiState.trafficMetric == TrafficChartMetric.THROUGHPUT_KB) {
                        stringResource(id = R.string.telemetry_legend_outgoing)
                    } else {
                        stringResource(id = R.string.telemetry_legend_delivered)
                    }
                    val secondaryLegend = if (uiState.trafficMetric == TrafficChartMetric.THROUGHPUT_KB) {
                        stringResource(id = R.string.telemetry_legend_incoming)
                    } else {
                        stringResource(id = R.string.telemetry_legend_failed)
                    }
                    LegendDotChip(
                        color = outgoingColor,
                        label = primaryLegend
                    )
                    LegendDotChip(
                        color = secondaryBarColor,
                        label = secondaryLegend
                    )
                }

                val activeBucket = buckets[safeIndex]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telemetry_traffic_tooltip"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.telemetry_traffic_tooltip,
                                activeBucket.label,
                                TransferSizeLimits.formatByteSize(activeBucket.outgoingBytes),
                                TransferSizeLimits.formatByteSize(activeBucket.incomingBytes),
                                activeBucket.transferCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${stringResource(id = R.string.telemetry_mix_text, uiState.textPayloadCount)} • ${
                                stringResource(id = R.string.telemetry_mix_files, uiState.filePayloadCount)
                            } • ${stringResource(id = R.string.telemetry_mix_apks, uiState.apkPayloadCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficGroupedBarCanvas(
    buckets: List<TrafficBucketPoint>,
    metric: TrafficChartMetric,
    outgoingColor: Color,
    incomingColor: Color,
    selectedIndex: Int,
    onSelectBucket: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val selectionRing = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = axisColor,
        fontFamily = FontFamily.Monospace
    )

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "traffic_bar_reveal"
    )

    val maxRawValue = remember(buckets, metric) {
        when (metric) {
            TrafficChartMetric.THROUGHPUT_KB -> {
                val maxBytes = buckets.maxOfOrNull { max(it.outgoingBytes, it.incomingBytes) } ?: 0L
                val maxKb = maxBytes / 1024f
                max(4f, maxKb * 1.15f)
            }
            TrafficChartMetric.TRANSFER_COUNT -> {
                val maxCnt = buckets.maxOfOrNull { max(it.transferCount, 1) } ?: 1
                max(4f, maxCnt.toFloat() * 1.25f)
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(buckets) {
            detectTapGestures { offset ->
                val leftPad = 46.dp.toPx()
                val rightPad = 10.dp.toPx()
                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val slotW = plotW / buckets.size.coerceAtLeast(1)
                val tappedIdx = ((offset.x - leftPad) / slotW).toInt().coerceIn(0, buckets.lastIndex)
                onSelectBucket(tappedIdx)
            }
        }
    ) {
        val leftPad = 46.dp.toPx()
        val rightPad = 10.dp.toPx()
        val topPad = 14.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        val steps = 4
        for (i in 0..steps) {
            val ratio = i.toFloat() / steps
            val y = topPad + plotH * (1f - ratio)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (i == 0) null else dashEffect
            )
            val tickVal = (maxRawValue * ratio).roundToInt()
            val labelText = if (metric == TrafficChartMetric.THROUGHPUT_KB) "${tickVal}K" else "$tickVal"
            val measured = textMeasurer.measure(labelText, axisStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    y = y - measured.size.height / 2f
                )
            )
        }

        val slotW = plotW / buckets.size.coerceAtLeast(1)
        val barWidth = (slotW * 0.28f).coerceIn(6.dp.toPx(), 20.dp.toPx())
        val barGap = (slotW * 0.08f).coerceAtLeast(2.dp.toPx())
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        buckets.forEachIndexed { idx, bucket ->
            val centerX = leftPad + slotW * (idx + 0.5f)
            val outVal = when (metric) {
                TrafficChartMetric.THROUGHPUT_KB -> bucket.outgoingBytes / 1024f
                TrafficChartMetric.TRANSFER_COUNT -> bucket.transferCount.toFloat()
            }
            val inVal = when (metric) {
                TrafficChartMetric.THROUGHPUT_KB -> bucket.incomingBytes / 1024f
                TrafficChartMetric.TRANSFER_COUNT -> bucket.failedCount.toFloat()
            }

            val outH = ((outVal / maxRawValue) * plotH * animProgress)
                .let { if (outVal > 0f) max(it, 3.dp.toPx()) else 0f }
            val inH = ((inVal / maxRawValue) * plotH * animProgress)
                .let { if (inVal > 0f) max(it, 3.dp.toPx()) else 0f }

            val outLeft = centerX - barWidth - barGap / 2f
            val inLeft = centerX + barGap / 2f

            if (idx == selectedIndex) {
                drawRoundRect(
                    color = selectionRing.copy(alpha = 0.08f),
                    topLeft = Offset(leftPad + slotW * idx + 2.dp.toPx(), topPad),
                    size = Size((slotW - 4.dp.toPx()).coerceAtLeast(1f), plotH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }

            if (outH > 0f) {
                val outPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                left = outLeft,
                                top = topPad + plotH - outH,
                                right = outLeft + barWidth,
                                bottom = topPad + plotH
                            ),
                            topLeft = cornerRadius,
                            topRight = cornerRadius
                        )
                    )
                }
                drawPath(path = outPath, color = outgoingColor)
            }

            if (inH > 0f) {
                val inPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                left = inLeft,
                                top = topPad + plotH - inH,
                                right = inLeft + barWidth,
                                bottom = topPad + plotH
                            ),
                            topLeft = cornerRadius,
                            topRight = cornerRadius
                        )
                    )
                }
                drawPath(path = inPath, color = incomingColor)
            }

            if (idx == 0 || idx == buckets.lastIndex || idx % 2 == 0) {
                val measured = textMeasurer.measure(bucket.label, axisStyle)
                val labelX = (centerX - measured.size.width / 2f)
                    .coerceIn(leftPad, leftPad + plotW - measured.size.width)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(labelX, topPad + plotH + 6.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun LegendDotChip(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TransportStabilityBreakdownCard(
    items: List<TransportDistributionItem>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_transport_card"),
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
                        imageVector = Icons.Rounded.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.telemetry_transport_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.telemetry_transport_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items.forEach { item ->
                val modeTitle = when (item.transportMode) {
                    P2PTransportMode.WIFI_DIRECT -> stringResource(id = R.string.network_transport_wifi_direct)
                    P2PTransportMode.LOCAL_LAN -> stringResource(id = R.string.network_transport_local_mesh)
                    P2PTransportMode.STANDBY -> stringResource(id = R.string.network_transport_unavailable)
                }
                val barColor = when (item.transportMode) {
                    P2PTransportMode.WIFI_DIRECT -> MaterialTheme.colorScheme.primary
                    P2PTransportMode.LOCAL_LAN -> MaterialTheme.colorScheme.secondary
                    P2PTransportMode.STANDBY -> MaterialTheme.colorScheme.outline
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = modeTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${item.sharePercent}% • ${
                                stringResource(id = R.string.telemetry_samples_count, item.sampleCount)
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (item.sharePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
