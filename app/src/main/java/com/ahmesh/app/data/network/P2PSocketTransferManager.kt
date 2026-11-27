package com.ahmesh.app.data.network

import androidx.annotation.VisibleForTesting
import com.ahmesh.app.data.pairing.LocalNodeIdentityRepository
import com.ahmesh.app.data.storage.AhMeshStorageManager
import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.DEFAULT_P2P_SOCKET_PORT
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.PendingOutgoingFile
import com.ahmesh.app.domain.model.TransferDirection
import com.ahmesh.app.domain.model.TransferSizeLimits
import com.ahmesh.app.domain.model.TransferStatus
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2PSocketTransferManager @Inject constructor(
    private val messageRepository: MessageRepository,
    private val storageManager: AhMeshStorageManager,
    private val localIdentityRepository: LocalNodeIdentityRepository,
    private val wifiDirectRepository: WifiDirectRepository
) : P2PTransferRepository {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverJob: Job? = null
    @Volatile
    private var activeServerSocket: ServerSocket? = null

    private val _activeTransfers = MutableStateFlow<List<ActiveTransferState>>(emptyList())
    override val activeTransfers: StateFlow<List<ActiveTransferState>> = _activeTransfers.asStateFlow()

    private val _isServerListening = MutableStateFlow(false)
    override val isServerListening: StateFlow<Boolean> = _isServerListening.asStateFlow()

    private val _serverPort = MutableStateFlow(DEFAULT_P2P_SOCKET_PORT)
    override val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _fileSizeWarningThresholdBytes =
        MutableStateFlow(TransferSizeLimits.DEFAULT_WARNING_BYTES)
    override val fileSizeWarningThresholdBytes: StateFlow<Long> =
        _fileSizeWarningThresholdBytes.asStateFlow()

    override fun setFileSizeWarningThresholdBytes(bytes: Long) {
        if (bytes > 0L) {
            _fileSizeWarningThresholdBytes.value = bytes
        }
    }

    override fun ensureServerStarted() {
        startServerOnPort(DEFAULT_P2P_SOCKET_PORT)
    }

    @VisibleForTesting
    internal fun startServerOnPort(requestedPort: Int): Int {
        val existingSocket = activeServerSocket
        if (existingSocket != null && !existingSocket.isClosed && _isServerListening.value) {
            return existingSocket.localPort
        }

        val boundSocket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(requestedPort))
            }
        }.getOrElse {
            ServerSocket(0).apply { reuseAddress = true }
        }

        activeServerSocket = boundSocket
        val actualPort = boundSocket.localPort
        _serverPort.value = actualPort
        _isServerListening.value = true
        wifiDirectRepository.updateSocketServerState(isRunning = true, port = actualPort)

        serverJob?.cancel()
        serverJob = ioScope.launch {
            try {
                while (isActive && !boundSocket.isClosed) {
                    val clientSocket = boundSocket.accept()
                    launch {
                        handleIncomingSocket(clientSocket)
                    }
                }
            } catch (_: IOException) {
                // Server socket closed cleanly or interrupted
            } finally {
                _isServerListening.value = false
                wifiDirectRepository.updateSocketServerState(isRunning = false, port = actualPort)
            }
        }
        return actualPort
    }

    override fun stopServer() {
        _isServerListening.value = false
        wifiDirectRepository.updateSocketServerState(isRunning = false, port = _serverPort.value)
        runCatching { activeServerSocket?.close() }
        activeServerSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    override suspend fun sendTextMessage(
        conversationId: String,
        recipientId: String,
        targetHost: String,
        targetPort: Int,
        text: String
    ): Result<P2PMessage> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Message text cannot be empty."))
        }
        if (targetHost.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Target peer IP address is not resolved yet."))
        }

        val localIdentity = localIdentityRepository.localIdentity.value
        val messageId = UUID.randomUUID().toString()
        val payloadBytes = trimmed.toByteArray(Charsets.UTF_8)
        val initialMessage = P2PMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = localIdentity.nodeId,
            recipientId = recipientId,
            ciphertext = trimmed,
            type = MessageType.TEXT,
            status = MessageStatus.SENDING,
            payloadSize = payloadBytes.size.toLong(),
            timestamp = System.currentTimeMillis()
        )
        messageRepository.saveMessage(initialMessage)

        return@withContext runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)
                socket.soTimeout = IO_TIMEOUT_MS

                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), CHUNK_SIZE_BYTES))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                writeFrameHeader(
                    output = output,
                    frameType = FRAME_TYPE_TEXT,
                    messageId = messageId,
                    conversationId = conversationId,
                    senderId = localIdentity.nodeId,
                    senderName = localIdentity.displayName,
                    recipientId = recipientId,
                    messageType = MessageType.TEXT,
                    fileName = "",
                    mimeType = "text/plain",
                    payloadSize = payloadBytes.size.toLong()
                )
                output.write(payloadBytes)
                output.flush()

                val ack = input.readByte().toInt()
                if (ack != FRAME_ACK_BYTE) {
                    throw IOException("Unexpected peer acknowledgement byte: $ack")
                }
            }

            val deliveredMessage = initialMessage.copy(status = MessageStatus.DELIVERED)
            messageRepository.saveMessage(deliveredMessage)
            deliveredMessage
        }.onFailure { error ->
            if (error is CancellationException) throw error
            messageRepository.saveMessage(initialMessage.copy(status = MessageStatus.FAILED))
        }
    }

    override suspend fun sendFileOrApk(
        conversationId: String,
        recipientId: String,
        targetHost: String,
        targetPort: Int,
        item: PendingOutgoingFile
    ): Result<P2PMessage> = withContext(Dispatchers.IO) {
        if (targetHost.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Target peer IP address is not resolved yet."))
        }

        val localIdentity = localIdentityRepository.localIdentity.value
        val messageId = UUID.randomUUID().toString()
        val transferId = UUID.randomUUID().toString()

        val initialMessage = P2PMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = localIdentity.nodeId,
            recipientId = recipientId,
            ciphertext = item.fileName,
            type = item.messageType,
            status = MessageStatus.SENDING,
            payloadUri = item.uriString ?: item.localFilePath,
            payloadSize = item.sizeBytes,
            timestamp = System.currentTimeMillis()
        )
        messageRepository.saveMessage(initialMessage)

        upsertTransferState(
            ActiveTransferState(
                transferId = transferId,
                fileName = item.fileName,
                mimeType = item.mimeType,
                messageType = item.messageType,
                direction = TransferDirection.OUTGOING,
                status = TransferStatus.QUEUED,
                bytesTransferred = 0L,
                totalBytes = item.sizeBytes,
                peerLabel = recipientId
            )
        )

        return@withContext runCatching {
            val sourceStream = storageManager.openOutgoingInputStream(item)
                ?: throw IOException("Unable to open selected file or APK for reading.")

            sourceStream.use { rawInput ->
                BufferedInputStream(rawInput, CHUNK_SIZE_BYTES).use { bufferedFileInput ->
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)
                        socket.soTimeout = IO_TIMEOUT_MS

                        val output = DataOutputStream(
                            BufferedOutputStream(socket.getOutputStream(), CHUNK_SIZE_BYTES)
                        )
                        val ackInput = DataInputStream(BufferedInputStream(socket.getInputStream()))

                        writeFrameHeader(
                            output = output,
                            frameType = FRAME_TYPE_FILE,
                            messageId = messageId,
                            conversationId = conversationId,
                            senderId = localIdentity.nodeId,
                            senderName = localIdentity.displayName,
                            recipientId = recipientId,
                            messageType = item.messageType,
                            fileName = item.fileName,
                            mimeType = item.mimeType,
                            payloadSize = item.sizeBytes
                        )

                        val buffer = ByteArray(CHUNK_SIZE_BYTES)
                        var bytesSent = 0L
                        var lastProgressUpdateAt = 0L

                        upsertTransferState(
                            ActiveTransferState(
                                transferId = transferId,
                                fileName = item.fileName,
                                mimeType = item.mimeType,
                                messageType = item.messageType,
                                direction = TransferDirection.OUTGOING,
                                status = TransferStatus.TRANSFERRING,
                                bytesTransferred = 0L,
                                totalBytes = item.sizeBytes,
                                peerLabel = recipientId
                            )
                        )

                        while (true) {
                            val read = bufferedFileInput.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            bytesSent += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdateAt >= PROGRESS_THROTTLE_MS || bytesSent >= item.sizeBytes) {
                                lastProgressUpdateAt = now
                                upsertTransferState(
                                    ActiveTransferState(
                                        transferId = transferId,
                                        fileName = item.fileName,
                                        mimeType = item.mimeType,
                                        messageType = item.messageType,
                                        direction = TransferDirection.OUTGOING,
                                        status = TransferStatus.TRANSFERRING,
                                        bytesTransferred = bytesSent,
                                        totalBytes = maxOf(item.sizeBytes, bytesSent),
                                        peerLabel = recipientId
                                    )
                                )
                            }
                        }
                        output.flush()

                        val ack = ackInput.readByte().toInt()
                        if (ack != FRAME_ACK_BYTE) {
                            throw IOException("Peer did not confirm file receipt (ack=$ack).")
                        }

                        val finalTotal = maxOf(item.sizeBytes, bytesSent)
                        upsertTransferState(
                            ActiveTransferState(
                                transferId = transferId,
                                fileName = item.fileName,
                                mimeType = item.mimeType,
                                messageType = item.messageType,
                                direction = TransferDirection.OUTGOING,
                                status = TransferStatus.COMPLETED,
                                bytesTransferred = finalTotal,
                                totalBytes = finalTotal,
                                peerLabel = recipientId
                            )
                        )
                    }
                }
            }

            val delivered = initialMessage.copy(status = MessageStatus.DELIVERED)
            messageRepository.saveMessage(delivered)
            delivered
        }.onFailure { error ->
            if (error is CancellationException) throw error
            upsertTransferState(
                ActiveTransferState(
                    transferId = transferId,
                    fileName = item.fileName,
                    mimeType = item.mimeType,
                    messageType = item.messageType,
                    direction = TransferDirection.OUTGOING,
                    status = TransferStatus.FAILED,
                    bytesTransferred = 0L,
                    totalBytes = item.sizeBytes,
                    peerLabel = recipientId,
                    errorMessage = error.message ?: "Transfer failed"
                )
            )
            messageRepository.saveMessage(initialMessage.copy(status = MessageStatus.FAILED))
        }
    }

    override fun clearCompletedTransfers() {
        _activeTransfers.update { list ->
            list.filter {
                it.status == TransferStatus.QUEUED || it.status == TransferStatus.TRANSFERRING
            }
        }
    }

    private suspend fun handleIncomingSocket(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { client ->
            client.soTimeout = IO_TIMEOUT_MS
            client.inetAddress?.hostAddress?.let { remoteIp ->
                if (remoteIp != "127.0.0.1" && remoteIp != "::1") {
                    wifiDirectRepository.registerResolvedPeerIp(remoteIp)
                }
            }

            val input = DataInputStream(BufferedInputStream(client.getInputStream(), CHUNK_SIZE_BYTES))
            val output = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            val magic = input.readInt()
            if (magic != PROTOCOL_MAGIC) {
                throw IOException("Invalid AH Mesh socket frame header: $magic")
            }
            val version = input.readByte().toInt()
            if (version != PROTOCOL_VERSION) {
                throw IOException("Unsupported AH Mesh protocol version: $version")
            }

            val frameType = input.readByte().toInt()
            val messageId = input.readUTF()
            val conversationId = input.readUTF()
            val senderId = input.readUTF()
            val senderName = input.readUTF()
            val recipientId = input.readUTF()
            val messageTypeName = input.readUTF()
            val rawFileName = input.readUTF()
            val rawMimeType = input.readUTF()
            val payloadSize = input.readLong().coerceAtLeast(0L)

            val messageType = runCatching { MessageType.valueOf(messageTypeName) }
                .getOrDefault(if (frameType == FRAME_TYPE_TEXT) MessageType.TEXT else MessageType.FILE)

            val localIdentity = localIdentityRepository.localIdentity.value
            val normalizedConversationId = when {
                conversationId.isBlank() || conversationId == localIdentity.nodeId ->
                    senderId.ifBlank { DEFAULT_DIRECT_CONVERSATION_ID }
                else -> conversationId
            }

            if (senderId.isNotBlank() && senderId != localIdentity.nodeId) {
                val palette = listOf(0xFF0284C7L, 0xFF7C3AEDL, 0xFF0EA5E9L, 0xFF9333EAL, 0xFF0D9488L, 0xFF2563EBL)
                val avatarColor = palette[(senderId.hashCode() and 0x7FFFFFFF) % palette.size]
                messageRepository.ensurePeerKnown(
                    User(
                        id = senderId,
                        displayName = senderName.ifBlank { senderId },
                        publicKey = "",
                        avatarColor = avatarColor,
                        lastSeenAt = System.currentTimeMillis(),
                        isTrusted = false
                    )
                )
            }

            if (frameType == FRAME_TYPE_TEXT) {
                if (payloadSize > MAX_TEXT_FRAME_BYTES) {
                    throw IOException("Text frame exceeds maximum buffer limit: $payloadSize")
                }
                val bytes = ByteArray(payloadSize.toInt())
                input.readFully(bytes)
                val body = bytes.toString(Charsets.UTF_8)

                val incomingMessage = P2PMessage(
                    id = messageId,
                    conversationId = normalizedConversationId,
                    senderId = senderId,
                    recipientId = recipientId,
                    ciphertext = body,
                    type = MessageType.TEXT,
                    status = MessageStatus.DELIVERED,
                    payloadSize = payloadSize,
                    timestamp = System.currentTimeMillis()
                )
                messageRepository.saveMessage(incomingMessage)
                output.writeByte(FRAME_ACK_BYTE)
                output.flush()
                return@withContext
            }

            val safeName = AhMeshStorageManager.sanitizeFileName(rawFileName)
            val transferId = UUID.randomUUID().toString()
            val peerLabel = senderName.ifBlank { senderId }

            upsertTransferState(
                ActiveTransferState(
                    transferId = transferId,
                    fileName = safeName,
                    mimeType = rawMimeType,
                    messageType = messageType,
                    direction = TransferDirection.INCOMING,
                    status = TransferStatus.TRANSFERRING,
                    bytesTransferred = 0L,
                    totalBytes = payloadSize,
                    peerLabel = peerLabel
                )
            )

            val sink = storageManager.openIncomingFileSink(safeName, rawMimeType)
            var bytesReceived = 0L
            try {
                BufferedOutputStream(sink.outputStream, CHUNK_SIZE_BYTES).use { bufferedOut ->
                    val buffer = ByteArray(CHUNK_SIZE_BYTES)
                    var lastUpdateAt = 0L
                    while (bytesReceived < payloadSize) {
                        val remaining = (payloadSize - bytesReceived).coerceAtMost(buffer.size.toLong()).toInt()
                        val read = input.read(buffer, 0, remaining)
                        if (read < 0) throw EOFException("Stream ended before full file payload received.")
                        bufferedOut.write(buffer, 0, read)
                        bytesReceived += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= PROGRESS_THROTTLE_MS || bytesReceived >= payloadSize) {
                            lastUpdateAt = now
                            upsertTransferState(
                                ActiveTransferState(
                                    transferId = transferId,
                                    fileName = safeName,
                                    mimeType = rawMimeType,
                                    messageType = messageType,
                                    direction = TransferDirection.INCOMING,
                                    status = TransferStatus.TRANSFERRING,
                                    bytesTransferred = bytesReceived,
                                    totalBytes = payloadSize,
                                    peerLabel = peerLabel
                                )
                            )
                        }
                    }
                    bufferedOut.flush()
                }
                sink.finalizeSink()

                val receivedMessage = P2PMessage(
                    id = messageId,
                    conversationId = normalizedConversationId,
                    senderId = senderId,
                    recipientId = recipientId,
                    ciphertext = safeName,
                    type = messageType,
                    status = MessageStatus.DELIVERED,
                    payloadUri = sink.displayUri,
                    payloadSize = bytesReceived,
                    timestamp = System.currentTimeMillis()
                )
                messageRepository.saveMessage(receivedMessage)

                upsertTransferState(
                    ActiveTransferState(
                        transferId = transferId,
                        fileName = safeName,
                        mimeType = rawMimeType,
                        messageType = messageType,
                        direction = TransferDirection.INCOMING,
                        status = TransferStatus.COMPLETED,
                        bytesTransferred = bytesReceived,
                        totalBytes = payloadSize,
                        peerLabel = peerLabel,
                        savedUri = sink.displayUri
                    )
                )

                output.writeByte(FRAME_ACK_BYTE)
                output.flush()
            } catch (error: Throwable) {
                sink.abortSink()
                upsertTransferState(
                    ActiveTransferState(
                        transferId = transferId,
                        fileName = safeName,
                        mimeType = rawMimeType,
                        messageType = messageType,
                        direction = TransferDirection.INCOMING,
                        status = TransferStatus.FAILED,
                        bytesTransferred = bytesReceived,
                        totalBytes = payloadSize,
                        peerLabel = peerLabel,
                        errorMessage = error.message ?: "Receive failed"
                    )
                )
                throw error
            }
        }
    }

    private fun writeFrameHeader(
        output: DataOutputStream,
        frameType: Int,
        messageId: String,
        conversationId: String,
        senderId: String,
        senderName: String,
        recipientId: String,
        messageType: MessageType,
        fileName: String,
        mimeType: String,
        payloadSize: Long
    ) {
        output.writeInt(PROTOCOL_MAGIC)
        output.writeByte(PROTOCOL_VERSION)
        output.writeByte(frameType)
        output.writeUTF(messageId)
        output.writeUTF(conversationId)
        output.writeUTF(senderId)
        output.writeUTF(senderName)
        output.writeUTF(recipientId)
        output.writeUTF(messageType.name)
        output.writeUTF(fileName)
        output.writeUTF(mimeType)
        output.writeLong(payloadSize)
    }

    private fun upsertTransferState(updated: ActiveTransferState) {
        _activeTransfers.update { current ->
            val without = current.filterNot { it.transferId == updated.transferId }
            (listOf(updated) + without).take(25)
        }
    }

    companion object {
        private const val PROTOCOL_MAGIC = 0x41484D31 // "AHM1"
        private const val PROTOCOL_VERSION = 1
        private const val FRAME_TYPE_TEXT = 1
        private const val FRAME_TYPE_FILE = 2
        private const val FRAME_ACK_BYTE = 0x06
        private const val CHUNK_SIZE_BYTES = 16 * 1024
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val IO_TIMEOUT_MS = 20_000
        private const val PROGRESS_THROTTLE_MS = 60L
        private const val MAX_TEXT_FRAME_BYTES = 1024L * 1024L // 1 MB
        private const val DEFAULT_DIRECT_CONVERSATION_ID = "ah_mesh_direct"
    }
}
