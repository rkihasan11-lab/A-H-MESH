package com.ahmesh.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ahmesh.app.data.local.AppDatabase
import com.ahmesh.app.data.local.dao.BiometricAuditLogDao
import com.ahmesh.app.data.local.dao.MessageDao
import com.ahmesh.app.data.local.dao.NetworkTelemetryDao
import com.ahmesh.app.data.local.dao.ScannedQrHistoryDao
import com.ahmesh.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scanned_qr_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `nodeId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `publicKeyFingerprint` TEXT NOT NULL,
                    `rawPayload` TEXT NOT NULL,
                    `scannedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `biometric_audit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `outcome` TEXT NOT NULL,
                    `isSuccess` INTEGER NOT NULL,
                    `authMethod` TEXT NOT NULL,
                    `detail` TEXT,
                    `attemptedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `scanned_qr_history` ADD COLUMN `isSuccess` INTEGER NOT NULL DEFAULT 1"
            )
            db.execSQL(
                "ALTER TABLE `scanned_qr_history` ADD COLUMN `failureReason` TEXT"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `network_telemetry_samples` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `connectionState` TEXT NOT NULL,
                    `transportMode` TEXT NOT NULL,
                    `stabilityScore` INTEGER NOT NULL,
                    `discoveredPeersCount` INTEGER NOT NULL,
                    `connectedPeersCount` INTEGER NOT NULL,
                    `bytesTransferred` INTEGER NOT NULL,
                    `direction` TEXT NOT NULL,
                    `eventType` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_network_telemetry_samples_timestamp` ON `network_telemetry_samples` (`timestamp`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ah_mesh_db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideScannedQrHistoryDao(database: AppDatabase): ScannedQrHistoryDao =
        database.scannedQrHistoryDao()

    @Provides
    fun provideBiometricAuditLogDao(database: AppDatabase): BiometricAuditLogDao =
        database.biometricAuditLogDao()

    @Provides
    fun provideNetworkTelemetryDao(database: AppDatabase): NetworkTelemetryDao =
        database.networkTelemetryDao()
}
