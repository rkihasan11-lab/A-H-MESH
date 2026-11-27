package com.ahmesh.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    FILE,
    APK
}
