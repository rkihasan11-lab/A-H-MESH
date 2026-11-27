package com.ahmesh.app.presentation.screens.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.LocalIsDarkTheme
import com.ahmesh.app.core.theme.StatusConnectedDark
import com.ahmesh.app.core.theme.StatusConnectedLight
import com.ahmesh.app.data.storage.AhMeshStorageManager
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.InstalledAppInfo
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.domain.model.TransferStatus
import com.ahmesh.app.presentation.components.TransferProgressRowCard
import java.io.File

@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    peerId: String? = null,
    peerName: String? = null,
    initialHost: String? = null,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showApkPickerSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(peerId, peerName, initialHost) {
        viewModel.setPeerContext(peerId, peerName, initialHost)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFilePicked(uri)
        }
    }

    TransferScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onTargetHostChange = viewModel::onTargetHostChange,
        onMessageDraftChange = viewModel::onMessageDraftChange,
        onSendMessage = viewModel::sendTextMessage,
        onPickFileClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
        onOpenApkPickerClick = {
            viewModel.loadInstalledApps()
            showApkPickerSheet = true
        },
        onSelectLimitMb = viewModel::setFileSizeLimitMb,
        onClearCompleted = viewModel::clearCompletedTransfers,
        onConfirmOversized = viewModel::confirmOversizedTransfer,
        onDismissOversized = viewModel::dismissOversizedWarning,
        onDismissError = viewModel::dismissError,
        onRefreshConnection = viewModel::refreshConnection,
        onOpenAttachment = { message ->
            openSavedAttachment(
                context = context,
                rawUriOrPath = message.payloadUri,
                fileName = message.ciphertext,
                mimeTypeHint = ""
            )
        },
        onOpenTransferFile = { transfer ->
            openSavedAttachment(
                context = context,
                rawUriOrPath = transfer.savedUri,
                fileName = transfer.fileName,
                mimeTypeHint = transfer.mimeType
            )
        },
        modifier = modifier
    )

    if (showApkPickerSheet) {
        InstalledAppsPickerSheet(
            apps = uiState.filteredInstalledApps,
            isLoading = uiState.isLoadingApps,
            searchQuery = uiState.appSearchQuery,
            onSearchQueryChange = viewModel::onAppSearchQueryChange,
            onSelectApp = { app ->
                showApkPickerSheet = false
                viewModel.onInstalledAppSelected(app)
            },
            onDismiss = { showApkPickerSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreenContent(
    uiState: TransferUiState,
    onNavigateBack: () -> Unit,
    onTargetHostChange: (String) -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onPickFileClick: () -> Unit,
    onOpenApkPickerClick: () -> Unit,
    onSelectLimitMb: (Int) -> Unit,
    onClearCompleted: () -> Unit,
    onConfirmOversized: () -> Unit,
    onDismissOversized: () -> Unit,
    onDismissError: () -> Unit,
    onRefreshConnection: () -> Unit = {},
    onOpenAttachment: (P2PMessage) -> Unit = {},
    onOpenTransferFile: (ActiveTransferState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val serverColor = if (uiState.isServerListening) {
        if (isDark) StatusConnectedDark else StatusConnectedLight
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("transfer_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transfer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_transfer_back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.transfer_screen_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.peerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("transfer_port_badge"),
                        shape = RoundedCornerShape(50),
                        color = serverColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, serverColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = stringResource(id = R.string.transfer_port_badge, uiState.serverPort),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = serverColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            TransferComposerBar(
                messageDraft = uiState.messageDraft,
                onMessageDraftChange = onMessageDraftChange,
                onSendMessage = onSendMessage,
                onPickFileClick = onPickFileClick,
                onOpenApkPickerClick = onOpenApkPickerClick
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
                    .testTag("transfer_lazy_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "connection_controls") {
                    TransferTargetAndLimitCard(
                        targetHost = uiState.targetHost,
                        storageFolderDisplay = uiState.storageFolderDisplay,
                        selectedLimitMb = uiState.fileSizeLimitMb,
                        onTargetHostChange = onTargetHostChange,
                        onSelectLimitMb = onSelectLimitMb,
                        onRefreshConnection = onRefreshConnection
                    )
                }

                uiState.errorMessage?.let { err ->
                    item(key = "transfer_error_banner") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_error_banner"),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onDismissError,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.activeTransfers.isNotEmpty()) {
                    item(key = "active_transfers_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_active_list"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.transfer_active_section_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = onClearCompleted,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ClearAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = stringResource(id = R.string.transfer_clear_completed))
                            }
                        }
                    }

                    items(
                        items = uiState.activeTransfers,
                        key = { "tr_${it.transferId}" }
                    ) { transfer ->
                        TransferProgressRowCard(
                            transfer = transfer,
                            onClick = if (
                                transfer.status == TransferStatus.COMPLETED &&
                                !transfer.savedUri.isNullOrBlank()
                            ) {
                                { onOpenTransferFile(transfer) }
                            } else {
                                null
                            }
                        )
                    }
                }

                if (uiState.messages.isEmpty()) {
                    item(key = "messages_empty") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_empty_state"),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(id = R.string.transfer_messages_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(id = R.string.transfer_messages_empty_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.messages,
                        key = { "msg_${it.id}" }
                    ) { message ->
                        val isLocal = if (uiState.localNodeId.isNotBlank()) {
                            message.senderId == uiState.localNodeId
                        } else {
                            message.recipientId == uiState.recipientId
                        }
                        P2PMessageBubbleCard(
                            message = message,
                            isLocalSender = isLocal,
                            onOpenAttachment = { onOpenAttachment(message) }
                        )
                    }
                }
            }
        }
    }

    val pendingOversized = uiState.pendingOversizedItem
    if (pendingOversized != null) {
        AlertDialog(
            onDismissRequest = onDismissOversized,
            modifier = Modifier.testTag("transfer_size_warning_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = stringResource(id = R.string.transfer_size_warning_title))
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.transfer_size_warning_body,
                        pendingOversized.fileName,
                        TransferSizeLimits.formatByteSize(pendingOversized.sizeBytes),
                        TransferSizeLimits.formatByteSize(uiState.fileSizeLimitBytes)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmOversized,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_size_warning_confirm")
                ) {
                    Text(text = stringResource(id = R.string.transfer_size_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissOversized,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_size_warning_cancel")
                ) {
                    Text(text = stringResource(id = R.string.transfer_size_warning_cancel))
                }
            }
        )
    }
}

@Composable
private fun TransferTargetAndLimitCard(
    targetHost: String,
    storageFolderDisplay: String,
    selectedLimitMb: Int,
    onTargetHostChange: (String) -> Unit,
    onSelectLimitMb: (Int) -> Unit,
    onRefreshConnection: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = targetHost,
                    onValueChange = onTargetHostChange,
                    label = { Text(text = stringResource(id = R.string.transfer_target_host_label)) },
                    placeholder = { Text(text = stringResource(id = R.string.transfer_target_host_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transfer_target_host_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                IconButton(
                    onClick = onRefreshConnection,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_refresh_link_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(id = R.string.transfer_cd_refresh_link),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = stringResource(id = R.string.transfer_size_limit_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransferSizeLimits.PRESET_LIMITS_MB.forEach { limitMb ->
                    FilterChip(
                        selected = selectedLimitMb == limitMb,
                        onClick = { onSelectLimitMb(limitMb) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transfer_limit_chip_$limitMb"),
                        label = {
                            Text(text = stringResource(id = R.string.transfer_size_limit_value, limitMb))
                        }
                    )
                }
            }

            if (storageFolderDisplay.isNotBlank()) {
                Text(
                    text = stringResource(id = R.string.transfer_storage_folder_hint, storageFolderDisplay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun P2PMessageBubbleCard(
    message: P2PMessage,
    isLocalSender: Boolean,
    onOpenAttachment: () -> Unit = {}
) {
    val containerColor = if (isLocalSender) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val statusText = when (message.status) {
        MessageStatus.SENDING, MessageStatus.PENDING -> stringResource(id = R.string.transfer_msg_status_sending)
        MessageStatus.SENT, MessageStatus.DELIVERED -> stringResource(id = R.string.transfer_msg_status_delivered)
        MessageStatus.FAILED -> stringResource(id = R.string.transfer_msg_status_failed)
    }
    val hasOpenableAttachment = message.type != MessageType.TEXT && !message.payloadUri.isNullOrBlank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isLocalSender) Arrangement.End else Arrangement.Start
    ) {
        val bubbleContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.type != MessageType.TEXT) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (message.type == MessageType.APK) {
                                Icons.Rounded.Android
                            } else {
                                Icons.Rounded.Folder
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = message.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.payloadSize > 0L) {
                            Text(
                                text = "• ${TransferSizeLimits.formatByteSize(message.payloadSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = message.ciphertext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.status == MessageStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (message.status == MessageStatus.DELIVERED || message.status == MessageStatus.SENT) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (hasOpenableAttachment) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (message.type == MessageType.APK) {
                                stringResource(id = R.string.transfer_open_apk_action)
                            } else {
                                stringResource(id = R.string.transfer_open_file_action)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (hasOpenableAttachment) {
            Surface(
                onClick = onOpenAttachment,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("transfer_message_item_${message.id}"),
                shape = RoundedCornerShape(18.dp),
                color = containerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
            ) {
                bubbleContent()
            }
        } else {
            Surface(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("transfer_message_item_${message.id}"),
                shape = RoundedCornerShape(18.dp),
                color = containerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                bubbleContent()
            }
        }
    }
}

private fun openSavedAttachment(
    context: Context,
    rawUriOrPath: String?,
    fileName: String,
    mimeTypeHint: String
) {
    if (rawUriOrPath.isNullOrBlank()) return
    val resolvedUri = when {
        rawUriOrPath.startsWith("content://") -> Uri.parse(rawUriOrPath)
        rawUriOrPath.startsWith("file://") -> {
            val path = Uri.parse(rawUriOrPath).path.orEmpty()
            val targetFile = File(path)
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            }.getOrElse { Uri.fromFile(targetFile) }
        }
        else -> {
            val targetFile = File(rawUriOrPath)
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            }.getOrElse { Uri.parse(rawUriOrPath) }
        }
    }
    val mimeType = AhMeshStorageManager.resolveMimeType(fileName, mimeTypeHint)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(resolvedUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(viewIntent)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.transfer_open_error_toast, fileName),
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun TransferComposerBar(
    messageDraft: String,
    onMessageDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onPickFileClick: () -> Unit,
    onOpenApkPickerClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPickFileClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_pick_file_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.transfer_pick_file_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onOpenApkPickerClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_pick_apk_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.transfer_pick_apk_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = onMessageDraftChange,
                    placeholder = {
                        Text(text = stringResource(id = R.string.transfer_message_input_hint))
                    },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transfer_message_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                FilledIconButton(
                    onClick = onSendMessage,
                    enabled = messageDraft.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(id = R.string.transfer_send_message_cd)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsPickerSheet(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectApp: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("transfer_apk_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.transfer_apk_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                placeholder = {
                    Text(text = stringResource(id = R.string.transfer_apk_search_hint))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transfer_apk_search_input"),
                shape = RoundedCornerShape(14.dp)
            )

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(id = R.string.transfer_apk_loading))
                    }
                }
                apps.isEmpty() -> {
                    Text(
                        text = stringResource(id = R.string.transfer_apk_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = apps,
                            key = { it.packageName }
                        ) { app ->
                            Surface(
                                onClick = { onSelectApp(app) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("apk_item_${app.packageName}"),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Android,
                                        contentDescription = app.appName,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${app.packageName} • v${app.versionName} • ${
                                                TransferSizeLimits.formatByteSize(app.apkSizeBytes)
                                            }",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = stringResource(id = R.string.transfer_apk_send_action),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize()
            ) {
                Text(text = stringResource(id = R.string.transfer_apk_close))
            }
        }
    }
}
