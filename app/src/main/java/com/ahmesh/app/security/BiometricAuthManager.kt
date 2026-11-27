package com.ahmesh.app.security

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ahmesh.app.R
import com.ahmesh.app.domain.model.BiometricCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BiometricAuthResult {
    data object Succeeded : BiometricAuthResult
    data object FailedAttempt : BiometricAuthResult
    data class Canceled(
        val message: String? = null,
        val isNegativeButton: Boolean = false
    ) : BiometricAuthResult
    data class Error(
        val message: String? = null,
        val errorCode: Int = -1
    ) : BiometricAuthResult
}

internal enum class PromptErrorOutcome {
    CANCELED,
    FALLBACK_TO_DEVICE_CREDENTIAL,
    ERROR
}

@Singleton
open class BiometricAuthManager {

    private val appContext: Context?

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.appContext = context
    }

    @VisibleForTesting
    protected constructor() {
        this.appContext = null
    }

    private val context: Context
        get() = checkNotNull(appContext) { "ApplicationContext is required at runtime" }

    private var activePromptRef: WeakReference<BiometricPrompt>? = null

    private val _isPromptShowing = MutableStateFlow(false)
    val isPromptShowing: StateFlow<Boolean> = _isPromptShowing.asStateFlow()

    open fun checkBiometricCapability(): BiometricCapability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    open fun canAuthenticateWithDeviceCredential(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(context)
                .canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguard?.isDeviceSecure == true
        }
    }

    fun isAuthenticationAvailable(): Boolean {
        return checkBiometricCapability() == BiometricCapability.AVAILABLE ||
            canAuthenticateWithDeviceCredential()
    }

    /**
     * Ordered candidate intents to open system biometric enrollment (API 30+) or
     * system security settings on older Android versions.
     */
    fun createBiometricEnrollmentIntents(sdkInt: Int = Build.VERSION.SDK_INT): List<Intent> {
        return buildEnrollmentIntentActions(sdkInt).map { action ->
            Intent(action).apply {
                if (sdkInt >= Build.VERSION_CODES.R && action == Settings.ACTION_BIOMETRIC_ENROLL) {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BIOMETRIC_STRONG or BIOMETRIC_WEAK
                    )
                }
            }
        }
    }

    fun createSecuritySettingsIntents(): List<Intent> {
        return listOf(
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    /**
     * Attempts to launch the highest-priority settings intent via [launchAction], falling back
     * gracefully if the primary action is unavailable on the host device.
     */
    fun launchSettingsIntents(
        intents: List<Intent>,
        launchAction: (Intent) -> Unit
    ): Boolean {
        for (intent in intents) {
            try {
                launchAction(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return false
    }

    /**
     * Unified BiometricPrompt entry point supporting biometric hardware and optional
     * system device-credential (PIN/pattern/password) fallback inside the prompt or on error.
     */
    fun authenticate(
        activity: FragmentActivity,
        allowDeviceCredentialFallback: Boolean = true,
        useSystemDeviceCredentialInPrompt: Boolean = false,
        title: String = activity.getString(R.string.auth_prompt_title),
        subtitle: String = activity.getString(R.string.auth_prompt_subtitle),
        negativeButtonText: String = activity.getString(R.string.auth_prompt_negative_pin),
        onResult: (BiometricAuthResult) -> Unit
    ) {
        val hasBiometrics = checkBiometricCapability() == BiometricCapability.AVAILABLE
        if (useSystemDeviceCredentialInPrompt && allowDeviceCredentialFallback && canAuthenticateWithDeviceCredential()) {
            if (!hasBiometrics) {
                authenticateWithDeviceCredential(
                    activity = activity,
                    onResult = onResult
                )
                return
            }

            cancelAuthentication()
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = createPrompt(
                activity = activity,
                executor = executor,
                allowDeviceCredentialFallback = false,
                isDeviceCredentialFlow = true,
                onResult = onResult
            )
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }

            activePromptRef = WeakReference(prompt)
            _isPromptShowing.value = true
            prompt.authenticate(builder.build())
        } else {
            authenticateWithBiometrics(
                activity = activity,
                allowDeviceCredentialFallback = allowDeviceCredentialFallback,
                title = title,
                subtitle = subtitle,
                negativeButtonText = negativeButtonText,
                onResult = onResult
            )
        }
    }

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        allowDeviceCredentialFallback: Boolean = true,
        title: String = activity.getString(R.string.auth_prompt_title),
        subtitle: String = activity.getString(R.string.auth_prompt_subtitle),
        negativeButtonText: String = activity.getString(R.string.auth_prompt_negative_pin),
        onResult: (BiometricAuthResult) -> Unit
    ) {
        cancelAuthentication()

        if (checkBiometricCapability() != BiometricCapability.AVAILABLE) {
            if (allowDeviceCredentialFallback && canAuthenticateWithDeviceCredential()) {
                authenticateWithDeviceCredential(activity, onResult = onResult)
            } else {
                onResult(
                    BiometricAuthResult.Error(
                        message = context.getString(R.string.auth_error_biometric_unavailable),
                        errorCode = BiometricPrompt.ERROR_NO_BIOMETRICS
                    )
                )
            }
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = createPrompt(
            activity = activity,
            executor = executor,
            allowDeviceCredentialFallback = allowDeviceCredentialFallback,
            isDeviceCredentialFlow = false,
            onResult = onResult
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .build()

        activePromptRef = WeakReference(prompt)
        _isPromptShowing.value = true
        prompt.authenticate(promptInfo)
    }

    fun authenticateWithDeviceCredential(
        activity: FragmentActivity,
        title: String = activity.getString(R.string.auth_device_prompt_title),
        subtitle: String = activity.getString(R.string.auth_device_prompt_subtitle),
        onResult: (BiometricAuthResult) -> Unit
    ) {
        cancelAuthentication()

        if (!canAuthenticateWithDeviceCredential()) {
            onResult(
                BiometricAuthResult.Error(
                    message = context.getString(R.string.auth_error_biometric_unavailable),
                    errorCode = BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                )
            )
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = createPrompt(
            activity = activity,
            executor = executor,
            allowDeviceCredentialFallback = false,
            isDeviceCredentialFlow = true,
            onResult = onResult
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        activePromptRef = WeakReference(prompt)
        _isPromptShowing.value = true
        prompt.authenticate(builder.build())
    }

    open fun cancelAuthentication() {
        val activePrompt = activePromptRef?.get()
        activePromptRef = null
        _isPromptShowing.value = false
        activePrompt?.cancelAuthentication()
    }

    private fun createPrompt(
        activity: FragmentActivity,
        executor: Executor,
        allowDeviceCredentialFallback: Boolean,
        isDeviceCredentialFlow: Boolean,
        onResult: (BiometricAuthResult) -> Unit
    ): BiometricPrompt {
        return BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    clearPromptState()
                    onResult(BiometricAuthResult.Succeeded)
                }

                override fun onAuthenticationFailed() {
                    onResult(BiometricAuthResult.FailedAttempt)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    clearPromptState()
                    val outcome = classifyErrorOutcome(
                        errorCode = errorCode,
                        allowDeviceCredentialFallback = allowDeviceCredentialFallback,
                        isDeviceCredentialFlow = isDeviceCredentialFlow,
                        canUseDeviceCredential = canAuthenticateWithDeviceCredential()
                    )
                    when (outcome) {
                        PromptErrorOutcome.CANCELED -> {
                            onResult(
                                BiometricAuthResult.Canceled(
                                    message = errString.toString(),
                                    isNegativeButton = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                )
                            )
                        }
                        PromptErrorOutcome.FALLBACK_TO_DEVICE_CREDENTIAL -> {
                            authenticateWithDeviceCredential(activity, onResult = onResult)
                        }
                        PromptErrorOutcome.ERROR -> {
                            onResult(
                                BiometricAuthResult.Error(
                                    message = errString.toString(),
                                    errorCode = errorCode
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    private fun clearPromptState() {
        activePromptRef = null
        _isPromptShowing.value = false
    }

    internal companion object {
        private val CANCELED_ERROR_CODES = setOf(
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED
        )

        private val FALLBACK_ERROR_CODES = setOf(
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED
        )

        @VisibleForTesting
        internal fun classifyErrorOutcome(
            errorCode: Int,
            allowDeviceCredentialFallback: Boolean,
            isDeviceCredentialFlow: Boolean,
            canUseDeviceCredential: Boolean
        ): PromptErrorOutcome {
            return when {
                errorCode in CANCELED_ERROR_CODES -> PromptErrorOutcome.CANCELED
                !isDeviceCredentialFlow &&
                    allowDeviceCredentialFallback &&
                    canUseDeviceCredential &&
                    errorCode in FALLBACK_ERROR_CODES -> PromptErrorOutcome.FALLBACK_TO_DEVICE_CREDENTIAL
                else -> PromptErrorOutcome.ERROR
            }
        }

        @VisibleForTesting
        internal fun buildEnrollmentIntentActions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
            return if (sdkInt >= Build.VERSION_CODES.R) {
                listOf(
                    Settings.ACTION_BIOMETRIC_ENROLL,
                    Settings.ACTION_SECURITY_SETTINGS,
                    Settings.ACTION_SETTINGS
                )
            } else {
                listOf(
                    Settings.ACTION_SECURITY_SETTINGS,
                    Settings.ACTION_SETTINGS
                )
            }
        }
    }
}
