package com.ahmesh.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ahmesh.app.domain.model.SecuritySettings
import com.ahmesh.app.domain.repository.SecurityPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedSecurityPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurityPreferencesRepository {

    private val prefs: SharedPreferences by lazy { createPreferences(context) }

    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    override suspend fun configurePin(pin: String) {
        withContext(Dispatchers.IO) {
            val salt = ByteArray(SALT_SIZE_BYTES).apply {
                SecureRandom().nextBytes(this)
            }
            val derivedHash = derivePbkdf2Hash(pin, salt)
            prefs.edit()
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PIN_HASH, Base64.encodeToString(derivedHash, Base64.NO_WRAP))
                .putInt(KEY_PIN_LENGTH, pin.length)
                .apply()
            _settings.value = readSettings()
        }
    }

    override suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val saltEncoded = prefs.getString(KEY_PIN_SALT, null) ?: return@withContext false
        val hashEncoded = prefs.getString(KEY_PIN_HASH, null) ?: return@withContext false
        val salt = runCatching { Base64.decode(saltEncoded, Base64.NO_WRAP) }.getOrNull()
            ?: return@withContext false
        val expectedHash = runCatching { Base64.decode(hashEncoded, Base64.NO_WRAP) }.getOrNull()
            ?: return@withContext false
        val candidateHash = derivePbkdf2Hash(pin, salt)
        MessageDigest.isEqual(candidateHash, expectedHash)
    }

    override suspend fun clearPinAndLock() {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .remove(KEY_PIN_SALT)
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_LENGTH)
                .putBoolean(KEY_APP_LOCK_ENABLED, false)
                .apply()
            _settings.value = readSettings()
        }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
            _settings.value = readSettings()
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
            _settings.value = readSettings()
        }
    }

    override suspend fun setDeviceCredentialFallbackEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_DEVICE_FALLBACK_ENABLED, enabled).apply()
            _settings.value = readSettings()
        }
    }

    override suspend fun setLockOnBackground(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_LOCK_ON_BACKGROUND, enabled).apply()
            _settings.value = readSettings()
        }
    }

    private fun readSettings(): SecuritySettings {
        val hasPin = !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty() &&
            !prefs.getString(KEY_PIN_SALT, null).isNullOrEmpty()
        return SecuritySettings(
            isAppLockEnabled = prefs.getBoolean(KEY_APP_LOCK_ENABLED, true),
            isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true),
            isDeviceCredentialFallbackEnabled = prefs.getBoolean(KEY_DEVICE_FALLBACK_ENABLED, true),
            lockOnBackground = prefs.getBoolean(KEY_LOCK_ON_BACKGROUND, true),
            hasConfiguredPin = hasPin,
            pinLength = prefs.getInt(KEY_PIN_LENGTH, DEFAULT_PIN_LENGTH).coerceIn(4, 6)
        )
    }

    private fun derivePbkdf2Hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreferences(appContext: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Fallback only for environments without AndroidKeyStore (e.g., Robolectric JVM tests)
            appContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "ah_mesh_security_prefs"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_DEVICE_FALLBACK_ENABLED = "device_fallback_enabled"
        const val KEY_LOCK_ON_BACKGROUND = "lock_on_background"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
        const val DEFAULT_PIN_LENGTH = 4
        const val SALT_SIZE_BYTES = 16
        const val PBKDF2_ITERATIONS = 10_000
        const val HASH_KEY_BITS = 256
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
