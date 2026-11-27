package com.ahmesh.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class P2PMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val ciphertext: String,
    val type: MessageType,
    val status: MessageStatus,
    val payloadUri: String? = null,
    val payloadSize: Long = 0L,
    val timestamp: Long
)
