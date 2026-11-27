package com.ahmesh.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ahmesh.app.presentation.auth.AuthViewModel
import com.ahmesh.app.presentation.screens.assistant.AssistantScreen
import com.ahmesh.app.presentation.screens.auth.AuthenticationScreen
import com.ahmesh.app.presentation.screens.biometricstats.BiometricStatsScreen
import com.ahmesh.app.presentation.screens.main.MainScreen
import com.ahmesh.app.presentation.screens.pairing.PairingMode
import com.ahmesh.app.presentation.screens.pairing.PairingScreen
import com.ahmesh.app.presentation.screens.telemetry.LiveMetricsScreen
import com.ahmesh.app.presentation.screens.telemetry.NetworkTelemetryScreen
import com.ahmesh.app.presentation.screens.transfer.TransferScreen
import com.ahmesh.app.security.BiometricAuthManager

@Composable
fun AHMeshNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    biometricAuthManager: BiometricAuthManager,
    modifier: Modifier = Modifier,
    pendingShortcut: AppShortcutDestination? = null,
    onConsumeShortcut: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val startDestination = remember {
        if (authState.isLocked) Screen.Authentication.route else Screen.Main.route
    }

    LaunchedEffect(Unit) {
        authViewModel.onAppStartup()
    }

    LaunchedEffect(authState.isLocked) {
        val currentRoute = navController.currentDestination?.route ?: return@LaunchedEffect
        if (authState.isLocked && currentRoute != Screen.Authentication.route) {
            navController.navigate(Screen.Authentication.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pendingShortcut, authState.isLocked, currentBackStackEntry) {
        val shortcut = pendingShortcut ?: return@LaunchedEffect
        if (authState.isLocked) return@LaunchedEffect
        val currentRoute = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        if (currentRoute == Screen.Authentication.route) return@LaunchedEffect

        val targetRoute = when (shortcut) {
            AppShortcutDestination.QR_SCANNER -> Screen.Pairing.createRoute(PairingMode.SCAN)
            AppShortcutDestination.ASSISTANT -> Screen.Assistant.route
        }

        navController.navigate(targetRoute) {
            popUpTo(Screen.Main.route) { inclusive = false }
            launchSingleTop = true
        }
        onConsumeShortcut()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Authentication.route) {
            AuthenticationScreen(
                biometricAuthManager = biometricAuthManager,
                authViewModel = authViewModel,
                onAuthenticated = {
                    if (navController.currentDestination?.route != Screen.Main.route) {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Authentication.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(Screen.Main.route) {
            MainScreen(
                authViewModel = authViewModel,
                biometricAuthManager = biometricAuthManager,
                onNavigateToPairing = {
                    if (navController.currentDestination?.route != Screen.Pairing.route) {
                        navController.navigate(Screen.Pairing.createRoute(PairingMode.GENERATE)) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToAssistant = {
                    if (navController.currentDestination?.route != Screen.Assistant.route) {
                        navController.navigate(Screen.Assistant.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToBiometricStats = {
                    if (navController.currentDestination?.route != Screen.BiometricStats.route) {
                        navController.navigate(Screen.BiometricStats.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToTransfer = { peerId, peerName, host ->
                    if (navController.currentDestination?.route != Screen.Transfer.route) {
                        navController.navigate(
                            Screen.Transfer.createRoute(
                                peerId = peerId,
                                peerName = peerName,
                                host = host
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToNetworkTelemetry = {
                    if (navController.currentDestination?.route != Screen.NetworkTelemetry.route) {
                        navController.navigate(Screen.NetworkTelemetry.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToLiveMetrics = {
                    if (navController.currentDestination?.route != Screen.LiveMetrics.route) {
                        navController.navigate(Screen.LiveMetrics.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = Screen.Pairing.route,
            arguments = listOf(
                navArgument(Screen.Pairing.ARG_MODE) {
                    type = NavType.StringType
                    defaultValue = PairingMode.GENERATE.name
                }
            )
        ) { backStackEntry ->
            val modeArg = backStackEntry.arguments?.getString(Screen.Pairing.ARG_MODE)
            val initialMode = runCatching {
                PairingMode.valueOf(modeArg ?: PairingMode.GENERATE.name)
            }.getOrDefault(PairingMode.GENERATE)

            PairingScreen(
                biometricAuthManager = biometricAuthManager,
                authViewModel = authViewModel,
                initialMode = initialMode,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBiometricStats = {
                    if (navController.currentDestination?.route != Screen.BiometricStats.route) {
                        navController.navigate(Screen.BiometricStats.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenPeerTransfer = { peerId, peerName ->
                    if (navController.currentDestination?.route != Screen.Transfer.route) {
                        navController.navigate(
                            Screen.Transfer.createRoute(
                                peerId = peerId,
                                peerName = peerName
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(Screen.Assistant.route) {
            AssistantScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.BiometricStats.route) {
            BiometricStatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.NetworkTelemetry.route) {
            NetworkTelemetryScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenLiveMetrics = {
                    if (navController.currentDestination?.route != Screen.LiveMetrics.route) {
                        navController.navigate(Screen.LiveMetrics.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(Screen.LiveMetrics.route) {
            LiveMetricsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Transfer.route,
            arguments = listOf(
                navArgument(Screen.Transfer.ARG_PEER_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.Transfer.ARG_PEER_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.Transfer.ARG_HOST) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            TransferScreen(
                peerId = args?.getString(Screen.Transfer.ARG_PEER_ID)?.takeIf { it.isNotBlank() },
                peerName = args?.getString(Screen.Transfer.ARG_PEER_NAME)?.takeIf { it.isNotBlank() },
                initialHost = args?.getString(Screen.Transfer.ARG_HOST)?.takeIf { it.isNotBlank() },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
