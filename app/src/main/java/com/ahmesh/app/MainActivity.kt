package com.ahmesh.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ahmesh.app.core.security.configureAppCheck
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.core.theme.AppThemeMode
import com.ahmesh.app.core.theme.ThemeManager
import com.ahmesh.app.presentation.auth.AuthViewModel
import com.ahmesh.app.presentation.navigation.AHMeshNavHost
import com.ahmesh.app.presentation.navigation.AppShortcutDestination
import com.ahmesh.app.presentation.navigation.AppShortcutRouter
import com.ahmesh.app.security.BiometricAuthManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    @Inject
    lateinit var themeManager: ThemeManager

    private val authViewModel: AuthViewModel by viewModels()

    private var pendingShortcutDestination by mutableStateOf<AppShortcutDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1: Configure App Check with launch intent before any DI or Firebase operations
        configureAppCheck(this, intent)
        pendingShortcutDestination = AppShortcutRouter.parseDestination(intent)

        enableEdgeToEdge()
        setContent {
            val themeMode by themeManager.themeMode.collectAsStateWithLifecycle()
            val isDarkTheme = themeMode == AppThemeMode.DARK

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            AHMeshTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                AHMeshNavHost(
                    navController = navController,
                    authViewModel = authViewModel,
                    biometricAuthManager = biometricAuthManager,
                    pendingShortcut = pendingShortcutDestination,
                    onConsumeShortcut = { pendingShortcutDestination = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppShortcutRouter.parseDestination(intent)?.let { destination ->
            pendingShortcutDestination = destination
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            biometricAuthManager.cancelAuthentication()
        }
        super.onDestroy()
    }
}
