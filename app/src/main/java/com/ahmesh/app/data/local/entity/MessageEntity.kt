package com.ahmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.P2PMessage

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val ciphertext: String,
    val type: MessageType,
    val status: MessageStatus,
    val payloadUri: String?,
    val payloadSize: Long,
    val timestamp: Long
) {
    fun toDomain(): P2PMessage = P2PMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        ciphertext = ciphertext,
        type = type,
        status = status,
        payloadUri = payloadUri,
        payloadSize = payloadSize,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(message: P2PMessage): MessageEntity = MessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            recipientId = message.recipientId,
            ciphertext = message.ciphertext,
            type = message.type,
            status = message.status,
            payloadUri = message.payloadUri,
            payloadSize = message.payloadSize,
            timestamp = message.timestamp
        )
    }
}
