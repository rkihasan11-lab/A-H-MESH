package com.ahmesh.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}
