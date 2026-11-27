package com.ahmesh.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.data.pairing.QrCodeHelper
import com.ahmesh.app.data.security.EncryptedSecurityPreferencesRepository
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.model.BiometricCapability
import com.ahmesh.app.domain.model.P2PConnectionState
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.model.P2PTransportMode
import com.ahmesh.app.domain.model.PairingCodec
import com.ahmesh.app.domain.model.PairingPayload
import com.ahmesh.app.domain.model.QrScanStatistics
import com.ahmesh.app.domain.model.SecuritySettings
import com.ahmesh.app.presentation.auth.AuthLockScreen
import com.ahmesh.app.presentation.auth.AuthStatusNotice
import com.ahmesh.app.presentation.auth.AuthUiState
import com.ahmesh.app.presentation.components.BiometricAuthEventsTableSection
import com.ahmesh.app.presentation.components.BrandBannerCard
import com.ahmesh.app.presentation.components.NetworkStatusCard
import com.ahmesh.app.presentation.components.QrCodeGeneratorCard
import com.ahmesh.app.presentation.components.QrCodeScannerCard
import com.ahmesh.app.presentation.components.QrScanStatsTableSection
import com.ahmesh.app.presentation.components.QrScannerAlignmentOverlay
import com.ahmesh.app.presentation.components.SettingsSheetContent
import com.ahmesh.app.presentation.navigation.AppShortcutDestination
import com.ahmesh.app.presentation.navigation.AppShortcutRouter
import com.ahmesh.app.presentation.navigation.Screen
import com.ahmesh.app.presentation.screens.biometricstats.BiometricStatsScreenContent
import com.ahmesh.app.presentation.screens.biometricstats.BiometricStatsUiState
import com.ahmesh.app.presentation.screens.biometricstats.ChartMetricMode
import com.ahmesh.app.presentation.screens.pairing.PairingMode
import com.ahmesh.app.presentation.screens.pairing.PairingScreenContent
import com.ahmesh.app.presentation.screens.pairing.PairingUiState
import com.ahmesh.app.presentation.screens.pairing.PairingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AH Mesh", appName)
    }

    @Test
    fun pinHashingAndVerificationWorks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EncryptedSecurityPreferencesRepository(context)
        repo.configurePin("4829")
        assertTrue(repo.verifyPin("4829"))
        assertFalse(repo.verifyPin("1234"))
    }

    @Test
    fun networkStatusCardDisplaysIndicatorAndTriggersAction() {
        var actionClicked = false
        composeRule.setContent {
            AHMeshTheme {
                NetworkStatusCard(
                    status = P2PConnectionStatus(
                        state = P2PConnectionState.READY,
                        transportMode = P2PTransportMode.WIFI_DIRECT,
                        isWifiEnabled = true,
                        isWifiDirectSupported = true,
                        hasNearbyPermission = true
                    ),
                    savedPeerCount = 3,
                    onPrimaryAction = { actionClicked = true },
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithTag("network_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("network_status_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("network_status_badge").assertIsDisplayed()
        composeRule.onNodeWithText("P2P Ready").assertIsDisplayed()
        composeRule.onNodeWithTag("network_status_action_button").performClick()
        assertTrue(actionClicked)
    }

    @Test
    fun brandBannerAndAuthLockScreenRenderAndAcceptFallbacks() {
        var enteredDigit: Char? = null
        var deviceCredentialClicked = false
        composeRule.setContent {
            AHMeshTheme {
                AuthLockScreen(
                    uiState = AuthUiState(
                        isLocked = true,
                        biometricCapability = BiometricCapability.UNAVAILABLE,
                        canUseDeviceCredential = true,
                        settings = SecuritySettings(
                            isAppLockEnabled = true,
                            isBiometricEnabled = true,
                            isDeviceCredentialFallbackEnabled = true,
                            hasConfiguredPin = true,
                            pinLength = 4
                        ),
                        statusNotice = AuthStatusNotice.BIOMETRIC_UNAVAILABLE
                    ),
                    onDigitClick = { enteredDigit = it },
                    onBackspaceClick = {},
                    onUseBiometricsClick = {},
                    onUseDeviceCredentialClick = { deviceCredentialClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("auth_lock_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_status_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_pin_key_5").performClick()
        assertEquals('5', enteredDigit)
        composeRule.onNodeWithTag("auth_device_credential_button").performScrollTo().performClick()
        assertTrue(deviceCredentialClicked)
    }

    @Test
    fun brandBannerOpensAboutDialog() {
        composeRule.setContent {
            AHMeshTheme {
                BrandBannerCard()
            }
        }

        composeRule.onNodeWithTag("brand_banner_card").assertIsDisplayed()
        composeRule.onNodeWithTag("brand_about_button").performClick()
        composeRule.onNodeWithText("About AH Mesh").assertIsDisplayed()
    }

    @Test
    fun biometricEventsTableDisplaysTimestampResultAndEventType() {
        composeRule.setContent {
            AHMeshTheme {
                BiometricAuthEventsTableSection(
                    auditLogs = listOf(
                        BiometricAuditRecord(
                            id = 10L,
                            outcome = BiometricAuditOutcome.SUCCEEDED,
                            isSuccess = true,
                            authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK,
                            attemptedAt = 1_760_000_000_000L
                        ),
                        BiometricAuditRecord(
                            id = 11L,
                            outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
                            isSuccess = false,
                            authMethod = PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY,
                            attemptedAt = 1_759_999_900_000L
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithTag("pairing_biometric_events_table_card").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_biometric_events_table").assertIsDisplayed()
        composeRule.onNodeWithText("Timestamp").assertIsDisplayed()
        composeRule.onNodeWithText("Result").assertIsDisplayed()
        composeRule.onNodeWithText("Event Type").assertIsDisplayed()
        composeRule.onNodeWithText("QR Key Biometric").assertIsDisplayed()
        composeRule.onNodeWithText("Credential Verify").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_events_table_row_10").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_events_table_row_11").assertIsDisplayed()
    }

    @Test
    fun settingsSheetDarkModeSwitchTogglesThemePreference() {
        var darkModeCallbackValue: Boolean? = null
        composeRule.setContent {
            AHMeshTheme(darkTheme = true) {
                SettingsSheetContent(
                    authState = AuthUiState(
                        isLocked = false,
                        biometricCapability = BiometricCapability.AVAILABLE,
                        canUseDeviceCredential = true,
                        settings = SecuritySettings()
                    ),
                    isDarkMode = true,
                    onToggleDarkMode = { darkModeCallbackValue = it },
                    onDismiss = {},
                    onEnableAppLock = {},
                    onDisableAppLockWithoutPin = {},
                    onSetupPinAndEnable = {},
                    onEnableExistingPin = {},
                    onVerifyAndChangePin = { _, _, _ -> },
                    onResetWithNewPin = {},
                    onClearPinAndDisableLock = {},
                    onVerifyAndDisableLock = { _, _ -> },
                    onToggleBiometric = {},
                    onToggleDeviceFallback = {},
                    onToggleLockOnBackground = {},
                    onOpenBiometricSetup = {},
                    onLockNow = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_appearance_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_dark_mode_switch").performClick()
        assertEquals(false, darkModeCallbackValue)
    }

    @Test
    fun qrScanStatsTableDisplaysRadialRingAndTableMetrics() {
        composeRule.setContent {
            AHMeshTheme {
                QrScanStatsTableSection(
                    stats = QrScanStatistics(
                        totalScans = 10,
                        successCount = 8,
                        failureCount = 2
                    )
                )
            }
        }

        composeRule.onNodeWithTag("pairing_qr_stats_table_card").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_qr_stats_radial_ring").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_qr_stats_table").assertIsDisplayed()
        composeRule.onNodeWithText("Scan Outcome").assertIsDisplayed()
        composeRule.onNodeWithText("Total Scans").assertIsDisplayed()
        composeRule.onNodeWithText("Successful Scans").assertIsDisplayed()
        composeRule.onNodeWithText("Failed Scans").assertIsDisplayed()
        composeRule.onAllNodesWithText("80%")[0].assertIsDisplayed()
    }

    @Test
    fun qrGeneratorCardDisplaysPublicKeyAndCopiesToClipboard() {
        val sampleKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsamplePublicKeyPayload123456"
        val identity = PairingPayload(
            nodeId = "ah-copy01",
            displayName = "Alpha Mesh",
            publicKey = sampleKey
        )

        composeRule.setContent {
            AHMeshTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    QrCodeGeneratorCard(
                        identity = identity,
                        qrPayloadString = PairingCodec.encode(identity),
                        isQrUnlocked = true,
                        authErrorMessage = null,
                        onRequestUnlock = {},
                        onLockQr = {},
                        onDisplayNameChange = {},
                        onRotateSession = {},
                        onShowHandshakeQr = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("pairing_public_key_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_public_key_string").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(sampleKey).assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_copy_public_key_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Copy to Clipboard").assertIsDisplayed()

        composeRule.onNodeWithTag("pairing_copy_public_key_button").performClick()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals(sampleKey, copied)
    }

    @Test
    fun biometricStatsScreenDisplaysRatesBarChartAndModeToggle() {
        var selectedMode = ChartMetricMode.RATE_PERCENT
        val logs = listOf(
            BiometricAuditRecord(
                id = 1L,
                outcome = BiometricAuditOutcome.SUCCEEDED,
                isSuccess = true,
                authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK
            ),
            BiometricAuditRecord(
                id = 2L,
                outcome = BiometricAuditOutcome.SUCCEEDED,
                isSuccess = true,
                authMethod = PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH
            ),
            BiometricAuditRecord(
                id = 3L,
                outcome = BiometricAuditOutcome.SUCCEEDED,
                isSuccess = true,
                authMethod = PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY
            ),
            BiometricAuditRecord(
                id = 4L,
                outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
                isSuccess = false,
                authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK
            )
        )

        composeRule.setContent {
            AHMeshTheme {
                BiometricStatsScreenContent(
                    uiState = BiometricStatsUiState.fromAuditLogs(logs, selectedMode),
                    onNavigateBack = {},
                    onMetricModeChange = { selectedMode = it },
                    onClearAuditLogs = {}
                )
            }
        }

        composeRule.onNodeWithTag("biometric_stats_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_stats_kpi_row").assertIsDisplayed()
        composeRule.onNodeWithText("75%").assertIsDisplayed()
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_stats_bar_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_stats_tooltip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_outcome_breakdown_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_method_breakdown_card").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("biometric_chart_mode_count").performScrollTo().performClick()
        assertEquals(ChartMetricMode.ATTEMPT_COUNT, selectedMode)
    }

    @Test
    fun biometricStatsScreenDisplaysEmptyStateWhenNoLogsRecorded() {
        composeRule.setContent {
            AHMeshTheme {
                BiometricStatsScreenContent(
                    uiState = BiometricStatsUiState(),
                    onNavigateBack = {},
                    onMetricModeChange = {},
                    onClearAuditLogs = {}
                )
            }
        }

        composeRule.onNodeWithTag("biometric_stats_empty_state").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No Biometric Logs Recorded").assertIsDisplayed()
    }

    @Test
    fun qrGeneratorCardDisplaysSaveToGalleryButtonAndGeneratesExportBitmap() {
        val sampleKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEgalleryTestPublicKey123456"
        val identity = PairingPayload(
            nodeId = "ah-gal01",
            displayName = "Gallery Mesh",
            publicKey = sampleKey
        )
        val payload = PairingCodec.encode(identity)

        composeRule.setContent {
            AHMeshTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    QrCodeGeneratorCard(
                        identity = identity,
                        qrPayloadString = payload,
                        isQrUnlocked = true,
                        authErrorMessage = null,
                        onRequestUnlock = {},
                        onLockQr = {},
                        onDisplayNameChange = {},
                        onRotateSession = {},
                        onShowHandshakeQr = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("pairing_save_to_gallery_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save to Gallery").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_save_to_gallery_button").performClick()

        val bitmap = QrCodeHelper.createExportableQrBitmap(content = payload)
        assertTrue(bitmap != null && bitmap.width == bitmap.height && bitmap.width > 720)
        bitmap?.recycle()

        val fileName = QrCodeHelper.buildGalleryImageFileName("ah-gal/01")
        assertTrue(fileName.startsWith("ahmesh_qr_ah-gal_01_"))
        assertTrue(fileName.endsWith(".png"))
    }

    @Test
    fun qrScannerAlignmentOverlayDisplaysHintsAndErrorState() {
        composeRule.setContent {
            AHMeshTheme {
                Column {
                    QrScannerAlignmentOverlay(
                        hasScanError = false,
                        showDetailedGuide = true,
                        modifier = Modifier.size(240.dp)
                    )
                    QrScannerAlignmentOverlay(
                        hasScanError = true,
                        showDetailedGuide = true,
                        modifier = Modifier.size(240.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithText("Align peer QR inside frame").assertIsDisplayed()
        composeRule.onNodeWithText("Re-align QR & hold steady").assertIsDisplayed()
        composeRule.onAllNodesWithText("15–25 cm • Match corner markers")[0].assertIsDisplayed()
    }

    @Test
    fun qrScannerCardTogglesHandshakeAlignmentGuide() {
        composeRule.setContent {
            AHMeshTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    QrCodeScannerCard(
                        manualCodeInput = "",
                        hasScanError = false,
                        onManualCodeChange = {},
                        onManualSubmit = {},
                        onQrCodeDetected = {},
                        onScanFailed = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("qr_scanner_guide_toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qr_scanner_alignment_guide_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Handshake Alignment Guide").assertIsDisplayed()

        composeRule.onNodeWithTag("qr_scanner_guide_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("qr_scanner_alignment_guide_banner").assertDoesNotExist()
    }

    @Test
    fun appShortcutRouterParsesShortcutIntentsAndRoutes() {
        assertEquals(
            AppShortcutDestination.QR_SCANNER,
            AppShortcutRouter.parseDestination(Intent(AppShortcutRouter.ACTION_OPEN_QR_SCANNER))
        )
        assertEquals(
            AppShortcutDestination.ASSISTANT,
            AppShortcutRouter.parseDestination(Intent(AppShortcutRouter.ACTION_OPEN_ASSISTANT))
        )
        assertEquals(
            AppShortcutDestination.ASSISTANT,
            AppShortcutRouter.parseDestination(
                Intent(Intent.ACTION_VIEW).putExtra(
                    AppShortcutRouter.EXTRA_SHORTCUT_DESTINATION,
                    AppShortcutDestination.ASSISTANT.name
                )
            )
        )
        assertNull(AppShortcutRouter.parseDestination(Intent(Intent.ACTION_MAIN)))
        assertNull(AppShortcutRouter.parseDestination(null))

        assertEquals("pairing?mode=GENERATE", Screen.Pairing.createRoute())
        assertEquals("pairing?mode=SCAN", Screen.Pairing.createRoute(PairingMode.SCAN))
    }

    @Test
    fun launcherShortcutsXmlContainsQrScannerAndAssistantEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.shortcuts)
        val shortcutIds = mutableListOf<String>()
        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "shortcut") {
                val id = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "shortcutId")
                if (id != null) shortcutIds.add(id)
            }
            eventType = parser.next()
        }
        parser.close()

        assertTrue(shortcutIds.contains("open_qr_scanner"))
        assertTrue(shortcutIds.contains("open_assistant"))
    }

    @Test
    fun pairingScreenStartsDirectlyOnScannerWhenInitialModeIsScan() {
        composeRule.setContent {
            AHMeshTheme {
                PairingScreenContent(
                    uiState = PairingUiState(),
                    biometricCapability = BiometricCapability.AVAILABLE,
                    onNavigateBack = {},
                    onRequestQrUnlock = {},
                    onOpenBiometricSetup = {},
                    onLockQr = {},
                    onDisplayNameChange = {},
                    onRotateSession = {},
                    onManualCodeChange = {},
                    onManualSubmit = {},
                    onQrCodeDetected = {},
                    onScanFailed = {},
                    onRetryScan = {},
                    onClearScanHistory = {},
                    onClearBiometricAuditLogs = {},
                    onSaveAuditLog = {},
                    onShareAuditLog = {},
                    onDismissFeedback = {},
                    initialMode = PairingMode.SCAN
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qr_scanner_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qr_generator_card").assertDoesNotExist()
    }
}
