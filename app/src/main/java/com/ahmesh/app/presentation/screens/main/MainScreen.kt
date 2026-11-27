package com.ahmesh.app.presentation.screens.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
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
import com.ahmesh.app.core.localization.AppLanguage
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.presentation.auth.AuthUiState
import com.ahmesh.app.presentation.auth.AuthViewModel
import com.ahmesh.app.presentation.components.BiometricSetupModal
import com.ahmesh.app.presentation.components.BrandBannerCard
import com.ahmesh.app.presentation.components.DiscoveredWifiPeerCard
import com.ahmesh.app.presentation.components.NetworkStatusCard
import com.ahmesh.app.presentation.components.RadarSearchCard
import com.ahmesh.app.presentation.components.SettingsSheet
import com.ahmesh.app.presentation.components.TransferProgressRowCard
import com.ahmesh.app.presentation.components.TransferQuickActionCard
import com.ahmesh.app.presentation.components.WifiDirectSessionCard
import com.ahmesh.app.security.BiometricAuthManager

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    biometricAuthManager: BiometricAuthManager,
    onNavigateToPairing: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToBiometricStats: () -> Unit = {},
    onNavigateToTransfer: (peerId: String?, peerName: String?, host: String?) -> Unit = { _, _, _ -> },
    onNavigateToNetworkTelemetry: () -> Unit = {},
    onNavigateToLiveMetrics: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    var showSecuritySheet by rememberSaveable { mutableStateOf(false) }
    var showBiometricSetupModal by rememberSaveable { mutableStateOf(false) }

    val nearbyPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshNetworkStatus()
        if (results.values.any { it }) {
            viewModel.toggleDiscovery()
        }
    }

    val isProtectionActive = authState.settings.isAppLockEnabled &&
        (authState.settings.hasConfiguredPin ||
            authState.biometricCapability == com.ahmesh.app.domain.model.BiometricCapability.AVAILABLE ||
            authState.canUseDeviceCredential)

    MainScreenContent(
        uiState = uiState,
        isAppLockEnabled = isProtectionActive,
        onOpenSecuritySettings = {
            authViewModel.refreshCapabilitiesAndMaybePrompt()
            showSecuritySheet = true
        },
        onNetworkActionClick = {
            val status = uiState.networkStatus
            when {
                status.state == P2PConnectionState.PERMISSION_REQUIRED ||
                    (!status.hasNearbyPermission && status.isWifiDirectSupported) -> {
                    permissionLauncher.launch(nearbyPermissions)
                }
                status.state == P2PConnectionState.OFFLINE -> {
                    viewModel.refreshNetworkStatus()
                }
                else -> {
                    viewModel.toggleDiscovery()
                }
            }
        },
        onRefreshNetwork = viewModel::refreshNetworkStatus,
        onStopDiscovery = viewModel::stopDiscovery,
        onToggleLanguage = viewModel::toggleLanguage,
        onOpenPairing = onNavigateToPairing,
        onOpenAssistant = onNavigateToAssistant,
        onOpenTransfer = { peerId, peerName, host ->
            onNavigateToTransfer(peerId, peerName, host)
        },
        onConnectWifiPeer = viewModel::connectToWifiPeer,
        onDisconnectWifiDirect = viewModel::disconnectWifiDirect,
        onDismissConnectionNotice = viewModel::dismissConnectionNotice,
        onOpenNetworkTelemetry = onNavigateToNetworkTelemetry,
        onOpenLiveMetrics = onNavigateToLiveMetrics,
        modifier = modifier
    )

    if (showSecuritySheet) {
        SettingsSheet(
            authState = authState,
            isDarkMode = uiState.isDarkMode,
            onToggleDarkMode = viewModel::setDarkMode,
            onDismiss = { showSecuritySheet = false },
            onEnableAppLock = authViewModel::enableAppLock,
            onDisableAppLockWithoutPin = authViewModel::disableAppLockWithoutPin,
            onSetupPinAndEnable = authViewModel::enableAppLockWithNewPin,
            onEnableExistingPin = authViewModel::enableExistingPinAppLock,
            onVerifyAndChangePin = authViewModel::verifyAndUpdatePin,
            onResetWithNewPin = authViewModel::resetWithNewPin,
            onClearPinAndDisableLock = authViewModel::clearPinAndDisableLock,
            onVerifyAndDisableLock = authViewModel::verifyAndDisableAppLock,
            onToggleBiometric = authViewModel::setBiometricEnabled,
            onToggleDeviceFallback = authViewModel::setDeviceCredentialFallbackEnabled,
            onToggleLockOnBackground = authViewModel::setLockOnBackground,
            onOpenBiometricSetup = { showBiometricSetupModal = true },
            onOpenBiometricStats = {
                showSecuritySheet = false
                onNavigateToBiometricStats()
            },
            onLockNow = authViewModel::lockAppNow
        )
    }

    if (showBiometricSetupModal) {
        BiometricSetupModal(
            biometricAuthManager = biometricAuthManager,
            onDismiss = { showBiometricSetupModal = false },
            onCapabilitiesRefreshed = authViewModel::refreshCapabilities,
            onSystemSettingsOpened = authViewModel::onSystemSecuritySettingsOpened,
            onSystemSettingsReturned = authViewModel::onSystemSecuritySettingsReturned,
            onBiometricVerified = {
                authViewModel.refreshCapabilities()
                if (authState.settings.isAppLockEnabled && !authState.settings.isBiometricEnabled) {
                    authViewModel.setBiometricEnabled(true)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    uiState: MainUiState,
    isAppLockEnabled: Boolean,
    onOpenSecuritySettings: () -> Unit,
    onNetworkActionClick: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onStopDiscovery: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    onOpenTransfer: (peerId: String?, peerName: String?, host: String?) -> Unit = { _, _, _ -> },
    onConnectWifiPeer: (String) -> Unit = {},
    onDisconnectWifiDirect: () -> Unit = {},
    onDismissConnectionNotice: () -> Unit = {},
    onOpenNetworkTelemetry: () -> Unit = {},
    onOpenLiveMetrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.main_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenPairing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("main_pairing_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = stringResource(id = R.string.cd_open_pairing),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenAssistant,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("main_assistant_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(id = R.string.assistant_topbar_button_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenSecuritySettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("security_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = stringResource(id = R.string.cd_security_settings),
                            tint = if (isAppLockEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    FilterChip(
                        selected = uiState.currentLanguage == AppLanguage.BENGALI,
                        onClick = onToggleLanguage,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("language_toggle_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = stringResource(id = R.string.cd_language_toggle),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (uiState.currentLanguage == AppLanguage.ENGLISH) "EN" else "বাংলা",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .testTag("main_lazy_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "brand_banner_card") {
                    BrandBannerCard()
                }

                item(key = "network_status_card") {
                    NetworkStatusCard(
                        status = uiState.networkStatus,
                        savedPeerCount = uiState.peers.size,
                        onPrimaryAction = onNetworkActionClick,
                        onRefresh = onRefreshNetwork,
                        onOpenTelemetry = onOpenNetworkTelemetry,
                        onOpenLiveMetrics = onOpenLiveMetrics
                    )
                }

                item(key = "p2p_radar_pulse_card") {
                    val isSearching = uiState.networkStatus.isDiscovering ||
                        uiState.networkStatus.state == P2PConnectionState.DISCOVERING
                    AnimatedVisibility(
                        visible = isSearching,
                        enter = fadeIn(tween(240)) + expandVertically(tween(260)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(220))
                    ) {
                        RadarSearchCard(
                            isSearching = isSearching,
                            transportMode = uiState.networkStatus.transportMode,
                            discoveredPeersCount = uiState.networkStatus.discoveredPeersCount,
                            onStopScan = onStopDiscovery
                        )
                    }
                }

                item(key = "qr_pairing_cta_card") {
                    PairingQuickActionCard(onClick = onOpenPairing)
                }

                item(key = "transfer_hub_cta_card") {
                    TransferQuickActionCard(
                        onClick = {
                            onOpenTransfer(
                                uiState.linkInfo.connectedPeerAddress,
                                uiState.linkInfo.connectedPeerName,
                                uiState.linkInfo.suggestedTargetHost
                            )
                        }
                    )
                }

                uiState.connectionNotice?.let { notice ->
                    item(key = "wifi_connection_notice") {
                        Surface(
                            onClick = onDismissConnectionNotice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("main_connection_notice"),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                if (uiState.linkInfo.isGroupFormed) {
                    item(key = "wifi_direct_session_card") {
                        WifiDirectSessionCard(
                            linkInfo = uiState.linkInfo,
                            onDisconnect = onDisconnectWifiDirect,
                            onOpenTransferHub = {
                                onOpenTransfer(
                                    uiState.linkInfo.connectedPeerAddress,
                                    uiState.linkInfo.connectedPeerName,
                                    uiState.linkInfo.suggestedTargetHost
                                )
                            }
                        )
                    }
                }

                if (uiState.activeTransfers.isNotEmpty()) {
                    item(key = "active_transfers_header") {
                        Text(
                            text = stringResource(id = R.string.transfer_active_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    items(
                        items = uiState.activeTransfers.take(3),
                        key = { "main_transfer_${it.transferId}" }
                    ) { transfer ->
                        TransferProgressRowCard(transfer = transfer)
                    }
                }

                if (uiState.discoveredWifiPeers.isNotEmpty()) {
                    item(key = "discovered_wifi_peers_header") {
                        Text(
                            text = stringResource(id = R.string.wifi_direct_nearby_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    items(
                        items = uiState.discoveredWifiPeers,
                        key = { "wifi_peer_${it.deviceAddress}" }
                    ) { wifiPeer ->
                        DiscoveredWifiPeerCard(
                            peer = wifiPeer,
                            onConnectClick = { onConnectWifiPeer(wifiPeer.deviceAddress) },
                            onOpenTransferClick = {
                                onOpenTransfer(
                                    wifiPeer.deviceAddress,
                                    wifiPeer.deviceName,
                                    uiState.linkInfo.suggestedTargetHost
                                )
                            }
                        )
                    }
                }

                item(key = "live_mesh_status_card") {
                    LiveMeshStatusCard(
                        uiState = uiState,
                        onOpenPairing = onOpenPairing,
                        onOpenTransfer = {
                            onOpenTransfer(
                                uiState.linkInfo.connectedPeerAddress,
                                uiState.linkInfo.connectedPeerName,
                                uiState.linkInfo.suggestedTargetHost
                            )
                        }
                    )
                }

                if (uiState.peers.isNotEmpty()) {
                    item(key = "peers_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.peers_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.peers_discovered_chip,
                                    uiState.networkStatus.discoveredPeersCount
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    items(
                        items = uiState.peers,
                        key = { it.id }
                    ) { peer ->
                        PeerNodeCard(
                            peer = peer,
                            onClick = {
                                onOpenTransfer(
                                    peer.id,
                                    peer.displayName,
                                    uiState.linkInfo.suggestedTargetHost
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMeshStatusCard(
    uiState: MainUiState,
    onOpenPairing: () -> Unit,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = uiState.networkStatus
    val transportLabel = when (status.transportMode) {
        P2PTransportMode.WIFI_DIRECT -> stringResource(id = R.string.network_transport_wifi_direct)
        P2PTransportMode.LOCAL_LAN -> stringResource(id = R.string.network_transport_local_mesh)
        P2PTransportMode.STANDBY -> stringResource(id = R.string.network_transport_unavailable)
    }

    val cardTitle = when (status.state) {
        P2PConnectionState.CONNECTED -> stringResource(id = R.string.main_live_card_title_connected)
        P2PConnectionState.DISCOVERING -> stringResource(id = R.string.main_live_card_title_discovering)
        P2PConnectionState.READY -> stringResource(id = R.string.main_live_card_title_ready)
        P2PConnectionState.PERMISSION_REQUIRED -> stringResource(id = R.string.main_live_card_title_permission)
        P2PConnectionState.OFFLINE -> stringResource(id = R.string.main_live_card_title_offline)
    }

    val cardDesc = when (status.state) {
        P2PConnectionState.CONNECTED -> stringResource(id = R.string.main_live_card_desc_connected, transportLabel)
        P2PConnectionState.DISCOVERING -> stringResource(id = R.string.main_live_card_desc_discovering, transportLabel)
        P2PConnectionState.READY -> stringResource(id = R.string.main_live_card_desc_ready, transportLabel)
        P2PConnectionState.PERMISSION_REQUIRED -> stringResource(id = R.string.main_live_card_desc_permission)
        P2PConnectionState.OFFLINE -> stringResource(id = R.string.main_live_card_desc_offline)
    }

    val linkedPeerCount = maxOf(
        status.connectedPeersCount,
        if (uiState.linkInfo.isGroupFormed) 1 else 0
    )
    val discoveredPeerCount = maxOf(
        status.discoveredPeersCount,
        uiState.discoveredWifiPeers.size
    )

    val latestTransfer = uiState.activeTransfers.firstOrNull()
    val recentActivityText = when {
        latestTransfer != null -> stringResource(
            id = R.string.main_live_recent_transfer,
            latestTransfer.fileName,
            latestTransfer.progressPercent
        )
        uiState.recentMessage != null -> stringResource(
            id = R.string.main_live_recent_message,
            uiState.recentMessage.ciphertext.take(44)
        )
        uiState.lastScannedQr != null -> stringResource(
            id = R.string.main_live_recent_pairing,
            uiState.lastScannedQr.displayName,
            uiState.lastScannedQr.nodeId
        )
        uiState.peers.isNotEmpty() -> stringResource(
            id = R.string.main_live_recent_pairing,
            uiState.peers.first().displayName,
            uiState.peers.first().id
        )
        else -> stringResource(id = R.string.main_live_no_recent_activity)
    }

    val hasPeersEmpty = uiState.peers.isEmpty() && uiState.discoveredWifiPeers.isEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (hasPeersEmpty) "empty_mesh_placeholder" else "live_mesh_status_container"),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .testTag("live_mesh_summary_card"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (linkedPeerCount > 0) Icons.Rounded.Hub else Icons.Rounded.Sensors,
                        contentDescription = cardTitle,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = cardDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(id = R.string.main_live_metric_transport, transportLabel),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                id = R.string.main_live_metric_peers,
                                linkedPeerCount,
                                discoveredPeerCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = if (uiState.isSocketServerListening) {
                                stringResource(id = R.string.main_live_metric_socket_up, uiState.serverPort)
                            } else {
                                stringResource(id = R.string.main_live_metric_socket_down)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.isSocketServerListening) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_peers_badge, uiState.peers.count { it.isTrusted }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_mesh_recent_activity_banner"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ) {
                Text(
                    text = recentActivityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPairing,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("live_mesh_pair_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.main_live_action_pair),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onOpenTransfer,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("live_mesh_transfer_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.main_live_action_transfer),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerNodeCard(
    peer: User,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("peer_card_${peer.id}"),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(peer.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = peer.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (peer.isTrusted) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = peer.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PairingQuickActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("main_pair_device_button"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = stringResource(id = R.string.cd_open_pairing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.main_pair_device_cta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.main_pair_device_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    AHMeshTheme {
        MainScreenContent(
            uiState = MainUiState(
                networkStatus = P2PConnectionStatus(
                    state = P2PConnectionState.READY,
                    transportMode = P2PTransportMode.WIFI_DIRECT,
                    isWifiEnabled = true,
                    isWifiDirectSupported = true,
                    hasNearbyPermission = true
                )
            ),
            isAppLockEnabled = true,
            onOpenSecuritySettings = {},
            onNetworkActionClick = {},
            onRefreshNetwork = {},
            onStopDiscovery = {},
            onToggleLanguage = {},
            onOpenPairing = {}
        )
    }
}
