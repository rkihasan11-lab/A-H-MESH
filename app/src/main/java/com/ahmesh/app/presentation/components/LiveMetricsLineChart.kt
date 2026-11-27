package com.ahmesh.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.LocalIsDarkTheme
import com.ahmesh.app.core.theme.StatusConnectedDark
import com.ahmesh.app.core.theme.StatusConnectedLight
import com.ahmesh.app.core.theme.StatusDiscoveringDark
import com.ahmesh.app.core.theme.StatusDiscoveringLight
import com.ahmesh.app.domain.model.LiveMetricSample
import com.ahmesh.app.domain.model.LiveSeriesVisibility
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun LiveMetricsLineChartCard(
    samples: List<LiveMetricSample>,
    visibility: LiveSeriesVisibility,
    isStreaming: Boolean,
    onToggleStability: () -> Unit,
    onToggleOutgoing: () -> Unit,
    onToggleIncoming: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val stabilityColor = MaterialTheme.colorScheme.primary
    val outgoingColor = if (isDark) StatusConnectedDark else StatusConnectedLight
    val incomingColor = if (isDark) StatusDiscoveringDark else StatusDiscoveringLight

    var pinnedByTouch by remember { mutableStateOf(false) }
    var selectedIndex by remember(samples.size, pinnedByTouch) {
        mutableIntStateOf((samples.lastIndex).coerceAtLeast(0))
    }

    val livePulse = rememberInfiniteTransition(label = "live_beacon_ring").animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "live_beacon_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_metrics_line_chart_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timeline,
                        contentDescription = null,
                        tint = stabilityColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.live_metrics_chart_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.live_metrics_chart_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Series Toggle Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = visibility.showStability,
                    onClick = onToggleStability,
                    leadingIcon = { SeriesDot(color = stabilityColor) },
                    label = { Text(stringResource(id = R.string.live_series_stability)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_series_stability_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = visibility.showOutgoingTraffic,
                    onClick = onToggleOutgoing,
                    leadingIcon = { SeriesDot(color = outgoingColor) },
                    label = { Text(stringResource(id = R.string.live_series_outgoing)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_series_outgoing_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                FilterChip(
                    selected = visibility.showIncomingTraffic,
                    onClick = onToggleIncoming,
                    leadingIcon = { SeriesDot(color = incomingColor) },
                    label = { Text(stringResource(id = R.string.live_series_incoming)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_series_incoming_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }

            if (samples.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_metrics_chart_empty"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(id = R.string.live_metrics_empty_stream),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val activeIdx = if (pinnedByTouch) {
                    selectedIndex.coerceIn(0, samples.lastIndex)
                } else {
                    samples.lastIndex
                }

                val maxTrafficKb = remember(samples) {
                    val peak = samples.maxOfOrNull { max(it.outgoingKbPerTick, it.incomingKbPerTick) } ?: 0f
                    computeNiceTrafficCeiling(peak)
                }

                val textMeasurer = rememberTextMeasurer()
                val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                val spineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                val leftAxisStyle = MaterialTheme.typography.labelSmall.copy(
                    color = stabilityColor,
                    fontFamily = FontFamily.Monospace
                )
                val rightAxisStyle = MaterialTheme.typography.labelSmall.copy(
                    color = outgoingColor,
                    fontFamily = FontFamily.Monospace
                )
                val xAxisStyle = MaterialTheme.typography.labelSmall.copy(
                    color = axisColor,
                    fontFamily = FontFamily.Monospace
                )
                val chartCd = stringResource(id = R.string.live_metrics_chart_cd)

                fun resolveIndex(xPx: Float, widthPx: Float, leftPadPx: Float, rightPadPx: Float): Int {
                    if (samples.size <= 1) return 0
                    val plotW = (widthPx - leftPadPx - rightPadPx).coerceAtLeast(1f)
                    val ratio = ((xPx - leftPadPx) / plotW).coerceIn(0f, 1f)
                    return (ratio * (samples.size - 1)).roundToInt().coerceIn(0, samples.lastIndex)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .semantics { contentDescription = chartCd }
                        .testTag("live_metrics_line_canvas")
                        .pointerInput(samples) {
                            detectTapGestures { offset ->
                                pinnedByTouch = true
                                selectedIndex = resolveIndex(
                                    xPx = offset.x,
                                    widthPx = size.width.toFloat(),
                                    leftPadPx = 40.dp.toPx(),
                                    rightPadPx = 44.dp.toPx()
                                )
                            }
                        }
                        .pointerInput(samples) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    pinnedByTouch = true
                                    selectedIndex = resolveIndex(
                                        xPx = offset.x,
                                        widthPx = size.width.toFloat(),
                                        leftPadPx = 40.dp.toPx(),
                                        rightPadPx = 44.dp.toPx()
                                    )
                                },
                                onHorizontalDrag = { change, _ ->
                                    selectedIndex = resolveIndex(
                                        xPx = change.position.x,
                                        widthPx = size.width.toFloat(),
                                        leftPadPx = 40.dp.toPx(),
                                        rightPadPx = 44.dp.toPx()
                                    )
                                }
                            )
                        }
                ) {
                    val leftPad = 40.dp.toPx()
                    val rightPad = 44.dp.toPx()
                    val topPad = 16.dp.toPx()
                    val bottomPad = 30.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    val dashGrid = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f)
                    val dashIncoming = PathEffect.dashPathEffect(floatArrayOf(12f, 7f), 0f)

                    // D3-style horizontal gridlines + dual Y-axis ticks (Stability % left, Traffic KB right)
                    val gridSteps = 4
                    for (step in 0..gridSteps) {
                        val ratio = step.toFloat() / gridSteps
                        val y = topPad + plotH * (1f - ratio)
                        drawLine(
                            color = if (step == 0) spineColor else gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + plotW, y),
                            strokeWidth = if (step == 0) 1.2.dp.toPx() else 0.9.dp.toPx(),
                            pathEffect = if (step == 0) null else dashGrid
                        )

                        val stabLabel = "${(ratio * 100f).roundToInt()}%"
                        val leftLayout = textMeasurer.measure(stabLabel, leftAxisStyle)
                        drawText(
                            textLayoutResult = leftLayout,
                            topLeft = Offset(
                                x = (leftPad - leftLayout.size.width - 5.dp.toPx()).coerceAtLeast(0f),
                                y = y - leftLayout.size.height / 2f
                            )
                        )

                        val kbValue = maxTrafficKb * ratio
                        val kbLabel = if (kbValue >= 10f) "${kbValue.roundToInt()}K" else String.format(Locale.US, "%.1fK", kbValue)
                        val rightLayout = textMeasurer.measure(kbLabel, rightAxisStyle)
                        drawText(
                            textLayoutResult = rightLayout,
                            topLeft = Offset(
                                x = leftPad + plotW + 5.dp.toPx(),
                                y = y - rightLayout.size.height / 2f
                            )
                        )
                    }

                    // Left & Right D3 axis spines
                    drawLine(
                        color = spineColor,
                        start = Offset(leftPad, topPad),
                        end = Offset(leftPad, topPad + plotH),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = spineColor,
                        start = Offset(leftPad + plotW, topPad),
                        end = Offset(leftPad + plotW, topPad + plotH),
                        strokeWidth = 1.dp.toPx()
                    )

                    fun xFor(idx: Int): Float {
                        return if (samples.size == 1) {
                            leftPad + plotW
                        } else {
                            leftPad + (idx.toFloat() / (samples.size - 1)) * plotW
                        }
                    }

                    // Series 1: Stability % (Left Axis)
                    val stabilityCoords = samples.mapIndexed { idx, s ->
                        val norm = (s.stabilityPercent / 100f).coerceIn(0f, 1f)
                        Offset(xFor(idx), topPad + plotH * (1f - norm))
                    }

                    // Series 2 & 3: Outgoing & Incoming Traffic KB (Right Axis)
                    val outgoingCoords = samples.mapIndexed { idx, s ->
                        val norm = (s.outgoingKbPerTick / maxTrafficKb).coerceIn(0f, 1f)
                        Offset(xFor(idx), topPad + plotH * (1f - norm))
                    }
                    val incomingCoords = samples.mapIndexed { idx, s ->
                        val norm = (s.incomingKbPerTick / maxTrafficKb).coerceIn(0f, 1f)
                        Offset(xFor(idx), topPad + plotH * (1f - norm))
                    }

                    if (visibility.showStability && stabilityCoords.isNotEmpty()) {
                        if (stabilityCoords.size >= 2) {
                            val linePath = buildSmoothD3Path(stabilityCoords, topPad, topPad + plotH)
                            val areaPath = Path().apply {
                                addPath(linePath)
                                lineTo(stabilityCoords.last().x, topPad + plotH)
                                lineTo(stabilityCoords.first().x, topPad + plotH)
                                close()
                            }
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        stabilityColor.copy(alpha = 0.28f),
                                        stabilityColor.copy(alpha = 0.02f)
                                    ),
                                    startY = topPad,
                                    endY = topPad + plotH
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = stabilityColor,
                                style = Stroke(
                                    width = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (visibility.showOutgoingTraffic && outgoingCoords.isNotEmpty()) {
                        if (outgoingCoords.size >= 2) {
                            val outPath = buildSmoothD3Path(outgoingCoords, topPad, topPad + plotH)
                            val outArea = Path().apply {
                                addPath(outPath)
                                lineTo(outgoingCoords.last().x, topPad + plotH)
                                lineTo(outgoingCoords.first().x, topPad + plotH)
                                close()
                            }
                            drawPath(
                                path = outArea,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        outgoingColor.copy(alpha = 0.18f),
                                        outgoingColor.copy(alpha = 0.01f)
                                    ),
                                    startY = topPad,
                                    endY = topPad + plotH
                                )
                            )
                            drawPath(
                                path = outPath,
                                color = outgoingColor,
                                style = Stroke(
                                    width = 2.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (visibility.showIncomingTraffic && incomingCoords.isNotEmpty()) {
                        if (incomingCoords.size >= 2) {
                            val inPath = buildSmoothD3Path(incomingCoords, topPad, topPad + plotH)
                            drawPath(
                                path = inPath,
                                color = incomingColor,
                                style = Stroke(
                                    width = 2.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = dashIncoming
                                )
                            )
                        }
                    }

                    // Active D3 Crosshair line
                    val cursorX = stabilityCoords[activeIdx].x
                    drawLine(
                        color = crosshairColor,
                        start = Offset(cursorX, topPad),
                        end = Offset(cursorX, topPad + plotH),
                        strokeWidth = 1.1.dp.toPx(),
                        pathEffect = dashGrid
                    )

                    // Point highlight rings
                    if (visibility.showStability) {
                        val pt = stabilityCoords[activeIdx]
                        if (isStreaming && activeIdx == samples.lastIndex) {
                            drawCircle(
                                color = stabilityColor.copy(alpha = (1f - livePulse.value) * 0.45f),
                                radius = (6f + livePulse.value * 9f).dp.toPx(),
                                center = pt
                            )
                        }
                        drawCircle(color = stabilityColor, radius = 4.8.dp.toPx(), center = pt)
                    }
                    if (visibility.showOutgoingTraffic) {
                        val pt = outgoingCoords[activeIdx]
                        drawCircle(color = outgoingColor, radius = 4.2.dp.toPx(), center = pt)
                    }
                    if (visibility.showIncomingTraffic) {
                        val pt = incomingCoords[activeIdx]
                        drawCircle(color = incomingColor, radius = 4.2.dp.toPx(), center = pt)
                    }

                    // X-axis timestamps (first, mid, last)
                    val tickIndices = linkedSetOf(0, samples.lastIndex / 2, samples.lastIndex)
                    tickIndices.forEach { idx ->
                        val layout = textMeasurer.measure(samples[idx].timeLabel, xAxisStyle)
                        val xPos = (xFor(idx) - layout.size.width / 2f)
                            .coerceIn(leftPad, (leftPad + plotW - layout.size.width).coerceAtLeast(leftPad))
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(xPos, topPad + plotH + 7.dp.toPx())
                        )
                    }
                }

                val inspected = samples[activeIdx]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_metrics_hud_tooltip"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${inspected.timeLabel} • ${inspected.connectionState.name} (${inspected.transportMode.name})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (pinnedByTouch) {
                                Text(
                                    text = stringResource(id = R.string.live_metrics_resume_live_tail),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { pinnedByTouch = false }
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("live_metrics_resume_tail_pill")
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                id = R.string.live_metrics_hud_detail,
                                inspected.stabilityPercent,
                                formatKb(inspected.outgoingKbPerTick),
                                formatKb(inspected.incomingKbPerTick),
                                inspected.connectedPeers
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

@Composable
private fun SeriesDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun buildSmoothD3Path(
    coords: List<Offset>,
    minY: Float,
    maxY: Float
): Path {
    val path = Path()
    if (coords.isEmpty()) return path
    path.moveTo(coords.first().x, coords.first().y)
    for (i in 0 until coords.lastIndex) {
        val p0 = coords[i]
        val p1 = coords[i + 1]
        val midX = (p0.x + p1.x) / 2f
        val cy1 = p0.y.coerceIn(minY, maxY)
        val cy2 = p1.y.coerceIn(minY, maxY)
        path.cubicTo(midX, cy1, midX, cy2, p1.x, p1.y.coerceIn(minY, maxY))
    }
    return path
}

private fun computeNiceTrafficCeiling(peakKb: Float): Float {
    val minCeiling = 4f
    val raw = max(minCeiling, peakKb * 1.25f)
    return when {
        raw <= 8f -> 8f
        raw <= 20f -> 20f
        raw <= 64f -> 64f
        raw <= 256f -> 256f
        raw <= 1024f -> 1024f
        else -> ((raw / 512f).roundToInt().coerceAtLeast(3) * 512f)
    }
}

private fun formatKb(value: Float): String {
    return if (value >= 100f) {
        "${value.roundToInt()} KB"
    } else {
        String.format(Locale.US, "%.1f KB", value)
    }
}
