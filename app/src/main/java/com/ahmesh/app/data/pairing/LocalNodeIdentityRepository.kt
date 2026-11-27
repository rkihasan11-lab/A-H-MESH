package com.ahmesh.app.data.pairing

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ahmesh.app.domain.model.PairingPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LocalNodeIdentityRepository {

    private val prefs: SharedPreferences?
    private val _localIdentity: MutableStateFlow<PairingPayload>

    open val localIdentity: StateFlow<PairingPayload>
        get() = _localIdentity.asStateFlow()

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.prefs = createPreferences(context)
        this._localIdentity = MutableStateFlow(loadOrInitializeIdentity(this.prefs))
    }

    @VisibleForTesting
    protected constructor(initialIdentity: PairingPayload) {
        this.prefs = null
        this._localIdentity = MutableStateFlow(initialIdentity)
    }

    open fun updateDisplayName(rawName: String) {
        val sanitized = rawName.trim().take(32)
        if (sanitized.isEmpty()) return
        prefs?.edit()?.putString(KEY_DISPLAY_NAME, sanitized)?.apply()
        _localIdentity.value = _localIdentity.value.copy(
            displayName = sanitized,
            issuedAt = System.currentTimeMillis()
        )
    }

    open fun rotateSessionTimestamp() {
        _localIdentity.value = _localIdentity.value.copy(
            issuedAt = System.currentTimeMillis()
        )
    }

    private fun loadOrInitializeIdentity(preferences: SharedPreferences): PairingPayload {
        val existingKey = preferences.getString(KEY_PUBLIC_KEY, null)
        val existingNodeId = preferences.getString(KEY_NODE_ID, null)
        val existingName = preferences.getString(KEY_DISPLAY_NAME, null)

        val publicKey = existingKey ?: generateEcPublicKeyBase64()
        val nodeId = existingNodeId ?: deriveNodeId(publicKey)
        val displayName = existingName ?: defaultDeviceAlias(nodeId)

        if (existingKey == null || existingNodeId == null || existingName == null) {
            preferences.edit()
                .putString(KEY_PUBLIC_KEY, publicKey)
                .putString(KEY_NODE_ID, nodeId)
                .putString(KEY_DISPLAY_NAME, displayName)
                .apply()
        }

        return PairingPayload(
            nodeId = nodeId,
            displayName = displayName,
            publicKey = publicKey,
            transportHint = "WIFI_DIRECT",
            issuedAt = System.currentTimeMillis()
        )
    }

    private fun generateEcPublicKeyBase64(): String {
        return runCatching {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = generator.generateKeyPair()
            Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        }.getOrElse {
            val fallback = "${Build.MODEL}-${System.nanoTime()}".toByteArray(Charsets.UTF_8)
            Base64.encodeToString(fallback, Base64.NO_WRAP)
        }
    }

    private fun deriveNodeId(publicKey: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray(Charsets.UTF_8))
        val shortHex = hash.take(5).joinToString("") { "%02x".format(it) }
        return "ah-$shortHex"
    }

    private fun defaultDeviceAlias(nodeId: String): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() }?.take(16) ?: "AH Node"
        val suffix = nodeId.takeLast(4).uppercase()
        return "$model-$suffix"
    }

    @Suppress("DEPRECATION")
    private fun createPreferences(appContext: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val PREFS_NAME = "ah_mesh_node_identity"
        const val KEY_NODE_ID = "node_id"
        const val KEY_PUBLIC_KEY = "public_key"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
