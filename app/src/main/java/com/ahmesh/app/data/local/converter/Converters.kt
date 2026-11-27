package com.ahmesh.app.data.local.converter

import androidx.room.TypeConverter
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.MessageStatus
import com.ahmesh.app.domain.model.MessageType

class Converters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType =
        MessageType.entries.firstOrNull { it.name == value } ?: MessageType.TEXT

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus =
        MessageStatus.entries.firstOrNull { it.name == value } ?: MessageStatus.PENDING

    @TypeConverter
    fun fromBiometricAuditOutcome(value: BiometricAuditOutcome): String = value.name

    @TypeConverter
    fun toBiometricAuditOutcome(value: String): BiometricAuditOutcome =
        BiometricAuditOutcome.entries.firstOrNull { it.name == value } ?: BiometricAuditOutcome.ERROR
}
