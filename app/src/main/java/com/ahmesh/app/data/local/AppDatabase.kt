package com.ahmesh.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ahmesh.app.data.local.converter.Converters
import com.ahmesh.app.data.local.dao.BiometricAuditLogDao
import com.ahmesh.app.data.local.dao.MessageDao
import com.ahmesh.app.data.local.dao.NetworkTelemetryDao
import com.ahmesh.app.data.local.dao.ScannedQrHistoryDao
import com.ahmesh.app.data.local.dao.UserDao
import com.ahmesh.app.data.local.entity.BiometricAuditLogEntity
import com.ahmesh.app.data.local.entity.MessageEntity
import com.ahmesh.app.data.local.entity.NetworkTelemetryEntity
import com.ahmesh.app.data.local.entity.ScannedQrHistoryEntity
import com.ahmesh.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        ScannedQrHistoryEntity::class,
        BiometricAuditLogEntity::class,
        NetworkTelemetryEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun scannedQrHistoryDao(): ScannedQrHistoryDao
    abstract fun biometricAuditLogDao(): BiometricAuditLogDao
    abstract fun networkTelemetryDao(): NetworkTelemetryDao
}
