package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface MessageRepository {
    fun observeConversation(conversationId: String): Flow<List<P2PMessage>>
    fun observeAllMessages(): Flow<List<P2PMessage>> = emptyFlow()
    fun observeAllUsers(): Flow<List<User>>
    fun observeScannedQrHistory(): Flow<List<ScannedQrRecord>>
    fun observeQrScanStatistics(): Flow<QrScanStatistics>
    suspend fun saveMessage(message: P2PMessage)
    suspend fun upsertUser(user: User)
    suspend fun ensurePeerKnown(user: User) {}
    suspend fun recordScannedQr(record: ScannedQrRecord)
    suspend fun clearScannedQrHistory()
    suspend fun deleteMessage(messageId: String)
}
