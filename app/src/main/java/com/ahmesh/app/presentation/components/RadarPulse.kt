package com.ahmesh.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.P2PTransportMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarSearchCard(
    isSearching: Boolean,
    transportMode: P2PTransportMode,
    discoveredPeersCount: Int,
    onStopScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transportLabel = when (transportMode) {
        P2PTransportMode.WIFI_DIRECT -> stringResource(id = R.string.network_transport_wifi_direct)
        P2PTransportMode.LOCAL_LAN -> stringResource(id = R.string.network_transport_local_mesh)
        P2PTransportMode.STANDBY -> stringResource(id = R.string.network_transport_unavailable)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("radar_search_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.55f),
                    secondaryColor.copy(alpha = 0.45f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.14f),
                            secondaryColor.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                            imageVector = Icons.Rounded.Radar,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(id = R.string.radar_searching_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.radar_searching_subtitle,
                                    transportLabel
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = primaryColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.radar_peers_detected,
                                discoveredPeersCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                RadarPulse(
                    isSearching = isSearching,
                    discoveredPeersCount = discoveredPeersCount,
                    modifier = Modifier.size(176.dp)
                )

                FilledTonalButton(
                    onClick = onStopScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("radar_stop_scan_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.radar_stop_scan),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun RadarPulse(
    isSearching: Boolean,
    discoveredPeersCount: Int = 0,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    gridColor: Color = MaterialTheme.colorScheme.outline,
    coreHighlightColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val activeAlpha by animateFloatAsState(
        targetValue = if (isSearching) 1f else 0.35f,
        animationSpec = tween(durationMillis = 300),
        label = "radar_active_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse_transition")
    val primaryPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse_ring_1"
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep_angle"
    )

    val radarCd = stringResource(id = R.string.cd_radar_pulse)

    Canvas(
        modifier = modifier
            .size(160.dp)
            .testTag("radar_pulse_canvas")
            .semantics { contentDescription = radarCd }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (size.minDimension / 2f) * 0.94f
        val coreRadius = maxRadius * 0.13f

        // Outer ambient radial field
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.16f * activeAlpha),
                    secondaryColor.copy(alpha = 0.06f * activeAlpha),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // Concentric radar range rings
        val gridStroke = 1.dp.toPx()
        listOf(0.35f, 0.66f, 0.98f).forEach { fraction ->
            drawCircle(
                color = gridColor.copy(alpha = 0.32f),
                radius = maxRadius * fraction,
                center = center,
                style = Stroke(width = gridStroke)
            )
        }

        // Crosshair axes
        drawLine(
            color = gridColor.copy(alpha = 0.24f),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = gridStroke
        )
        drawLine(
            color = gridColor.copy(alpha = 0.24f),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = gridStroke
        )

        if (isSearching) {
            // Rotating radar sweep sector
            val arcTopLeft = Offset(center.x - maxRadius, center.y - maxRadius)
            val arcSize = Size(maxRadius * 2f, maxRadius * 2f)
            rotate(degrees = sweepAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        0.90f to secondaryColor.copy(alpha = 0.16f),
                        1.0f to primaryColor.copy(alpha = 0.42f),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize
                )

                // Leading sweep needle
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.2f)
                        ),
                        start = center,
                        end = Offset(center.x + maxRadius, center.y)
                    ),
                    start = center,
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Three staggered expanding pulse rings
            val ringStroke = 2.2.dp.toPx()
            listOf(0f, 0.33f, 0.66f).forEachIndexed { index, phaseOffset ->
                val progress = (primaryPulse + phaseOffset) % 1f
                val ringRadius = coreRadius + (maxRadius - coreRadius) * progress
                val ringAlpha = (1f - progress) * (1f - progress) * 0.72f
                val ringColor = if (index % 2 == 0) primaryColor else secondaryColor
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = ringStroke)
                )
            }

            // Highlight blips for detected nearby peers
            val blipCount = discoveredPeersCount.coerceIn(0, 6)
            for (i in 0 until blipCount) {
                val angleRad = Math.toRadians((i * 67.0) + 28.0)
                val distFraction = 0.44f + ((i % 3) * 0.17f)
                val bx = center.x + (cos(angleRad) * maxRadius * distFraction).toFloat()
                val by = center.y + (sin(angleRad) * maxRadius * distFraction).toFloat()
                val blipPulse = ((primaryPulse + i * 0.21f) % 1f)
                drawCircle(
                    color = secondaryColor.copy(alpha = (1f - blipPulse) * 0.55f),
                    radius = 5.dp.toPx() + 5.dp.toPx() * blipPulse,
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = secondaryColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(bx, by)
                )
            }
        }

        // Center emitter beacon
        drawCircle(
            color = primaryColor.copy(alpha = 0.25f * activeAlpha),
            radius = coreRadius * 1.7f,
            center = center
        )
        drawCircle(
            color = primaryColor,
            radius = coreRadius,
            center = center
        )
        drawCircle(
            color = coreHighlightColor.copy(alpha = 0.85f),
            radius = coreRadius * 0.38f,
            center = center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RadarSearchCardPreview() {
    AHMeshTheme {
        RadarSearchCard(
            isSearching = true,
            transportMode = P2PTransportMode.WIFI_DIRECT,
            discoveredPeersCount = 2,
            onStopScan = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RadarPulseIdlePreview() {
    AHMeshTheme {
        Box(modifier = Modifier.padding(24.dp)) {
            RadarPulse(isSearching = false)
        }
    }
}
