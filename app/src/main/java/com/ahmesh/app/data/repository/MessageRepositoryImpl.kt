package com.ahmesh.app.data.repository

import com.ahmesh.app.data.local.dao.MessageDao
import com.ahmesh.app.data.local.dao.ScannedQrHistoryDao
import com.ahmesh.app.data.local.dao.UserDao
import com.ahmesh.app.data.local.entity.MessageEntity
import com.ahmesh.app.data.local.entity.ScannedQrHistoryEntity
import com.ahmesh.app.data.local.entity.UserEntity
import com.ahmesh.app.domain.model.P2PMessage
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.ScannedQrRecord
import com.ahmesh.app.domain.model.User
import com.ahmesh.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val scannedQrHistoryDao: ScannedQrHistoryDao
) : MessageRepository {

    override fun observeConversation(conversationId: String): Flow<List<P2PMessage>> {
        return messageDao.observeConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeAllMessages(): Flow<List<P2PMessage>> {
        return messageDao.observeAllMessages().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeAllUsers(): Flow<List<User>> {
        return userDao.observeAllUsers().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeScannedQrHistory(): Flow<List<ScannedQrRecord>> {
        return scannedQrHistoryDao.observeHistory().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeQrScanStatistics(): Flow<QrScanStatistics> {
        return scannedQrHistoryDao.observeScanStats().map { stats ->
            QrScanStatistics(
                totalScans = stats.totalCount,
                successCount = stats.successCount,
                failureCount = stats.failureCount
            )
        }
    }

    override suspend fun saveMessage(message: P2PMessage) {
        messageDao.insertMessage(MessageEntity.fromDomain(message))
    }

    override suspend fun upsertUser(user: User) {
        userDao.upsertUser(UserEntity.fromDomain(user))
    }

    override suspend fun ensurePeerKnown(user: User) {
        val existing = userDao.getUserById(user.id)
        if (existing == null) {
            userDao.upsertUser(UserEntity.fromDomain(user))
        } else {
            userDao.upsertUser(
                existing.copy(
                    displayName = user.displayName.ifBlank { existing.displayName },
                    lastSeenAt = maxOf(existing.lastSeenAt, user.lastSeenAt)
                )
            )
        }
    }

    override suspend fun recordScannedQr(record: ScannedQrRecord) {
        scannedQrHistoryDao.insertScan(ScannedQrHistoryEntity.fromDomain(record))
    }

    override suspend fun clearScannedQrHistory() {
        scannedQrHistoryDao.clearHistory()
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }
}
