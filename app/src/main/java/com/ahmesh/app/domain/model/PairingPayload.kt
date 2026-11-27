package com.ahmesh.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class PairingPayload(
    @SerialName("v") val version: Int = CURRENT_VERSION,
    @SerialName("n") val nodeId: String,
    @SerialName("d") val displayName: String,
    @SerialName("k") val publicKey: String,
    @SerialName("t") val transportHint: String = "WIFI_DIRECT",
    @SerialName("i") val issuedAt: Long = System.currentTimeMillis()
) {
    fun shortKeyFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString(":") { byte -> "%02X".format(byte) }
    }

    fun derivedAvatarColor(): Long {
        val palette = listOf(
            0xFF0284C7L,
            0xFF7C3AEDL,
            0xFF0EA5E9L,
            0xFF9333EAL,
            0xFF0D9488L,
            0xFF2563EBL
        )
        val index = (nodeId.hashCode() and 0x7FFFFFFF) % palette.size
        return palette[index]
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

object PairingCodec {
    private const val PAYLOAD_PREFIX = "AHMESH1:"
    private const val URI_PREFIX = "ahmesh://pair?"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: PairingPayload): String {
        return PAYLOAD_PREFIX + json.encodeToString(PairingPayload.serializer(), payload)
    }

    fun decode(rawInput: String): PairingPayload? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val jsonCandidate = when {
            trimmed.startsWith(PAYLOAD_PREFIX) -> trimmed.removePrefix(PAYLOAD_PREFIX).trim()
            trimmed.startsWith(URI_PREFIX) -> trimmed.removePrefix(URI_PREFIX).trim()
            trimmed.startsWith("{") -> trimmed
            else -> return null
        }

        val parsed = runCatching {
            json.decodeFromString(PairingPayload.serializer(), jsonCandidate)
        }.getOrNull() ?: return null

        if (parsed.nodeId.isBlank() || parsed.displayName.isBlank() || parsed.publicKey.isBlank()) {
            return null
        }
        return parsed
    }

    fun isValidPublicKeyFormat(publicKey: String): Boolean {
        val candidate = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .trim()
        if (candidate.length < 16 || candidate.length % 4 == 1) return false
        return BASE64_KEY_REGEX.matches(candidate)
    }

    private val BASE64_KEY_REGEX = Regex("^[A-Za-z0-9+/_-]{16,}={0,2}$")
}
