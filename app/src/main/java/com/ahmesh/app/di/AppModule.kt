package com.ahmesh.app.di

import com.ahmesh.app.core.localization.AppLanguageManager
import com.ahmesh.app.core.localization.LanguageManager
import com.ahmesh.app.core.theme.DataStoreThemeManager
import com.ahmesh.app.core.theme.ThemeManager
import com.ahmesh.app.data.network.AndroidWifiDirectManager
import com.ahmesh.app.data.network.P2PSocketTransferManager
import com.ahmesh.app.data.repository.MessageRepositoryImpl
import com.ahmesh.app.data.repository.NetworkTelemetryRepositoryImpl
import com.ahmesh.app.data.repository.SecurityAuditRepositoryImpl
import com.ahmesh.app.data.security.EncryptedSecurityPreferencesRepository
import com.ahmesh.app.domain.repository.MessageRepository
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.ahmesh.app.domain.repository.NetworkTelemetryRepository
import com.ahmesh.app.domain.repository.P2PTransferRepository
import com.ahmesh.app.domain.repository.SecurityAuditRepository
import com.ahmesh.app.domain.repository.SecurityPreferencesRepository
import com.ahmesh.app.domain.repository.WifiDirectRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindSecurityAuditRepository(
        impl: SecurityAuditRepositoryImpl
    ): SecurityAuditRepository

    @Binds
    @Singleton
    abstract fun bindLanguageManager(
        impl: AppLanguageManager
    ): LanguageManager

    @Binds
    @Singleton
    abstract fun bindNetworkStatusMonitor(
        impl: AndroidWifiDirectManager
    ): NetworkStatusMonitor

    @Binds
    @Singleton
    abstract fun bindWifiDirectRepository(
        impl: AndroidWifiDirectManager
    ): WifiDirectRepository

    @Binds
    @Singleton
    abstract fun bindP2PTransferRepository(
        impl: P2PSocketTransferManager
    ): P2PTransferRepository

    @Binds
    @Singleton
    abstract fun bindNetworkTelemetryRepository(
        impl: NetworkTelemetryRepositoryImpl
    ): NetworkTelemetryRepository

    @Binds
    @Singleton
    abstract fun bindSecurityPreferencesRepository(
        impl: EncryptedSecurityPreferencesRepository
    ): SecurityPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindThemeManager(
        impl: DataStoreThemeManager
    ): ThemeManager
}
