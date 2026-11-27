package com.ahmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ahmesh.app.domain.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val publicKey: String,
    val avatarColor: Long,
    val lastSeenAt: Long,
    val isTrusted: Boolean
) {
    fun toDomain(): User = User(
        id = id,
        displayName = displayName,
        publicKey = publicKey,
        avatarColor = avatarColor,
        lastSeenAt = lastSeenAt,
        isTrusted = isTrusted
    )

    companion object {
        fun fromDomain(user: User): UserEntity = UserEntity(
            id = user.id,
            displayName = user.displayName,
            publicKey = user.publicKey,
            avatarColor = user.avatarColor,
            lastSeenAt = user.lastSeenAt,
            isTrusted = user.isTrusted
        )
    }
}
