package com.ahmesh.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.domain.model.QrScanStatistics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrScanSuccessRateWidget(
    stats: QrScanStatistics,
    modifier: Modifier = Modifier
) {
    val animatedSuccessRatio by animateFloatAsState(
        targetValue = stats.successRatio,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "qr_scan_success_ratio"
    )
    val animatedFailureRatio by animateFloatAsState(
        targetValue = stats.failureRatio,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "qr_scan_failure_ratio"
    )

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val successColor = MaterialTheme.colorScheme.primary
    val failureColor = MaterialTheme.colorScheme.error
    val accessibilitySummary = stringResource(
        id = R.string.pairing_qr_rate_cd,
        stats.successRatePercent,
        stats.successCount,
        stats.failureCount
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilitySummary
            }
            .testTag("pairing_qr_scan_rate_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .testTag("pairing_qr_scan_rate_ring"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 7.dp.toPx()
                    val diameter = minOf(size.width, size.height) - strokePx
                    val topLeft = Offset(
                        x = (size.width - diameter) / 2f,
                        y = (size.height - diameter) / 2f
                    )
                    val arcSize = Size(diameter, diameter)
                    val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round)

                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )

                    if (stats.totalScans > 0) {
                        val hasBothSegments = animatedSuccessRatio > 0.01f && animatedFailureRatio > 0.01f
                        val gapDegrees = if (hasBothSegments) 10f else 0f
                        val halfGap = gapDegrees / 2f

                        if (animatedSuccessRatio > 0f) {
                            val successSweep = (animatedSuccessRatio * 360f - gapDegrees).coerceAtLeast(2f)
                            drawArc(
                                color = successColor,
                                startAngle = -90f + halfGap,
                                sweepAngle = successSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = strokeStyle
                            )
                        }

                        if (animatedFailureRatio > 0f) {
                            val startAngle = -90f + animatedSuccessRatio * 360f + halfGap
                            val failureSweep = (animatedFailureRatio * 360f - gapDegrees).coerceAtLeast(2f)
                            drawArc(
                                color = failureColor,
                                startAngle = startAngle,
                                sweepAngle = failureSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = strokeStyle
                            )
                        }
                    }
                }

                Text(
                    text = if (stats.totalScans > 0) "${stats.successRatePercent}%" else "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("pairing_qr_scan_rate_percentage")
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.pairing_qr_rate_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (stats.totalScans == 0) {
                    Text(
                        text = stringResource(id = R.string.pairing_qr_rate_no_scans),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(trackColor),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (animatedSuccessRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(animatedSuccessRatio.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(successColor)
                            )
                        }
                        if (animatedFailureRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(animatedFailureRatio.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(failureColor)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ScanRateLegendDot(
                            color = successColor,
                            label = stringResource(id = R.string.pairing_qr_rate_passed, stats.successCount)
                        )
                        ScanRateLegendDot(
                            color = failureColor,
                            label = stringResource(id = R.string.pairing_qr_rate_failed, stats.failureCount)
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_qr_rate_total, stats.totalScans),
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
private fun ScanRateLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
