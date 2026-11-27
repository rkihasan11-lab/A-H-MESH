package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.ActiveTransferState
import com.ahmesh.app.domain.model.DEFAULT_P2P_SOCKET_PORT
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.PendingOutgoingFile
import kotlinx.coroutines.flow.StateFlow

interface P2PTransferRepository {
    val activeTransfers: StateFlow<List<ActiveTransferState>>
    val isServerListening: StateFlow<Boolean>
    val serverPort: StateFlow<Int>
    val fileSizeWarningThresholdBytes: StateFlow<Long>

    fun setFileSizeWarningThresholdBytes(bytes: Long)
    fun ensureServerStarted()
    fun stopServer()

    suspend fun sendTextMessage(
        conversationId: String,
        recipientId: String,
        targetHost: String,
        targetPort: Int = DEFAULT_P2P_SOCKET_PORT,
        text: String
    ): Result<P2PMessage>

    suspend fun sendFileOrApk(
        conversationId: String,
        recipientId: String,
        targetHost: String,
        targetPort: Int = DEFAULT_P2P_SOCKET_PORT,
        item: PendingOutgoingFile
    ): Result<P2PMessage>

    fun clearCompletedTransfers()
}
