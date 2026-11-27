package com.ahmesh.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val displayName: String,
    val publicKey: String,
    val avatarColor: Long,
    val lastSeenAt: Long,
    val isTrusted: Boolean
)
