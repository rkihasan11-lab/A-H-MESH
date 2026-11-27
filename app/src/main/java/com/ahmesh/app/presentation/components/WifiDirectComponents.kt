package com.ahmesh.app.presentation.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.LocalIsDarkTheme
import com.ahmesh.app.core.theme.StatusConnectedDark
import com.ahmesh.app.core.theme.StatusConnectedLight
import com.ahmesh.app.core.theme.StatusDiscoveringDark
import com.ahmesh.app.core.theme.StatusDiscoveringLight
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.TransferDirection
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.domain.model.TransferStatus
import com.ahmesh.app.domain.model.WifiDirectLinkInfo
import com.ahmesh.app.domain.model.WifiDirectPeer
import com.ahmesh.app.domain.model.WifiPeerConnectionStatus

@Composable
fun WifiDirectSessionCard(
    linkInfo: WifiDirectLinkInfo,
    onDisconnect: () -> Unit,
    onOpenTransferHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val accent = if (linkInfo.isGroupFormed) {
        if (isDark) StatusConnectedDark else StatusConnectedLight
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wifi_direct_session_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.wifi_direct_session_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (linkInfo.isGroupOwner) {
                                stringResource(id = R.string.wifi_direct_role_owner)
                            } else {
                                stringResource(id = R.string.wifi_direct_role_client)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (linkInfo.isSocketServerRunning) {
                            stringResource(id = R.string.wifi_direct_server_listening, linkInfo.serverPort)
                        } else {
                            stringResource(id = R.string.wifi_direct_server_standby)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    linkInfo.groupOwnerIp?.let { ownerIp ->
                        Text(
                            text = stringResource(id = R.string.wifi_direct_owner_ip, ownerIp),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val peerIpText = linkInfo.peerIp?.let { peerIp ->
                        stringResource(id = R.string.wifi_direct_peer_ip, peerIp)
                    } ?: stringResource(id = R.string.wifi_direct_peer_ip_pending)
                    Text(
                        text = peerIpText,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("wifi_direct_disconnect_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.wifi_direct_disconnect),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onOpenTransferHub,
                    modifier = Modifier
                        .weight(1.2f)
                        .minimumInteractiveComponentSize()
                        .testTag("wifi_direct_open_transfer_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.wifi_direct_open_transfer),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoveredWifiPeerCard(
    peer: WifiDirectPeer,
    onConnectClick: () -> Unit,
    onOpenTransferClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val statusColor = when (peer.status) {
        WifiPeerConnectionStatus.CONNECTED -> if (isDark) StatusConnectedDark else StatusConnectedLight
        WifiPeerConnectionStatus.INVITED -> if (isDark) StatusDiscoveringDark else StatusDiscoveringLight
        WifiPeerConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val statusLabel = when (peer.status) {
        WifiPeerConnectionStatus.AVAILABLE -> stringResource(id = R.string.wifi_direct_status_available)
        WifiPeerConnectionStatus.INVITED -> stringResource(id = R.string.wifi_direct_status_invited)
        WifiPeerConnectionStatus.CONNECTED -> stringResource(id = R.string.wifi_direct_status_connected)
        WifiPeerConnectionStatus.FAILED -> stringResource(id = R.string.wifi_direct_status_failed)
        WifiPeerConnectionStatus.UNAVAILABLE -> stringResource(id = R.string.wifi_direct_status_unavailable)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wifi_peer_card_${peer.deviceAddress}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (peer.status == WifiPeerConnectionStatus.CONNECTED) {
                        Icons.Rounded.WifiTethering
                    } else {
                        Icons.Rounded.Devices
                    },
                    contentDescription = peer.deviceName,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = peer.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${peer.deviceAddress} • $statusLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (peer.status) {
                WifiPeerConnectionStatus.CONNECTED -> {
                    FilledTonalButton(
                        onClick = onOpenTransferClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("wifi_peer_action_${peer.deviceAddress}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(id = R.string.wifi_direct_connected_chip))
                    }
                }
                WifiPeerConnectionStatus.INVITED -> {
                    OutlinedButton(
                        onClick = onConnectClick,
                        enabled = false,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("wifi_peer_action_${peer.deviceAddress}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(id = R.string.wifi_direct_connecting))
                    }
                }
                else -> {
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("wifi_peer_action_${peer.deviceAddress}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(id = R.string.wifi_direct_connect))
                    }
                }
            }
        }
    }
}

@Composable
fun TransferQuickActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("main_transfer_hub_button"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = stringResource(id = R.string.cd_open_transfer_hub),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.wifi_direct_transfer_cta_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.wifi_direct_transfer_cta_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TransferProgressRowCard(
    transfer: ActiveTransferState,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val statusColor = when (transfer.status) {
        TransferStatus.COMPLETED -> if (isDark) StatusConnectedDark else StatusConnectedLight
        TransferStatus.FAILED, TransferStatus.CANCELED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when (transfer.messageType) {
        MessageType.APK -> Icons.Rounded.Android
        else -> if (transfer.direction == TransferDirection.OUTGOING) {
            Icons.Rounded.FileUpload
        } else {
            Icons.Rounded.FolderZip
        }
    }

    val statusText = when (transfer.status) {
        TransferStatus.QUEUED -> stringResource(id = R.string.transfer_status_queued)
        TransferStatus.TRANSFERRING -> stringResource(
            id = R.string.transfer_status_transferring,
            transfer.progressPercent
        )
        TransferStatus.COMPLETED -> stringResource(
            id = R.string.transfer_status_completed,
            TransferSizeLimits.formatByteSize(transfer.totalBytes)
        )
        TransferStatus.FAILED, TransferStatus.CANCELED -> stringResource(
            id = R.string.transfer_status_failed,
            transfer.errorMessage ?: "Interrupted"
        )
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("transfer_progress_card_${transfer.transferId}"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
        ) {
            TransferProgressRowContent(transfer = transfer, icon = icon, statusColor = statusColor, statusText = statusText)
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("transfer_progress_card_${transfer.transferId}"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
        ) {
            TransferProgressRowContent(transfer = transfer, icon = icon, statusColor = statusColor, statusText = statusText)
        }
    }
}

@Composable
private fun TransferProgressRowContent(
    transfer: ActiveTransferState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    statusColor: androidx.compose.ui.graphics.Color,
    statusText: String
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${
                            if (transfer.direction == TransferDirection.OUTGOING) {
                                stringResource(id = R.string.transfer_direction_outgoing)
                            } else {
                                stringResource(id = R.string.transfer_direction_incoming)
                            }
                        } • $statusText",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (transfer.status) {
                    TransferStatus.COMPLETED -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    TransferStatus.FAILED, TransferStatus.CANCELED -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    else -> Text(
                        text = "${transfer.progressPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            if (transfer.status == TransferStatus.QUEUED || transfer.status == TransferStatus.TRANSFERRING) {
                LinearProgressIndicator(
                    progress = { transfer.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Text(
                    text = "${TransferSizeLimits.formatByteSize(transfer.bytesTransferred)} / ${
                        TransferSizeLimits.formatByteSize(transfer.totalBytes)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
