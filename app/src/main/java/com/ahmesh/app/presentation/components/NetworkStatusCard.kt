package com.ahmesh.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ahmesh.app.core.theme.LocalIsDarkTheme
import com.ahmesh.app.core.theme.StatusConnectedDark
import com.ahmesh.app.core.theme.StatusConnectedLight
import com.ahmesh.app.core.theme.StatusDiscoveringDark
import com.ahmesh.app.core.theme.StatusDiscoveringLight
import com.ahmesh.app.core.theme.StatusWarningDark
import com.ahmesh.app.core.theme.StatusWarningLight
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PTransportMode

@Composable
fun NetworkStatusCard(
    status: P2PConnectionStatus,
    savedPeerCount: Int,
    onPrimaryAction: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTelemetry: (() -> Unit)? = null,
    onOpenLiveMetrics: (() -> Unit)? = null
) {
    val isDark = LocalIsDarkTheme.current
    val visualSpec = status.state.toVisualSpec(isDark)
    val animatedAccent by animateColorAsState(
        targetValue = visualSpec.accentColor,
        animationSpec = tween(durationMillis = 280),
        label = "status_accent"
    )

    val statusTitle = stringResource(id = visualSpec.titleResId)
    val statusDescription = when (status.state) {
        P2PConnectionState.CONNECTED -> stringResource(
            id = R.string.network_desc_connected,
            maxOf(status.connectedPeersCount, 1)
        )
        P2PConnectionState.DISCOVERING -> stringResource(id = R.string.network_desc_discovering)
        P2PConnectionState.READY -> stringResource(id = R.string.network_desc_ready)
        P2PConnectionState.PERMISSION_REQUIRED -> stringResource(id = R.string.network_desc_permission)
        P2PConnectionState.OFFLINE -> stringResource(id = R.string.network_desc_offline)
    }

    val transportLabel = when (status.transportMode) {
        P2PTransportMode.WIFI_DIRECT -> stringResource(id = R.string.network_transport_wifi_direct)
        P2PTransportMode.LOCAL_LAN -> stringResource(id = R.string.network_transport_local_mesh)
        P2PTransportMode.STANDBY -> stringResource(id = R.string.network_transport_unavailable)
    }

    val wifiLabel = if (status.isWifiEnabled) {
        stringResource(id = R.string.network_wifi_on)
    } else {
        stringResource(id = R.string.network_wifi_off)
    }

    val displayedPeers = maxOf(status.connectedPeersCount, savedPeerCount)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_status_card"),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = animatedAccent.copy(alpha = 0.36f)
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        P2PVisualStatusIndicator(
                            state = status.state,
                            accentColor = animatedAccent,
                            statusLabel = statusTitle
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(id = R.string.network_card_header),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                modifier = Modifier.testTag("network_status_badge"),
                                shape = RoundedCornerShape(50),
                                color = animatedAccent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, animatedAccent.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = visualSpec.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = animatedAccent
                                    )
                                    Text(
                                        text = statusTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = animatedAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("network_status_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.network_cd_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = statusDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricColumn(
                            label = stringResource(id = R.string.network_metric_peers),
                            value = displayedPeers.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(
                            modifier = Modifier.height(28.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        MetricColumn(
                            label = stringResource(id = R.string.network_metric_transport),
                            value = transportLabel,
                            modifier = Modifier.weight(1.3f)
                        )
                        VerticalDivider(
                            modifier = Modifier.height(28.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        MetricColumn(
                            label = stringResource(id = R.string.network_metric_wifi),
                            value = wifiLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val actionText = when (status.state) {
                    P2PConnectionState.PERMISSION_REQUIRED ->
                        stringResource(id = R.string.network_action_grant_permission)
                    P2PConnectionState.DISCOVERING ->
                        stringResource(id = R.string.network_action_stop_scan)
                    P2PConnectionState.OFFLINE ->
                        stringResource(id = R.string.network_action_refresh)
                    P2PConnectionState.READY, P2PConnectionState.CONNECTED ->
                        stringResource(id = R.string.network_action_start_scan)
                }

                val actionIcon = when (status.state) {
                    P2PConnectionState.PERMISSION_REQUIRED -> Icons.Rounded.LockOpen
                    P2PConnectionState.DISCOVERING -> Icons.Rounded.Radar
                    P2PConnectionState.OFFLINE -> Icons.Rounded.Refresh
                    P2PConnectionState.READY, P2PConnectionState.CONNECTED -> Icons.Rounded.WifiTethering
                }

                if (status.state == P2PConnectionState.DISCOVERING || status.state == P2PConnectionState.OFFLINE) {
                    FilledTonalButton(
                        onClick = onPrimaryAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("network_status_action_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Button(
                        onClick = onPrimaryAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("network_status_action_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (onOpenTelemetry != null || onOpenLiveMetrics != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onOpenTelemetry != null) {
                            OutlinedButton(
                                onClick = onOpenTelemetry,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("network_telemetry_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ShowChart,
                                    contentDescription = stringResource(id = R.string.network_telemetry_cd_open),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.network_telemetry_open_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (onOpenLiveMetrics != null) {
                            OutlinedButton(
                                onClick = onOpenLiveMetrics,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("network_live_metrics_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timeline,
                                    contentDescription = stringResource(id = R.string.live_metrics_open_cd),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.live_metrics_open_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun P2PVisualStatusIndicator(
    state: P2PConnectionState,
    accentColor: Color,
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    val shouldAnimate = state == P2PConnectionState.DISCOVERING || state == P2PConnectionState.CONNECTED
    val infiniteTransition = rememberInfiniteTransition(label = "p2p_beacon_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == P2PConnectionState.DISCOVERING) 1400 else 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    val cdText = stringResource(id = R.string.network_cd_indicator, statusLabel)

    Canvas(
        modifier = modifier
            .size(44.dp)
            .testTag("network_status_indicator")
            .semantics { contentDescription = cdText }
    ) {
        val maxRadius = size.minDimension / 2f
        val coreRadius = maxRadius * 0.34f

        // Static ambient halo
        drawCircle(
            color = accentColor.copy(alpha = 0.14f),
            radius = maxRadius * 0.78f
        )

        if (shouldAnimate) {
            val expandingRadius = coreRadius + (maxRadius - coreRadius) * pulseProgress
            val ringAlpha = (1f - pulseProgress) * 0.55f
            drawCircle(
                color = accentColor.copy(alpha = ringAlpha),
                radius = expandingRadius,
                style = Stroke(width = 2.dp.toPx())
            )
        } else {
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = maxRadius * 0.62f,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        if (state == P2PConnectionState.OFFLINE) {
            drawCircle(
                color = accentColor,
                radius = coreRadius,
                style = Stroke(width = 2.5.dp.toPx())
            )
        } else {
            drawCircle(
                color = accentColor,
                radius = coreRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = coreRadius * 0.36f
            )
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class StatusVisualSpec(
    val titleResId: Int,
    val accentColor: Color,
    val icon: ImageVector
)

@Composable
private fun P2PConnectionState.toVisualSpec(isDark: Boolean): StatusVisualSpec {
    return when (this) {
        P2PConnectionState.CONNECTED -> StatusVisualSpec(
            titleResId = R.string.network_status_connected,
            accentColor = if (isDark) StatusConnectedDark else StatusConnectedLight,
            icon = Icons.Rounded.Hub
        )
        P2PConnectionState.DISCOVERING -> StatusVisualSpec(
            titleResId = R.string.network_status_discovering,
            accentColor = if (isDark) StatusDiscoveringDark else StatusDiscoveringLight,
            icon = Icons.Rounded.Radar
        )
        P2PConnectionState.READY -> StatusVisualSpec(
            titleResId = R.string.network_status_ready,
            accentColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Rounded.Sensors
        )
        P2PConnectionState.PERMISSION_REQUIRED -> StatusVisualSpec(
            titleResId = R.string.network_status_permission,
            accentColor = if (isDark) StatusWarningDark else StatusWarningLight,
            icon = Icons.Rounded.LockOpen
        )
        P2PConnectionState.OFFLINE -> StatusVisualSpec(
            titleResId = R.string.network_status_offline,
            accentColor = MaterialTheme.colorScheme.error,
            icon = Icons.Rounded.WifiOff
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkStatusCardReadyPreview() {
    AHMeshTheme {
        NetworkStatusCard(
            status = P2PConnectionStatus(
                state = P2PConnectionState.READY,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                isWifiEnabled = true,
                isWifiDirectSupported = true,
                hasNearbyPermission = true
            ),
            savedPeerCount = 0,
            onPrimaryAction = {},
            onRefresh = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkStatusCardDiscoveringPreview() {
    AHMeshTheme {
        NetworkStatusCard(
            status = P2PConnectionStatus(
                state = P2PConnectionState.DISCOVERING,
                transportMode = P2PTransportMode.WIFI_DIRECT,
                isWifiEnabled = true,
                isWifiDirectSupported = true,
                hasNearbyPermission = true,
                isDiscovering = true,
                discoveredPeersCount = 2
            ),
            savedPeerCount = 2,
            onPrimaryAction = {},
            onRefresh = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
