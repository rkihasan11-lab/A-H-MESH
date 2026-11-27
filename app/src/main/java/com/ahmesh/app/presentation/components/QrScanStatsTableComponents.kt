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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.QrScanStatistics

@Composable
fun QrScanStatsTableSection(
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_qr_stats_table_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.pairing_qr_stats_table_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_qr_stats_table_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = stringResource(id = R.string.pairing_qr_rate_total, stats.totalScans),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Recharts-style Progress Indicator Section (Radial Ring + Proportional Bar)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .testTag("pairing_qr_stats_radial_ring"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokePx = 6.dp.toPx()
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(trackColor),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (animatedSuccessRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animatedSuccessRatio.coerceAtLeast(0.01f))
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(50))
                                        .background(successColor)
                                )
                            }
                            if (animatedFailureRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animatedFailureRatio.coerceAtLeast(0.01f))
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(50))
                                        .background(failureColor)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(successColor)
                                )
                                Text(
                                    text = "${stats.successCount} passed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(failureColor)
                                )
                                Text(
                                    text = "${stats.failureCount} failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Structured Statistics Table
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing_qr_stats_table"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.pairing_qr_stats_table_metric),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_qr_stats_table_count),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_qr_stats_table_rate),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    QrScanStatTableRow(
                        label = stringResource(id = R.string.pairing_qr_stats_row_total),
                        count = stats.totalScans.toString(),
                        rate = "100%",
                        isSuccess = true,
                        isTotal = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    QrScanStatTableRow(
                        label = stringResource(id = R.string.pairing_qr_stats_row_successful),
                        count = stats.successCount.toString(),
                        rate = "${stats.successRatePercent}%",
                        isSuccess = true,
                        isTotal = false
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    val failureRatePercent = if (stats.totalScans > 0) {
                        100 - stats.successRatePercent
                    } else {
                        0
                    }
                    QrScanStatTableRow(
                        label = stringResource(id = R.string.pairing_qr_stats_row_failed),
                        count = stats.failureCount.toString(),
                        rate = "$failureRatePercent%",
                        isSuccess = false,
                        isTotal = false
                    )
                }
            }
        }
    }
}

@Composable
private fun QrScanStatTableRow(
    label: String,
    count: String,
    rate: String,
    isSuccess: Boolean,
    isTotal: Boolean
) {
    val pillColor = if (isTotal) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else if (isSuccess) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
    }

    val pillTint = if (isTotal) {
        MaterialTheme.colorScheme.onSurface
    } else if (isSuccess) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1.2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isTotal) {
                Icon(
                    imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = pillTint,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = pillTint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier.weight(0.7f),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = pillColor
            ) {
                Text(
                    text = rate,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = pillTint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QrScanStatsTableSectionPreview() {
    AHMeshTheme {
        QrScanStatsTableSection(
            stats = QrScanStatistics(
                totalScans = 12,
                successCount = 9,
                failureCount = 3
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
